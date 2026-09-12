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
import com.tequila.tumcalendarcontrol.sync.RuleEditCommand
import com.tequila.tumcalendarcontrol.sync.RuleLedgerEditor
import com.tequila.tumcalendarcontrol.sync.SyncRule
import java.time.LocalDate

enum class AppScreen {
    OVERVIEW, DETAIL, ATTENDANCE, COURSE_EDIT, SEARCH, FAVORITES, MORE, SETTINGS, PROFILE, SYNC_CONFIGURATION, SYNC_PREVIEW,
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
    var navigationDirection by mutableStateOf(NavigationDirection.FORWARD)
        private set
    private var detailReturnScreen by mutableStateOf(AppScreen.OVERVIEW)
    private var moreReturnScreen by mutableStateOf(AppScreen.OVERVIEW)
    private var profileReturnScreen by mutableStateOf(AppScreen.OVERVIEW)
    private var syncConfigurationReturnScreen by mutableStateOf(AppScreen.SETTINGS)
    private var syncPreviewReturnScreen by mutableStateOf(AppScreen.SETTINGS)
    private var courseEditReturnScreen by mutableStateOf(AppScreen.DETAIL)

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
        syncDestinations = syncDestinations.withEnabled(SyncDestination.LOCAL_CALENDAR, granted)
        feedback = if (granted) "已获本机日历写入许可；本轮仍不会创建或写入日历。" else "未获本机日历权限，已保持本机输出关闭。"
    }

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
        feedback = "同步引擎尚未配置；本轮不会读取或写入任何真实日历。"
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
    )

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
