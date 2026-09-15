/*
 * 职责：保存界面屏幕栈、主题、来源课程、搜索、收藏、出席状态、个人资料及同步目标状态。
 * 输入／输出：接收用户的本地点击；输出 Compose 可观察状态和更新后的运行内课程列表。
 * 外部副作用：无；个人资料和同步目标的本机保存均由界面层的偏好模块单独负责。
 * 不负责：数据库、日期选择器、真实日历写入、网络同步或账户身份。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.domain.Course
import com.tequila.tumcalendarcontrol.domain.CourseLocalOverride
import com.tequila.tumcalendarcontrol.domain.SampleCourses
import com.tequila.tumcalendarcontrol.domain.TumEventStatus
import com.tequila.tumcalendarcontrol.domain.displayTitle
import com.tequila.tumcalendarcontrol.sync.PrivateSyncConfiguration
import com.tequila.tumcalendarcontrol.sync.OfflinePreviewSamples
import com.tequila.tumcalendarcontrol.sync.RuleLedger
import com.tequila.tumcalendarcontrol.sync.SyncPreview
import com.tequila.tumcalendarcontrol.sync.IcalReadOnlyImportResult
import com.tequila.tumcalendarcontrol.sync.IcalReadOnlyPreview
import com.tequila.tumcalendarcontrol.sync.SourceCourse
import com.tequila.tumcalendarcontrol.sync.RuleEditCommand
import com.tequila.tumcalendarcontrol.sync.RuleLedgerEditor
import com.tequila.tumcalendarcontrol.sync.SyncRule
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceOccurrenceIdentity
import com.tequila.tumcalendarcontrol.sync.SyncCandidateFactory
import com.tequila.tumcalendarcontrol.sync.ManagedSyncEngine
import com.tequila.tumcalendarcontrol.sync.ManagedSyncPreview
import com.tequila.tumcalendarcontrol.sync.ManagedSyncResult
import com.tequila.tumcalendarcontrol.sync.ManualScheduleEditor
import com.tequila.tumcalendarcontrol.sync.ManualScheduleItem
import com.tequila.tumcalendarcontrol.sync.OutputTarget
import com.tequila.tumcalendarcontrol.sync.DesiredCalendarEvent
import com.tequila.tumcalendarcontrol.sync.SyncRunSummary
import com.tequila.tumcalendarcontrol.sync.StoredEventMapping
import com.tequila.tumcalendarcontrol.sync.RuleLedgerBackup
import com.tequila.tumcalendarcontrol.sync.RuleLedgerBackupCodec
import com.tequila.tumcalendarcontrol.sync.SourceCheckSettings
import com.tequila.tumcalendarcontrol.sync.SourceCheckStatus
import com.tequila.tumcalendarcontrol.sync.SourceCheckCadence
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import java.time.LocalDate

enum class AppScreen {
    OVERVIEW, DETAIL, ATTENDANCE, COURSE_EDIT, SEARCH, FAVORITES, MORE, SETTINGS, PROFILE, SYNC_CONFIGURATION, SYNC_PREVIEW, SOURCE_RANGE, SOURCE_PREVIEW, SOURCE_ATTENDANCE, MANUAL_EVENTS, MANAGED_SYNC_PREVIEW, BACKGROUND_CHECK, RULE_BACKUP,
}

enum class ThemeMode(val label: String) {
    LIGHT("浅色模式"),
    DARK("深色模式"),
    SYSTEM("跟随系统"),
}

enum class NavigationDirection {
    FORWARD, BACKWARD,
}

/** 批量设置状态时可同时处理收藏；保持不变避免误改既有收藏。 */
enum class SourceFavoriteAction(val label: String, val description: String) {
    KEEP("不改变收藏", "保留每门课当前的收藏状态"),
    ADD("加入收藏", "把这批课程同时加入收藏"),
    REMOVE("取消收藏", "把这批课程同时移出收藏"),
}

/** 仅描述当前进程的 Google 授权结果；令牌绝不持久化，重启后必须重新授权。 */
enum class GoogleAuthorizationStatus(val title: String, val defaultDetail: String) {
    NOT_CONNECTED("Google 日历未连接", "还没有完成本次授权；不会创建或写入 Google 日历。"),
    AUTHORIZING("正在连接 Google 日历", "请在 Google 页面选择账户并允许访问，然后返回本应用。"),
    AUTHORIZED("Google 日历已连接", "现在可以查看将写入的内容并确认写入；Google 日历尚未改变。"),
    FAILED("未能连接 Google 日历", "没有完成授权，Google 日历没有改变。检查配置后重试。"),
}

class CalendarControlState(
    initialCourses: List<Course> = SampleCourses.all(),
    initialProfile: UserProfile = UserProfile(),
    initialSyncDestinations: SyncDestinations = SyncDestinations(),
    initialPrivateSyncConfiguration: PrivateSyncConfiguration = PrivateSyncConfiguration(),
    initialRuleLedger: RuleLedger = RuleLedger(),
    initialSourceCheckSettings: SourceCheckSettings = SourceCheckSettings(),
    initialSourceCheckStatus: SourceCheckStatus = SourceCheckStatus(),
    initialCoursePresentations: Map<String, CoursePresentation> = emptyMap(),
) {
    /** 仅保存 UID 对应的本机展示元数据；来源目录仍只在当前进程保存。 */
    private val savedCoursePresentations = initialCoursePresentations
    var courses by mutableStateOf(initialCourses)
        private set
    private val navigation = NavigationState()
    val screen: AppScreen get() = navigation.screen
    var selectedCourseId by mutableStateOf(initialCourses.first().id)
        private set
    var searchQuery by mutableStateOf("")
    var profile by mutableStateOf(initialProfile)
        private set
    var pendingAttendance by mutableStateOf(initialCourses.first().attendance)
    var pendingAttendanceScope by mutableStateOf(AttendanceEditScope.ALL_OCCURRENCES)
    var pendingEffectiveDate by mutableStateOf("")
    var visibleSourceStatuses by mutableStateOf(TumEventStatus.entries.toSet())
    var localCalendarPermissionRequestPending by mutableStateOf(false)
        private set
    var feedback by mutableStateOf<String?>(null)
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    var syncDestinations by mutableStateOf(initialSyncDestinations)
        private set
    var privateSyncConfiguration by mutableStateOf(initialPrivateSyncConfiguration)
        private set
    var ruleLedger by mutableStateOf(initialRuleLedger)
        private set
    var sourceCheckSettings by mutableStateOf(initialSourceCheckSettings)
        private set
    var sourceCheckStatus by mutableStateOf(initialSourceCheckStatus)
        private set
    var sourceCheckSchedulingRequested by mutableStateOf(false)
        private set
    var notificationPermissionRequestPending by mutableStateOf(false)
        private set
    var ruleBackupExportRequested by mutableStateOf(false)
        private set
    var ruleBackupImportRequested by mutableStateOf(false)
        private set
    var pendingRuleBackup by mutableStateOf<RuleLedgerBackup?>(null)
        private set
    val offlinePreview: SyncPreview = OfflinePreviewSamples.preview()
    private val sourceWorkflow = SourceReadWorkflowState()
    private val outputWorkflow = ManagedOutputWorkflowState()
    val sourceReadRequested: Boolean get() = sourceWorkflow.readRequested
    val sourceReadInProgress: Boolean get() = sourceWorkflow.readInProgress
    val sourcePreview: IcalReadOnlyPreview? get() = sourceWorkflow.preview
    val sourcePreviewError: String? get() = sourceWorkflow.previewError
    /** 真实来源资料仅在当前进程内保存，重新读取或退出后由用户源重新生成。 */
    val sourceCourses: List<SourceCourse> get() = sourceWorkflow.courses
    /** 成功读取过来源后，主标签页应只显示来源投影，不能退回合成示例。 */
    val sourceCatalogLoaded: Boolean get() = sourceWorkflow.catalogLoaded
    /** 真正来源实例只在当前进程用于预览／同步候选，退出或重新读取时丢弃。 */
    /** 只暴露本次运行实例给浏览器；调用者不得用它写入或持久化来源内容。 */
    val currentSourceOccurrences: List<SourceCalendarOccurrence> get() = sourceWorkflow.occurrences
    /** 当前勾选的是具体课次；规则与收藏在需要时才从中聚合所属课程 UID。 */
    val selectedSourceOccurrences: Set<SourceOccurrenceIdentity> get() = sourceWorkflow.selectedOccurrences
    var sourceBrowserQuery: String
        get() = sourceWorkflow.browserQuery
        set(value) { sourceWorkflow.browserQuery = value }
    var sourceBrowserFilter: SourceCourseFilter
        get() = sourceWorkflow.browserFilter
        set(value) { sourceWorkflow.browserFilter = value }
    val sourceReloadConfirmationPending: Boolean get() = sourceWorkflow.reloadConfirmationPending
    var pendingSourceAttendance: AttendanceState
        get() = sourceWorkflow.pendingAttendance
        set(value) { sourceWorkflow.pendingAttendance = value }
    var pendingSourceAttendanceScope: AttendanceEditScope
        get() = sourceWorkflow.pendingAttendanceScope
        set(value) { sourceWorkflow.pendingAttendanceScope = value }
    var pendingSourceEffectiveDate: String
        get() = sourceWorkflow.pendingEffectiveDate
        set(value) { sourceWorkflow.pendingEffectiveDate = value }
    var pendingSourceFavoriteAction: SourceFavoriteAction
        get() = sourceWorkflow.pendingFavoriteAction
        set(value) { sourceWorkflow.pendingFavoriteAction = value }
    var managedSyncPreview: ManagedSyncPreview?
        get() = outputWorkflow.preview
        private set(value) { outputWorkflow.preview = value }
    private var managedDesiredEvents: List<DesiredCalendarEvent>
        get() = outputWorkflow.desiredEvents
        set(value) { outputWorkflow.desiredEvents = value }
    /** 预览分项仅按应用生成身份前缀统计，不暴露来源标题或正文。 */
    val managedSourceCandidateCount: Int get() = managedDesiredEvents.count { !it.identity.sourceUid.startsWith("manual:") }
    val managedManualCandidateCount: Int get() = managedDesiredEvents.count { it.identity.sourceUid.startsWith("manual:") }
    val managedSyncRequested: Boolean get() = outputWorkflow.syncRequested
    val managedRollbackRequested: Boolean get() = outputWorkflow.rollbackRequested
    val managedSyncInProgress: Boolean get() = outputWorkflow.inProgress
    val googleAuthorizationRequested: Boolean get() = outputWorkflow.googleAuthorizationRequested
    val hasGoogleAuthorization: Boolean get() = outputWorkflow.hasGoogleAuthorization
    val googleAuthorizationStatus: GoogleAuthorizationStatus get() = outputWorkflow.googleAuthorizationStatus
    val googleAuthorizationDetail: String get() = outputWorkflow.googleAuthorizationDetail
    val localCalendarPermissionGranted: Boolean get() = outputWorkflow.localCalendarPermissionGranted
    /** 只读请求的单调编号；忽略超时后才返回的旧线程结果，避免覆盖新的用户操作。 */
    val sourceReadToken: Long get() = sourceWorkflow.readToken
    val navigationDirection: NavigationDirection get() = navigation.direction

    /** 更多操作覆盖层打开时，仍保持下方主页面稳定，避免与主标签页共用过渡。 */
    val contentScreen: AppScreen
        get() = navigation.contentScreen

    val isMoreOpen: Boolean
        get() = navigation.isMoreOpen

    val selectedCourse: Course
        get() = courses.first { it.id == selectedCourseId }

    /** 真实来源没有可依赖的 TUM 状态字段，读取后主界面完整显示其课程目录。 */
    val displayedCourses: List<Course>
        get() = if (sourceCatalogLoaded) courses else courses.filter { it.hasFixedPlus && it.sourceStatus in visibleSourceStatuses }

    val searchedCourses: List<Course>
        get() = displayedCourses.filter { it.displayTitle.contains(searchQuery, ignoreCase = true) }

    val favoriteCourses: List<Course>
        get() = courses.filter { it.isFavorite && it.hasFixedPlus }

    /** 主标签页按相对位置决定左右动效；详情等层级页面仍使用前进动效。 */
    fun open(screen: AppScreen) {
        navigation.open(screen)
    }

    fun openCourse(courseId: String) {
        selectedCourseId = courseId
        pendingAttendance = selectedCourse.attendance
        navigation.openDetail()
    }

    fun openAttendance() {
        pendingAttendance = selectedCourse.attendance
        pendingAttendanceScope = AttendanceEditScope.ALL_OCCURRENCES
        pendingEffectiveDate = ""
        feedback = null
        open(AppScreen.ATTENDANCE)
    }

    fun openCourseEdit() = navigation.openCourseEdit()

    /** 仅应用私有覆盖；来源 title、schedule、location 永远保持不变。 */
    fun updateSelectedCourseOverride(override: CourseLocalOverride) {
        courses = courses.map { if (it.id == selectedCourseId) it.copy(localOverride = override) else it }
        feedback = "已保存课程显示修改；课程原来的信息没有改变。"
    }

    fun toggleSourceStatusVisible(status: TumEventStatus) {
        visibleSourceStatuses = if (status in visibleSourceStatuses) visibleSourceStatuses - status else visibleSourceStatuses + status
    }

    fun openMore() = navigation.openMore()

    fun openProfile() = navigation.openProfile()

    /** 已由界面层校验并提交的本机个人资料；不代表任何外部账户。 */
    fun updateProfile(updatedProfile: UserProfile) {
        profile = updatedProfile.copy(displayName = updatedProfile.displayName.trim().ifBlank { UserProfile().displayName })
    }

    /** 只更新未来输出许可；真正的日历连接和写入仍由后续经授权的同步模块负责。 */
    fun setSyncDestinationEnabled(destination: SyncDestination, enabled: Boolean) {
        syncDestinations = syncDestinations.withEnabled(destination, enabled)
        if (destination == SyncDestination.GOOGLE_CALENDAR && !enabled) {
            outputWorkflow.disconnectGoogle()
        }
    }

    /** 申请权限不是写入：只有系统同意后才保存未来本机输出许可。 */
    fun requestLocalCalendarOutput() { localCalendarPermissionRequestPending = true }

    fun resolveLocalCalendarPermission(granted: Boolean) {
        localCalendarPermissionRequestPending = false
        outputWorkflow.localCalendarPermissionGranted = granted
        syncDestinations = syncDestinations.withEnabled(SyncDestination.LOCAL_CALENDAR, granted)
        feedback = if (granted) "已允许访问本机日历；现在仍需查看数量并确认，才会写入。" else "没有获得本机日历权限；本机日历保持关闭。"
    }

    /** 启动时仅读取系统已授予状态，不申请权限也不改变用户的输出开关。 */
    fun refreshLocalCalendarPermission(granted: Boolean) { outputWorkflow.localCalendarPermissionGranted = granted }

    /** 保存前由页面展示格式问题；状态机不发起任何连接或日历写入。 */
    fun updatePrivateSyncConfiguration(configuration: PrivateSyncConfiguration) {
        require(configuration.validate().isEmpty()) { "同步配置格式无效。" }
        privateSyncConfiguration = configuration
    }

    fun openSyncConfiguration() = navigation.openSyncConfiguration()

    /** 范围配置独立于账户表单；打开和保存都不触发网络读取。 */
    fun openSourceRange() = navigation.openSourceRange()

    /** 后台检查只在用户已经配置来源后才可开启；排程本身不会读取或写入日历。 */
    fun updateSourceCheck(enabled: Boolean, cadence: SourceCheckCadence = sourceCheckSettings.cadence) {
        if (enabled && !privateSyncConfiguration.hasTumSubscription) {
            feedback = "请先保存课程订阅地址；尚未开启更新检查。"
            return
        }
        val newlyEnabled = enabled && !sourceCheckSettings.enabled
        sourceCheckSettings = SourceCheckSettings(enabled, cadence)
        sourceCheckSchedulingRequested = true
        if (newlyEnabled) notificationPermissionRequestPending = true
        feedback = if (enabled) "已开启课程更新检查；它不会自动写入任何日历。" else "已关闭课程更新检查。"
    }

    fun consumeSourceCheckSchedulingRequest() { sourceCheckSchedulingRequested = false }
    fun consumeNotificationPermissionRequest() { notificationPermissionRequestPending = false }
    fun resolveNotificationPermission(granted: Boolean) {
        notificationPermissionRequestPending = false
        if (!granted && sourceCheckSettings.enabled) feedback = "未授予通知权限；更新检查仍会在应用内记录状态。"
    }
    fun refreshSourceCheckStatus(status: SourceCheckStatus) { sourceCheckStatus = status }

    fun openBackgroundCheck() = navigation.openBackgroundCheck()

    fun openRuleBackup() = navigation.openRuleBackup()

    /** SAF 写入只能由用户选择文件后触发；此处仅表达 UI 请求。 */
    fun requestRuleBackupExport() { ruleBackupExportRequested = true }
    fun consumeRuleBackupExportRequest() { ruleBackupExportRequested = false }
    fun resolveRuleBackupExport(saved: Boolean) { feedback = if (saved) "课程设置已导出；不包含课程地址、账户或已写入日程。" else "没有导出课程设置。" }

    /** SAF 读取后的文本只留在内存解析为待确认草稿，未确认前不改动规则资料库。 */
    fun requestRuleBackupImport() { ruleBackupImportRequested = true }
    fun consumeRuleBackupImportRequest() { ruleBackupImportRequested = false }
    fun resolveRuleBackupImport(text: String?) {
        pendingRuleBackup = text?.let(RuleLedgerBackupCodec::decode)
        feedback = if (pendingRuleBackup == null) "无法读取备份；本机课程设置没有改变。" else "已读取备份。确认后才会替换出席设置和校园事项。"
    }
    fun cancelRuleBackupImport() { pendingRuleBackup = null }
    fun confirmRuleBackupImport() {
        val backup = pendingRuleBackup ?: return
        ruleLedger = ruleLedger.copy(rules = backup.rules, manualEvents = backup.manualEvents)
        refreshCoursesFromSource()
        pendingRuleBackup = null
        feedback = "已恢复出席设置和校园事项；日历没有改变。"
    }

    /** 保存范围只写入加密本机配置；不会清除当前来源目录或选择。 */
    fun saveSourceOutputRange(startDate: String, endDate: String): List<String> {
        val updated = privateSyncConfiguration.copy(sourceRangeStartDate = startDate, sourceRangeEndDate = endDate)
        val problems = updated.validate()
        if (problems.isEmpty()) {
            privateSyncConfiguration = updated
            feedback = "已保存读取时间；当前课程列表和选择保持不变。"
        }
        return problems.map { it.message }
    }

    /** 重新读取会替换运行内目录；已有选择时必须经明确确认。 */
    fun requestSourceReadForCurrentRange() {
        when (val request = sourceWorkflow.requestRead(privateSyncConfiguration.hasTumSubscription)) {
            is SourceReadRequest.Rejected -> feedback = request.message
            SourceReadRequest.Ignored, SourceReadRequest.NeedsReloadConfirmation, is SourceReadRequest.Requested -> Unit
        }
    }

    fun resolveSourceReloadConfirmation(accepted: Boolean) {
        when (val request = sourceWorkflow.resolveReloadConfirmation(accepted, privateSyncConfiguration.hasTumSubscription)) {
            is SourceReadRequest.Rejected -> feedback = request.message
            SourceReadRequest.Ignored, SourceReadRequest.NeedsReloadConfirmation, is SourceReadRequest.Requested -> Unit
        }
    }

    /** 已读课程目录可直接返回；无目录时只提示，不能暗中发起来源读取。 */
    fun openSourceCourseSelector() {
        if (sourceWorkflow.preview == null) {
            feedback = "还没有读取课程。请先在“选择读取时间”中读取。"
            return
        }
        navigation.openExistingSourcePreview()
    }

    /** 预览只展示合成差异，不能触发网络、权限申请或任何日历写入。 */
    fun openSyncPreview() = navigation.openSyncPreview()

    /**
     * 读取请求仅作为 UI 意图；界面层随后在后台执行一次 HTTPS 只读获取。
     * 配置不存在时不发出请求，以防空地址被误认为一次同步。
     */
    fun requestSourcePreview() {
        when (val request = sourceWorkflow.requestDirectRead(privateSyncConfiguration.hasTumSubscription)) {
            is SourceReadRequest.Rejected -> feedback = request.message
            is SourceReadRequest.Requested -> feedback = "正在读取课程；不会写入任何日历。"
            SourceReadRequest.Ignored, SourceReadRequest.NeedsReloadConfirmation -> Unit
        }
    }

    /** 后台读取完成后保留摘要与仅本次运行可见的课程目录；不保留 URL 或 ICS 原文。 */
    fun resolveSourcePreview(token: Long, result: IcalReadOnlyImportResult) {
        when (val resolution = sourceWorkflow.resolveRead(token, result)) {
            is SourceReadResolution.Success -> {
                refreshCoursesFromSource()
                selectedCourseId = courses.firstOrNull()?.id ?: ""
                navigation.openSourcePreview()
            }
            is SourceReadResolution.Failure -> feedback = "未能读取课程：${resolution.message}。日历没有改变。"
            SourceReadResolution.Ignored -> Unit
        }
    }

    /** 课程目录的多选保留精确课次身份；来源标题、地点与正文不进入规则资料库。 */
    fun toggleSourceOccurrenceSelection(identity: SourceOccurrenceIdentity) {
        sourceWorkflow.toggleSelection(identity)
    }

    fun selectAllSourceOccurrences() = sourceWorkflow.selectAllOccurrences()

    fun clearSourceCourseSelection() = sourceWorkflow.clearSelection()

    fun updateSourceBrowserQuery(query: String) { sourceBrowserQuery = query }

    fun updateSourceBrowserFilter(filter: SourceCourseFilter) { sourceBrowserFilter = filter }

    /** 新来源课次无规则时默认蓝色；用户可按单课次或多选打开同一三态编辑器。 */
    fun sourceAttendanceFor(sourceUid: String): AttendanceState = ruleLedger.rules
        .firstOrNull { it.sourceUid == sourceUid }
        ?.defaultState
        ?: AttendanceState.CHOOSE_TO_ATTEND

    fun openSourceAttendanceEditor(selection: Set<SourceOccurrenceIdentity> = selectedSourceOccurrences) {
        if (selection.isEmpty()) {
            feedback = "请至少选择一个课次。"
            return
        }
        val firstState = sourceAttendanceFor(selection.first().sourceUid)
        sourceWorkflow.prepareAttendanceEditor(selection, firstState)
        navigation.openSourceAttendance()
    }

    /** 将 UI 范围转换为 UID 规则；“仅本次”严格使用选中的原始实例键。 */
    fun confirmSourceAttendance() {
        val result = sourceWorkflow.applyAttendanceRules(ruleLedger)
        if (result is SourceAttendanceRuleResult.Invalid) {
            feedback = result.message
            if (result.message.startsWith("课程列表")) navigation.handleBack()
            return
        }
        result as SourceAttendanceRuleResult.Applied
        ruleLedger = result.rules
        refreshCoursesFromSource()
        if (result.favoriteAction != SourceFavoriteAction.KEEP) {
            courses = courses.map { course ->
                if (course.id in result.sourceUids) course.copy(isFavorite = result.favoriteAction == SourceFavoriteAction.ADD) else course
            }
        }
        feedback = "已保存 ${selectedSourceOccurrences.size} 个课次的出席方式${if (result.favoriteAction == SourceFavoriteAction.KEEP) "" else "与收藏设置"}；尚未写入日历。"
        navigation.handleBack()
    }

    fun confirmAttendance() {
        val command = attendanceCommand() ?: return
        val selectedRule = ruleLedger.rules.firstOrNull { it.sourceUid == selectedCourseId }
            ?: SyncRule(selectedCourseId, hasConfirmedFixplatz = selectedCourse.hasFixedPlus, defaultState = selectedCourse.attendance)
        ruleLedger = ruleLedger.copy(rules = RuleLedgerEditor.apply(
            ruleLedger.rules.filterNot { it.sourceUid == selectedCourseId } + selectedRule,
            command,
        ))
        courses = courses.map { course ->
            if (course.id == selectedCourseId) course.copy(attendance = pendingAttendance) else course
        }
        feedback = "已更新“${selectedCourse.displayTitle}”的出席方式（${pendingAttendanceScope.label}）。这只改动应用内示例。"
        open(AppScreen.DETAIL)
    }

    private fun attendanceCommand(): RuleEditCommand? = when (pendingAttendanceScope) {
        AttendanceEditScope.ALL_OCCURRENCES -> RuleEditCommand.WholeCourse(setOf(selectedCourseId), pendingAttendance)
        AttendanceEditScope.ONLY_THIS_OCCURRENCE -> RuleEditCommand.Instances(setOf(selectedCourseId), setOf("$selectedCourseId:current-synthetic"), pendingAttendance)
        AttendanceEditScope.CURRENT_AND_FUTURE -> RuleEditCommand.FromDate(setOf(selectedCourseId), LocalDate.now(), pendingAttendance)
        AttendanceEditScope.SPECIFIED_AND_FUTURE -> AttendanceEffectiveDateParser.parseOrNull(pendingEffectiveDate)
            ?.let { RuleEditCommand.FromDate(setOf(selectedCourseId), it, pendingAttendance) }
            ?: run { feedback = "请输入 YYYY-MM-DD 格式的指定日期。"; null }
    }

    fun toggleFavorite(courseId: String) {
        courses = courses.map { course ->
            if (course.id == courseId) course.copy(isFavorite = !course.isFavorite) else course
        }
    }

    fun requestSync() {
        prepareCalendarWrite()
    }

    /** 使用者点“写入日历”后的内部准备步骤；只计算确认信息，绝不在此处写入。 */
    fun prepareCalendarWrite() = requestManagedSyncPreview()

    /** 已持久化的映射可在来源目录丢失后单独进入撤销页；该入口不生成候选或写入。 */
    fun openMappedOutputRollback() {
        if (ruleLedger.mappings.isEmpty()) {
            feedback = "没有本应用写入的日程可清除。"
            return
        }
        managedSyncPreview = null
        navigation.openMappedRollback()
    }

    /** 基于当前进程来源和本机规则生成差异；不查询、创建或写入任何目标日历。 */
    fun requestManagedSyncPreview() {
        if (currentSourceOccurrences.isEmpty() && ruleLedger.manualEvents.isEmpty()) {
            feedback = "先读取并选择课程，或添加校园事项，再写入日历。"
            return
        }
        val targets = enabledOutputTargets()
        if (targets.isEmpty()) {
            feedback = "请先在设置中选择本机日历或 Google 日历；日历没有改变。"
            return
        }
        managedDesiredEvents = SyncCandidateFactory().build(
            occurrences = currentSourceOccurrences,
            selectedSourceOccurrences = selectedSourceOccurrences,
            sourceOutputWindow = privateSyncConfiguration.sourceOutputWindow
                ?: run { feedback = "读取时间不正确；回到“选择读取时间”修改后重试。"; return },
            rules = ruleLedger.rules,
            manualEvents = ruleLedger.manualEvents,
        )
        managedSyncPreview = ManagedSyncEngine().preview(managedDesiredEvents, ruleLedger.mappings, targets)
        navigation.openManagedSyncPreview()
    }

    /** Google 授权只由显式点按触发；令牌成功后仅存活在本进程。 */
    fun requestGoogleAuthorization() {
        val rejection = outputWorkflow.requestGoogleAuthorization(syncDestinations.googleCalendarEnabled)
        if (rejection != null) {
            feedback = rejection
            return
        }
        feedback = googleAuthorizationDetail
    }

    fun resolveGoogleAuthorization(outcome: GoogleAuthorizationOutcome) {
        feedback = outputWorkflow.resolveGoogleAuthorization(outcome)
    }

    /** 只由已结构化识别的 Google 401 调用；不触碰日历、映射或其他目标状态。 */
    fun invalidateGoogleAuthorizationAfterUnauthorized(): String {
        val summary = outputWorkflow.invalidateGoogleAuthorization()
        feedback = summary
        return summary
    }

    /** 仅显示已脱敏的缓存清理结果；不保存令牌、账户或 Google 响应。 */
    fun resolveRejectedGoogleTokenCacheClear(cleared: Boolean) {
        outputWorkflow.resolveRejectedGoogleTokenCacheClear(cleared)?.let { feedback = it }
    }

    fun consumeGoogleAuthorizationRequest() = outputWorkflow.consumeGoogleAuthorizationRequest()

    /** 执行请求只表达用户确认；界面层再在后台建立专用目标并调用网关。 */
    fun requestManagedSyncExecution() {
        if (managedSyncPreview == null) { feedback = "先点击“写入日历”，查看数量后再确认。"; return }
        if (syncDestinations.localCalendarEnabled && !localCalendarPermissionGranted) { feedback = "本机日历没有获得系统权限；没有写入。"; return }
        if (syncDestinations.googleCalendarEnabled && !hasGoogleAuthorization) { feedback = "请先连接 Google 日历；没有写入。"; return }
        if (outputWorkflow.beginSync() != null) feedback = "正在写入日历…"
    }

    fun requestManagedRollback() {
        if (ruleLedger.mappings.isEmpty()) { feedback = "没有本应用写入的日程可清除。"; return }
        val targets = ruleLedger.mappings.map(StoredEventMapping::target).toSet()
        if (OutputTarget.LOCAL_CALENDAR in targets && !localCalendarPermissionGranted) { feedback = "本机日历没有获得系统权限；没有清除。"; return }
        if (OutputTarget.GOOGLE_CALENDAR in targets && !hasGoogleAuthorization) { feedback = "请先连接 Google 日历；没有清除。"; return }
        if (outputWorkflow.beginRollback() != null) feedback = "正在清除本应用写入的日程…"
    }

    fun currentGoogleAccessToken(): String? = outputWorkflow.currentGoogleAccessToken()
    fun currentManagedOperationToken(): Long = outputWorkflow.operationToken
    fun currentDesiredEvents(): List<DesiredCalendarEvent> = managedDesiredEvents
    fun currentManagedTargets(): Set<OutputTarget> = if (managedRollbackRequested) {
        ruleLedger.mappings.map(StoredEventMapping::target).toSet()
    } else {
        enabledOutputTargets()
    }

    /** 后台网关完成后只合并返回映射与脱敏计数，令牌和来源正文绝不进入资料库。 */
    fun resolveManagedOperation(token: Long, result: ManagedSyncResult, configuration: PrivateSyncConfiguration) {
        if (!outputWorkflow.finishOperation(token)) return
        ruleLedger = ruleLedger.copy(
            mappings = result.mappings,
            lastSummary = SyncRunSummary(Instant.now(), result.createdCount, result.updatedCount, result.removedCount, 0, result.failureSummary),
        )
        privateSyncConfiguration = configuration
        feedback = result.failureSummary ?: "日历已更新：新建 ${result.createdCount}、更新 ${result.updatedCount}、清除 ${result.removedCount}。"
    }

    fun failManagedOperation(token: Long, summary: String) {
        if (outputWorkflow.finishOperation(token)) feedback = summary
    }

    fun openManualEvents() = navigation.openManualEvents()

    /** 手动事项的 ID 由应用生成；时间采用 Europe/Berlin，输入错误不会改变资料库。 */
    fun addManualEvent(title: String, date: String, startHour: String, startMinute: String, endHour: String, endMinute: String, attendance: AttendanceState) {
        val item = runCatching {
            val day = LocalDate.parse(date)
            val start = LocalDateTime.of(day, java.time.LocalTime.of(startHour.toInt(), startMinute.toInt())).atZone(ZoneId.of("Europe/Berlin")).toInstant()
            val end = LocalDateTime.of(day, java.time.LocalTime.of(endHour.toInt(), endMinute.toInt())).atZone(ZoneId.of("Europe/Berlin")).toInstant()
            require(end.isAfter(start))
            ManualScheduleItem(UUID.randomUUID().toString(), title.trim().ifBlank { error("标题不能为空") }, start, end, attendance)
        }.getOrElse { feedback = "请填写有效的标题、YYYY-MM-DD 日期和小时／分钟；结束须晚于开始。"; return }
        ruleLedger = ruleLedger.copy(manualEvents = ManualScheduleEditor().upsert(ruleLedger.manualEvents, item))
        feedback = "校园事项已保存到这台设备。下次写入日历时会和课程一起处理。"
    }

    fun removeManualEvent(itemId: String) {
        ruleLedger = ruleLedger.copy(manualEvents = ManualScheduleEditor().remove(ruleLedger.manualEvents, itemId))
        feedback = "已从这台设备删除校园事项；日历没有改变。"
    }

    /** 处理应用内层级返回；只有主页面才交还给系统执行退出。 */
    fun handleBack(): Boolean = navigation.handleBack()

    fun canHandleBack(): Boolean = navigation.canHandleBack()

    private fun enabledOutputTargets(): Set<OutputTarget> = buildSet {
        if (syncDestinations.localCalendarEnabled) add(OutputTarget.LOCAL_CALENDAR)
        if (syncDestinations.googleCalendarEnabled) add(OutputTarget.GOOGLE_CALENDAR)
    }

    /** 来源读取和规则变更后的唯一 UI 回流点；不会变更来源、规则或任何目标日历。 */
    private fun refreshCoursesFromSource() {
        if (!sourceCatalogLoaded) return
        courses = SourceCourseProjection.courses(
            sourceCourses = sourceCourses,
            occurrences = currentSourceOccurrences,
            rules = ruleLedger.rules,
            previousCourses = courses,
            savedPresentations = savedCoursePresentations,
        )
    }

}

enum class AttendanceEditScope(val label: String, val description: String) {
    ALL_OCCURRENCES("全部日程", "修改这门课程的所有实例，包含过去日程"),
    ONLY_THIS_OCCURRENCE("只改本次", "只修改当前选定的一次实例"),
    CURRENT_AND_FUTURE("本次及以后", "从当前选定实例起修改，过去不变"),
    SPECIFIED_AND_FUTURE("指定日程及以后", "选择日期／实例后，从该处起修改"),
}

internal fun AppScreen.primaryIndex(): Int? = when (this) {
    AppScreen.OVERVIEW -> 0
    AppScreen.SEARCH -> 1
    AppScreen.FAVORITES -> 2
    AppScreen.SETTINGS -> 3
    else -> null
}
