/*
 * 职责：保存离线原型的屏幕栈、主题、选课、搜索、收藏、出席状态、个人资料及同步目标状态。
 * 输入／输出：接收用户的本地点击；输出 Compose 可观察状态和更新后的合成课程列表。
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
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import java.time.LocalDate

enum class AppScreen {
    OVERVIEW, DETAIL, ATTENDANCE, COURSE_EDIT, SEARCH, FAVORITES, MORE, SETTINGS, PROFILE, SYNC_CONFIGURATION, SYNC_PREVIEW, SOURCE_PREVIEW, SOURCE_ATTENDANCE, MANUAL_EVENTS, MANAGED_SYNC_PREVIEW,
}

enum class ThemeMode(val label: String) {
    LIGHT("浅色模式"),
    DARK("深色模式"),
    SYSTEM("跟随系统"),
}

enum class NavigationDirection {
    FORWARD, BACKWARD,
}

class CalendarControlState(
    initialCourses: List<Course> = SampleCourses.all(),
    initialProfile: UserProfile = UserProfile(),
    initialSyncDestinations: SyncDestinations = SyncDestinations(),
    initialPrivateSyncConfiguration: PrivateSyncConfiguration = PrivateSyncConfiguration(),
    initialRuleLedger: RuleLedger = RuleLedger(),
) {
    var courses by mutableStateOf(initialCourses)
        private set
    var screen by mutableStateOf(AppScreen.OVERVIEW)
        private set
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
    val offlinePreview: SyncPreview = OfflinePreviewSamples.preview()
    var sourceReadRequested by mutableStateOf(false)
        private set
    var sourceReadInProgress by mutableStateOf(false)
        private set
    var sourcePreview by mutableStateOf<IcalReadOnlyPreview?>(null)
        private set
    var sourcePreviewError by mutableStateOf<String?>(null)
        private set
    /** 真实来源资料仅在当前进程内保存，重新读取或退出后由用户源重新生成。 */
    var sourceCourses by mutableStateOf<List<SourceCourse>>(emptyList())
        private set
    /** 真正来源实例只在当前进程用于预览／同步候选，退出或重新读取时丢弃。 */
    private var sourceOccurrences by mutableStateOf<List<SourceCalendarOccurrence>>(emptyList())
    var selectedSourceCourseUids by mutableStateOf<Set<String>>(emptySet())
        private set
    var pendingSourceAttendance by mutableStateOf(AttendanceState.CHOOSE_TO_ATTEND)
    var pendingSourceAttendanceScope by mutableStateOf(AttendanceEditScope.ALL_OCCURRENCES)
    var pendingSourceEffectiveDate by mutableStateOf("")
    private var sourceAttendanceReturnScreen by mutableStateOf(AppScreen.SOURCE_PREVIEW)
    var managedSyncPreview by mutableStateOf<ManagedSyncPreview?>(null)
        private set
    private var managedDesiredEvents by mutableStateOf<List<DesiredCalendarEvent>>(emptyList())
    var managedSyncRequested by mutableStateOf(false)
        private set
    var managedRollbackRequested by mutableStateOf(false)
        private set
    var managedSyncInProgress by mutableStateOf(false)
        private set
    var googleAuthorizationRequested by mutableStateOf(false)
        private set
    private var googleAccessToken by mutableStateOf<String?>(null)
    val hasGoogleAuthorization: Boolean get() = googleAccessToken != null
    var localCalendarPermissionGranted by mutableStateOf(false)
        private set
    private var managedOperationToken by mutableStateOf(0L)
    /** 只读请求的单调编号；忽略超时后才返回的旧线程结果，避免覆盖新的用户操作。 */
    var sourceReadToken by mutableStateOf(0L)
        private set
    var navigationDirection by mutableStateOf(NavigationDirection.FORWARD)
        private set
    private var detailReturnScreen by mutableStateOf(AppScreen.OVERVIEW)
    private var moreReturnScreen by mutableStateOf(AppScreen.OVERVIEW)
    private var profileReturnScreen by mutableStateOf(AppScreen.OVERVIEW)
    private var syncConfigurationReturnScreen by mutableStateOf(AppScreen.SETTINGS)
    private var syncPreviewReturnScreen by mutableStateOf(AppScreen.SETTINGS)
    private var courseEditReturnScreen by mutableStateOf(AppScreen.DETAIL)
    private var manualEventsReturnScreen by mutableStateOf(AppScreen.SETTINGS)
    private var managedSyncPreviewReturnScreen by mutableStateOf(AppScreen.SETTINGS)

    /** 更多操作覆盖层打开时，仍保持下方主页面稳定，避免与主标签页共用过渡。 */
    val contentScreen: AppScreen
        get() = if (screen == AppScreen.MORE) moreReturnScreen else screen

    val isMoreOpen: Boolean
        get() = screen == AppScreen.MORE

    val selectedCourse: Course
        get() = courses.first { it.id == selectedCourseId }

    val searchedCourses: List<Course>
        get() = courses.filter { it.hasFixedPlus && it.sourceStatus in visibleSourceStatuses && it.displayTitle.contains(searchQuery, ignoreCase = true) }

    val favoriteCourses: List<Course>
        get() = courses.filter { it.isFavorite && it.hasFixedPlus }

    /** 主标签页按相对位置决定左右动效；详情等层级页面仍使用前进动效。 */
    fun open(screen: AppScreen) {
        val source = contentScreen
        navigationDirection = navigationDirectionBetween(source, screen)
        this.screen = screen
    }

    fun openCourse(courseId: String) {
        detailReturnScreen = contentScreen
        selectedCourseId = courseId
        pendingAttendance = selectedCourse.attendance
        open(AppScreen.DETAIL)
    }

    fun openAttendance() {
        pendingAttendance = selectedCourse.attendance
        pendingAttendanceScope = AttendanceEditScope.ALL_OCCURRENCES
        pendingEffectiveDate = ""
        feedback = null
        open(AppScreen.ATTENDANCE)
    }

    fun openCourseEdit() { courseEditReturnScreen = AppScreen.DETAIL; open(AppScreen.COURSE_EDIT) }

    /** 仅应用私有覆盖；来源 title、schedule、location 永远保持不变。 */
    fun updateSelectedCourseOverride(override: CourseLocalOverride) {
        courses = courses.map { if (it.id == selectedCourseId) it.copy(localOverride = override) else it }
        feedback = "已保存本机课程覆盖；不会回写 TUM 来源。"
    }

    fun toggleSourceStatusVisible(status: TumEventStatus) {
        visibleSourceStatuses = if (status in visibleSourceStatuses) visibleSourceStatuses - status else visibleSourceStatuses + status
    }

    fun openMore() {
        moreReturnScreen = contentScreen
        screen = AppScreen.MORE
    }

    fun openProfile() {
        profileReturnScreen = contentScreen
        screen = AppScreen.PROFILE
    }

    /** 已由界面层校验并提交的本机个人资料；不代表任何外部账户。 */
    fun updateProfile(updatedProfile: UserProfile) {
        profile = updatedProfile.copy(displayName = updatedProfile.displayName.trim().ifBlank { UserProfile().displayName })
    }

    /** 只更新未来输出许可；真正的日历连接和写入仍由后续经授权的同步模块负责。 */
    fun setSyncDestinationEnabled(destination: SyncDestination, enabled: Boolean) {
        syncDestinations = syncDestinations.withEnabled(destination, enabled)
    }

    /** 申请权限不是写入：只有系统同意后才保存未来本机输出许可。 */
    fun requestLocalCalendarOutput() { localCalendarPermissionRequestPending = true }

    fun resolveLocalCalendarPermission(granted: Boolean) {
        localCalendarPermissionRequestPending = false
        localCalendarPermissionGranted = granted
        syncDestinations = syncDestinations.withEnabled(SyncDestination.LOCAL_CALENDAR, granted)
        feedback = if (granted) "已获本机日历写入许可；本轮仍不会创建或写入日历。" else "未获本机日历权限，已保持本机输出关闭。"
    }

    /** 启动时仅读取系统已授予状态，不申请权限也不改变用户的输出开关。 */
    fun refreshLocalCalendarPermission(granted: Boolean) { localCalendarPermissionGranted = granted }

    /** 保存前由页面展示格式问题；状态机不发起任何连接或日历写入。 */
    fun updatePrivateSyncConfiguration(configuration: PrivateSyncConfiguration) {
        require(configuration.validate().isEmpty()) { "同步配置格式无效。" }
        privateSyncConfiguration = configuration
    }

    fun openSyncConfiguration() {
        syncConfigurationReturnScreen = contentScreen
        screen = AppScreen.SYNC_CONFIGURATION
    }

    /** 预览只展示合成差异，不能触发网络、权限申请或任何日历写入。 */
    fun openSyncPreview() { syncPreviewReturnScreen = contentScreen; screen = AppScreen.SYNC_PREVIEW }

    /**
     * 读取请求仅作为 UI 意图；界面层随后在后台执行一次 HTTPS 只读获取。
     * 配置不存在时不发出请求，以防空地址被误认为一次同步。
     */
    fun requestSourcePreview() {
        if (!privateSyncConfiguration.hasTumSubscription) {
            feedback = "请先保存 HTTPS 私有 iCal 地址；当前未发起网络请求。"
            return
        }
        if (sourceReadInProgress) return
        sourcePreview = null
        sourcePreviewError = null
        sourceReadToken += 1
        sourceReadInProgress = true
        sourceReadRequested = true
        feedback = "正在只读读取私有 iCal；不会写入任何日历。"
    }

    /** 后台读取完成后保留摘要与仅本次运行可见的课程目录；不保留 URL 或 ICS 原文。 */
    fun resolveSourcePreview(token: Long, result: IcalReadOnlyImportResult) {
        if (token != sourceReadToken || !sourceReadInProgress) return
        sourceReadRequested = false
        sourceReadInProgress = false
        when (result) {
            is IcalReadOnlyImportResult.Success -> {
                sourcePreview = result.preview
                sourceCourses = result.preview.courses
                sourceOccurrences = result.preview.occurrences
                selectedSourceCourseUids = emptySet()
                sourcePreviewError = null
                syncPreviewReturnScreen = contentScreen
                screen = AppScreen.SOURCE_PREVIEW
            }
            is IcalReadOnlyImportResult.Failure -> {
                sourcePreview = null
                sourcePreviewError = result.message
                feedback = "私有 iCal 未导入：${result.message}。未写入任何日历。"
            }
        }
    }

    /** 课程目录的多选只保留 UID；来源标题、地点与正文不进入规则资料库。 */
    fun toggleSourceCourseSelection(sourceUid: String) {
        selectedSourceCourseUids = if (sourceUid in selectedSourceCourseUids) {
            selectedSourceCourseUids - sourceUid
        } else {
            selectedSourceCourseUids + sourceUid
        }
    }

    fun selectAllSourceCourses() { selectedSourceCourseUids = sourceCourses.map(SourceCourse::sourceUid).toSet() }

    fun clearSourceCourseSelection() { selectedSourceCourseUids = emptySet() }

    /** 新来源课程无规则时默认蓝色；用户可按单课或多选打开同一三态编辑器。 */
    fun sourceAttendanceFor(sourceUid: String): AttendanceState = ruleLedger.rules
        .firstOrNull { it.sourceUid == sourceUid }
        ?.defaultState
        ?: AttendanceState.CHOOSE_TO_ATTEND

    fun openSourceAttendanceEditor(sourceUids: Set<String> = selectedSourceCourseUids) {
        if (sourceUids.isEmpty()) {
            feedback = "请先选择至少一门来源课程。"
            return
        }
        val firstState = sourceAttendanceFor(sourceUids.first())
        pendingSourceAttendance = firstState
        pendingSourceAttendanceScope = AttendanceEditScope.ALL_OCCURRENCES
        pendingSourceEffectiveDate = ""
        selectedSourceCourseUids = sourceUids
        sourceAttendanceReturnScreen = AppScreen.SOURCE_PREVIEW
        screen = AppScreen.SOURCE_ATTENDANCE
    }

    /** 将 UI 范围转换为 UID 规则；“仅本次”取每门课的代表实例，避免跨 UID 误用实例键。 */
    fun confirmSourceAttendance() {
        val selected = sourceCourses.filter { it.sourceUid in selectedSourceCourseUids }
        if (selected.isEmpty()) {
            feedback = "来源课程目录已失效；请重新读取后再修改。"
            screen = sourceAttendanceReturnScreen
            return
        }
        val sourceUids = selected.map(SourceCourse::sourceUid).toSet()
        val command = when (pendingSourceAttendanceScope) {
            AttendanceEditScope.ALL_OCCURRENCES -> RuleEditCommand.WholeCourse(sourceUids, pendingSourceAttendance)
            AttendanceEditScope.CURRENT_AND_FUTURE -> RuleEditCommand.FromDate(sourceUids, LocalDate.now(), pendingSourceAttendance)
            AttendanceEditScope.SPECIFIED_AND_FUTURE -> runCatching { LocalDate.parse(pendingSourceEffectiveDate) }.fold(
                onSuccess = { RuleEditCommand.FromDate(sourceUids, it, pendingSourceAttendance) },
                onFailure = { feedback = "请输入 YYYY-MM-DD 格式的指定日期。"; return },
            )
            AttendanceEditScope.ONLY_THIS_OCCURRENCE -> RuleEditCommand.InstancesBySource(
                sourceUids = sourceUids,
                instanceKeysBySourceUid = selected.associate { it.sourceUid to setOf(it.representativeInstanceKey) },
                state = pendingSourceAttendance,
            )
        }
        val existing = ruleLedger.rules.associateBy(SyncRule::sourceUid)
        val ensuredRules = sourceUids.map { uid ->
            // 该历史字段只为旧本机规则编码兼容而保留；来源目录不再以它过滤课程。
            existing[uid] ?: SyncRule(uid, hasConfirmedFixplatz = true, defaultState = AttendanceState.CHOOSE_TO_ATTEND)
        }
        ruleLedger = ruleLedger.copy(rules = RuleLedgerEditor.apply(
            ruleLedger.rules.filterNot { it.sourceUid in sourceUids } + ensuredRules,
            command,
        ))
        feedback = "已更新 ${sourceUids.size} 门来源课程的${pendingSourceAttendance.label}规则；尚未写入任何日历。"
        screen = sourceAttendanceReturnScreen
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
        feedback = "已在本地原型中更新“${selectedCourse.displayTitle}”的状态（${pendingAttendanceScope.label}）"
        open(AppScreen.DETAIL)
    }

    private fun attendanceCommand(): RuleEditCommand? = when (pendingAttendanceScope) {
        AttendanceEditScope.ALL_OCCURRENCES -> RuleEditCommand.WholeCourse(setOf(selectedCourseId), pendingAttendance)
        AttendanceEditScope.ONLY_THIS_OCCURRENCE -> RuleEditCommand.Instances(setOf(selectedCourseId), setOf("$selectedCourseId:current-synthetic"), pendingAttendance)
        AttendanceEditScope.CURRENT_AND_FUTURE -> RuleEditCommand.FromDate(setOf(selectedCourseId), LocalDate.now(), pendingAttendance)
        AttendanceEditScope.SPECIFIED_AND_FUTURE -> runCatching { LocalDate.parse(pendingEffectiveDate) }.fold(
            onSuccess = { RuleEditCommand.FromDate(setOf(selectedCourseId), it, pendingAttendance) },
            onFailure = { feedback = "请输入 YYYY-MM-DD 格式的指定日期。"; null },
        )
    }

    fun toggleFavorite(courseId: String) {
        courses = courses.map { course ->
            if (course.id == courseId) course.copy(isFavorite = !course.isFavorite) else course
        }
    }

    fun requestSync() {
        requestManagedSyncPreview()
    }

    /** 基于当前进程来源和本机规则生成差异；不查询、创建或写入任何目标日历。 */
    fun requestManagedSyncPreview() {
        if (sourceOccurrences.isEmpty()) {
            feedback = "请先主动读取私有 iCal 并确认课程目录；当前没有来源实例可预览。"
            return
        }
        val targets = enabledOutputTargets()
        if (targets.isEmpty()) {
            feedback = "请先启用本机日历或 Google 日历输出；当前不会写入任何日历。"
            return
        }
        managedDesiredEvents = SyncCandidateFactory().build(sourceOccurrences, ruleLedger.rules, ruleLedger.manualEvents)
        managedSyncPreview = ManagedSyncEngine().preview(managedDesiredEvents, ruleLedger.mappings, targets)
        managedSyncPreviewReturnScreen = contentScreen
        screen = AppScreen.MANAGED_SYNC_PREVIEW
    }

    /** Google 授权只由显式点按触发；令牌成功后仅存活在本进程。 */
    fun requestGoogleAuthorization() {
        if (!syncDestinations.googleCalendarEnabled) {
            feedback = "请先开启 Google 日历镜像输出；当前未发起授权。"
            return
        }
        googleAuthorizationRequested = true
    }

    fun resolveGoogleAuthorization(outcome: GoogleAuthorizationOutcome) {
        googleAuthorizationRequested = false
        when (outcome) {
            is GoogleAuthorizationOutcome.AccessToken -> { googleAccessToken = outcome.value; feedback = "Google 日历已获本次运行的授权；尚未创建或写入日历。" }
            is GoogleAuthorizationOutcome.Failure -> feedback = outcome.summary
            is GoogleAuthorizationOutcome.LaunchConsent -> Unit
        }
    }

    fun consumeGoogleAuthorizationRequest() { googleAuthorizationRequested = false }

    /** 执行请求只表达用户确认；界面层再在后台建立专用目标并调用网关。 */
    fun requestManagedSyncExecution() {
        if (managedSyncPreview == null) { feedback = "请先生成同步预览，再确认执行。"; return }
        if (syncDestinations.localCalendarEnabled && !localCalendarPermissionGranted) { feedback = "本机日历尚未获得系统权限；未执行写入。"; return }
        if (syncDestinations.googleCalendarEnabled && !hasGoogleAuthorization) { feedback = "请先完成 Google 授权；未执行写入。"; return }
        if (managedSyncInProgress) return
        managedOperationToken += 1
        managedSyncInProgress = true
        managedSyncRequested = true
        feedback = "正在执行已预览的受控同步…"
    }

    fun requestManagedRollback() {
        if (ruleLedger.mappings.isEmpty()) { feedback = "没有本应用已映射的输出事件可撤销。"; return }
        val targets = ruleLedger.mappings.map(StoredEventMapping::target).toSet()
        if (OutputTarget.LOCAL_CALENDAR in targets && !localCalendarPermissionGranted) { feedback = "本机日历尚未获得系统权限；未执行撤销。"; return }
        if (OutputTarget.GOOGLE_CALENDAR in targets && !hasGoogleAuthorization) { feedback = "请先完成 Google 授权；未执行撤销。"; return }
        if (managedSyncInProgress) return
        managedOperationToken += 1
        managedSyncInProgress = true
        managedRollbackRequested = true
        feedback = "正在撤销本应用已映射的输出事件…"
    }

    fun currentGoogleAccessToken(): String? = googleAccessToken
    fun currentManagedOperationToken(): Long = managedOperationToken
    fun currentDesiredEvents(): List<DesiredCalendarEvent> = managedDesiredEvents
    fun currentManagedTargets(): Set<OutputTarget> = if (managedRollbackRequested) {
        ruleLedger.mappings.map(StoredEventMapping::target).toSet()
    } else {
        enabledOutputTargets()
    }

    /** 后台网关完成后只合并返回映射与脱敏计数，令牌和来源正文绝不进入资料库。 */
    fun resolveManagedOperation(token: Long, result: ManagedSyncResult, configuration: PrivateSyncConfiguration) {
        if (token != managedOperationToken || !managedSyncInProgress) return
        managedSyncRequested = false
        managedRollbackRequested = false
        managedSyncInProgress = false
        ruleLedger = ruleLedger.copy(
            mappings = result.mappings,
            lastSummary = SyncRunSummary(Instant.now(), result.createdCount, result.updatedCount, result.removedCount, 0, result.failureSummary),
        )
        privateSyncConfiguration = configuration
        feedback = result.failureSummary ?: "受控同步已完成：新增 ${result.createdCount}、更新 ${result.updatedCount}、移除 ${result.removedCount}。"
    }

    fun failManagedOperation(token: Long, summary: String) {
        if (token != managedOperationToken || !managedSyncInProgress) return
        managedSyncRequested = false
        managedRollbackRequested = false
        managedSyncInProgress = false
        feedback = summary
    }

    fun openManualEvents() { manualEventsReturnScreen = contentScreen; screen = AppScreen.MANUAL_EVENTS }

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
        feedback = "已保存手动校园事项；它将在下次同步预览中出现。"
    }

    fun removeManualEvent(itemId: String) {
        ruleLedger = ruleLedger.copy(manualEvents = ManualScheduleEditor().remove(ruleLedger.manualEvents, itemId))
        feedback = "已从本机规则移除手动事项；尚未改动目标日历。"
    }

    /** 处理应用内层级返回；只有主页面才交还给系统执行退出。 */
    fun handleBack(): Boolean {
        val destination = when (screen) {
            AppScreen.ATTENDANCE -> AppScreen.DETAIL
            AppScreen.COURSE_EDIT -> courseEditReturnScreen
            AppScreen.DETAIL -> detailReturnScreen
            AppScreen.MORE -> moreReturnScreen
            AppScreen.PROFILE -> profileReturnScreen
            AppScreen.SYNC_CONFIGURATION -> syncConfigurationReturnScreen
            AppScreen.SYNC_PREVIEW -> syncPreviewReturnScreen
            AppScreen.SOURCE_PREVIEW -> syncPreviewReturnScreen
            AppScreen.SOURCE_ATTENDANCE -> sourceAttendanceReturnScreen
            AppScreen.MANUAL_EVENTS -> manualEventsReturnScreen
            AppScreen.MANAGED_SYNC_PREVIEW -> managedSyncPreviewReturnScreen
            else -> return false
        }
        navigationDirection = NavigationDirection.BACKWARD
        screen = destination
        return true
    }

    fun canHandleBack(): Boolean = screen in setOf(
        AppScreen.DETAIL,
        AppScreen.ATTENDANCE,
        AppScreen.COURSE_EDIT,
        AppScreen.MORE,
        AppScreen.PROFILE,
        AppScreen.SYNC_CONFIGURATION,
        AppScreen.SYNC_PREVIEW,
        AppScreen.SOURCE_PREVIEW,
        AppScreen.SOURCE_ATTENDANCE,
        AppScreen.MANUAL_EVENTS,
        AppScreen.MANAGED_SYNC_PREVIEW,
    )

    private fun enabledOutputTargets(): Set<OutputTarget> = buildSet {
        if (syncDestinations.localCalendarEnabled) add(OutputTarget.LOCAL_CALENDAR)
        if (syncDestinations.googleCalendarEnabled) add(OutputTarget.GOOGLE_CALENDAR)
    }

    private fun navigationDirectionBetween(source: AppScreen, destination: AppScreen): NavigationDirection {
        val sourceIndex = source.primaryIndex()
        val destinationIndex = destination.primaryIndex()
        return if (sourceIndex != null && destinationIndex != null && destinationIndex < sourceIndex) {
            NavigationDirection.BACKWARD
        } else {
            NavigationDirection.FORWARD
        }
    }
}

enum class AttendanceEditScope(val label: String, val description: String) {
    ALL_OCCURRENCES("全部日程", "修改这门课程的所有实例，包含过去日程"),
    ONLY_THIS_OCCURRENCE("只改本次", "只修改当前选定的一次实例"),
    CURRENT_AND_FUTURE("本次及以后", "从当前选定实例起修改，过去不变"),
    SPECIFIED_AND_FUTURE("指定日程及以后", "选择日期／实例后，从该处起修改"),
}

private fun AppScreen.primaryIndex(): Int? = when (this) {
    AppScreen.OVERVIEW -> 0
    AppScreen.SEARCH -> 1
    AppScreen.FAVORITES -> 2
    AppScreen.SETTINGS -> 3
    else -> null
}
