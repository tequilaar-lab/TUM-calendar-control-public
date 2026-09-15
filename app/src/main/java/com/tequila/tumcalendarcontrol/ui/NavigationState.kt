/*
 * 职责：保存应用内屏幕、返回目标、更多覆盖层背景与页面切换方向。
 * 输入／输出：接收 AppScreen 导航意图；输出当前屏幕、稳定内容屏幕及可否拦截返回。
 * 外部副作用：无；不读取课程、不保存配置、不申请权限、不访问网络或日历。
 * 不负责：课程选择、规则编辑、来源读取、授权、同步预览或输出执行。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class NavigationState {
    var screen by mutableStateOf(AppScreen.OVERVIEW)
        private set
    var direction by mutableStateOf(NavigationDirection.FORWARD)
        private set

    private var detailReturnScreen by mutableStateOf(AppScreen.OVERVIEW)
    private var moreReturnScreen by mutableStateOf(AppScreen.OVERVIEW)
    private var profileReturnScreen by mutableStateOf(AppScreen.OVERVIEW)
    private var syncConfigurationReturnScreen by mutableStateOf(AppScreen.SETTINGS)
    private var sourceRangeReturnScreen by mutableStateOf(AppScreen.SETTINGS)
    private var syncPreviewReturnScreen by mutableStateOf(AppScreen.SETTINGS)
    private var courseEditReturnScreen by mutableStateOf(AppScreen.DETAIL)
    private var sourceAttendanceReturnScreen by mutableStateOf(AppScreen.SOURCE_PREVIEW)
    private var manualEventsReturnScreen by mutableStateOf(AppScreen.SETTINGS)
    private var managedSyncPreviewReturnScreen by mutableStateOf(AppScreen.SETTINGS)
    private var outputCleanupReturnScreen by mutableStateOf(AppScreen.SETTINGS)
    private var backgroundCheckReturnScreen by mutableStateOf(AppScreen.SETTINGS)
    private var ruleBackupReturnScreen by mutableStateOf(AppScreen.SETTINGS)

    val contentScreen: AppScreen
        get() = if (screen == AppScreen.MORE) moreReturnScreen else screen
    val isMoreOpen: Boolean get() = screen == AppScreen.MORE

    fun open(destination: AppScreen) {
        direction = directionBetween(contentScreen, destination)
        screen = destination
    }

    fun openDetail() { detailReturnScreen = contentScreen; open(AppScreen.DETAIL) }
    fun openCourseEdit() { courseEditReturnScreen = AppScreen.DETAIL; open(AppScreen.COURSE_EDIT) }
    fun openMore() { moreReturnScreen = contentScreen; screen = AppScreen.MORE }
    /** 个人资料是左侧入口：进入从左滑入，返回时资料向左滑出、原页面从右进入。 */
    fun openProfile() { profileReturnScreen = contentScreen; direction = NavigationDirection.PROFILE_ENTER; screen = AppScreen.PROFILE }
    fun openSyncConfiguration() { syncConfigurationReturnScreen = contentScreen; openChild(AppScreen.SYNC_CONFIGURATION) }
    fun openSourceRange() { sourceRangeReturnScreen = contentScreen; direction = NavigationDirection.FORWARD; screen = AppScreen.SOURCE_RANGE }
    fun openSyncPreview() { syncPreviewReturnScreen = contentScreen; openChild(AppScreen.SYNC_PREVIEW) }
    fun openSourcePreview() { syncPreviewReturnScreen = contentScreen; openChild(AppScreen.SOURCE_PREVIEW) }
    fun openExistingSourcePreview() { openChild(AppScreen.SOURCE_PREVIEW) }
    fun openSourceAttendance() { sourceAttendanceReturnScreen = AppScreen.SOURCE_PREVIEW; openChild(AppScreen.SOURCE_ATTENDANCE) }
    fun openManualEvents() { manualEventsReturnScreen = contentScreen; openChild(AppScreen.MANUAL_EVENTS) }
    fun openManagedSyncPreview() { managedSyncPreviewReturnScreen = contentScreen; openChild(AppScreen.MANAGED_SYNC_PREVIEW) }
    fun openOutputCleanup() { outputCleanupReturnScreen = contentScreen; openChild(AppScreen.OUTPUT_CLEANUP) }
    fun openBackgroundCheck() { backgroundCheckReturnScreen = contentScreen; direction = NavigationDirection.FORWARD; screen = AppScreen.BACKGROUND_CHECK }
    fun openRuleBackup() { ruleBackupReturnScreen = contentScreen; direction = NavigationDirection.FORWARD; screen = AppScreen.RULE_BACKUP }

    fun handleBack(): Boolean {
        val closingProfile = screen == AppScreen.PROFILE
        val destination = when (screen) {
            AppScreen.ATTENDANCE -> AppScreen.DETAIL
            AppScreen.COURSE_EDIT -> courseEditReturnScreen
            AppScreen.DETAIL -> detailReturnScreen
            AppScreen.MORE -> moreReturnScreen
            AppScreen.PROFILE -> profileReturnScreen
            AppScreen.SYNC_CONFIGURATION -> syncConfigurationReturnScreen
            AppScreen.SYNC_PREVIEW -> syncPreviewReturnScreen
            AppScreen.SOURCE_RANGE -> sourceRangeReturnScreen
            AppScreen.SOURCE_PREVIEW -> syncPreviewReturnScreen
            AppScreen.SOURCE_ATTENDANCE -> sourceAttendanceReturnScreen
            AppScreen.MANUAL_EVENTS -> manualEventsReturnScreen
            AppScreen.MANAGED_SYNC_PREVIEW -> managedSyncPreviewReturnScreen
            AppScreen.OUTPUT_CLEANUP -> outputCleanupReturnScreen
            AppScreen.BACKGROUND_CHECK -> backgroundCheckReturnScreen
            AppScreen.RULE_BACKUP -> ruleBackupReturnScreen
            else -> return false
        }
        direction = if (closingProfile) NavigationDirection.PROFILE_EXIT else NavigationDirection.BACKWARD
        screen = destination
        return true
    }

    fun canHandleBack(): Boolean = screen !in setOf(
        AppScreen.OVERVIEW,
        AppScreen.SEARCH,
        AppScreen.FAVORITES,
        AppScreen.SETTINGS,
    )

    private fun directionBetween(source: AppScreen, destination: AppScreen): NavigationDirection {
        val sourceIndex = source.primaryIndex()
        val destinationIndex = destination.primaryIndex()
        return if (sourceIndex != null && destinationIndex != null && destinationIndex < sourceIndex) {
            NavigationDirection.BACKWARD
        } else {
            NavigationDirection.FORWARD
        }
    }

    /** 子页面不受上一轮主标签滑动方向影响，始终从右侧进入。 */
    private fun openChild(destination: AppScreen) {
        direction = NavigationDirection.FORWARD
        screen = destination
    }
}
