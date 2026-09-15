/*
 * 职责：将稳定的 AppScreen 值分发到对应 Compose 页面。
 * 输入／输出：输入 CalendarControlState 与当前 AppScreen；输出一个已存在的页面内容。
 * 外部副作用：无；各页面的用户操作仍通过 CalendarControlState 的既有入口表达。
 * 不负责：任何页面布局、课程／设置业务、导航栈修改、读取、授权或日历写入。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.runtime.Composable

/**
 * 只用于合成单元测试的显式路由清单。
 *
 * 运行时仍使用下方可由编译器检查穷尽性的 when；新增屏幕时，测试会要求同步更新本清单。
 */
internal val routedAppScreens: Set<AppScreen> =
    setOf(
    AppScreen.OVERVIEW,
    AppScreen.DETAIL,
    AppScreen.ATTENDANCE,
    AppScreen.COURSE_EDIT,
    AppScreen.SEARCH,
    AppScreen.FAVORITES,
    AppScreen.MORE,
    AppScreen.SETTINGS,
    AppScreen.PROFILE,
    AppScreen.SYNC_CONFIGURATION,
    AppScreen.SYNC_PREVIEW,
    AppScreen.SOURCE_RANGE,
    AppScreen.SOURCE_PREVIEW,
    AppScreen.SOURCE_ATTENDANCE,
    AppScreen.MANUAL_EVENTS,
    AppScreen.MANAGED_SYNC_PREVIEW,
    AppScreen.BACKGROUND_CHECK,
    AppScreen.RULE_BACKUP,
    )

@Composable
fun AppScreenContent(
    state: CalendarControlState,
    screen: AppScreen,
) {
    when (screen) {
        AppScreen.OVERVIEW,
        AppScreen.DETAIL,
        AppScreen.COURSE_EDIT,
        AppScreen.SEARCH,
        AppScreen.FAVORITES,
        -> CourseScreenContent(state, screen)

        AppScreen.MORE,
        AppScreen.SETTINGS,
        AppScreen.PROFILE,
        AppScreen.SYNC_CONFIGURATION,
        AppScreen.SYNC_PREVIEW,
        -> SettingsScreenContent(state, screen)

        AppScreen.ATTENDANCE,
        AppScreen.SOURCE_RANGE,
        AppScreen.SOURCE_PREVIEW,
        AppScreen.SOURCE_ATTENDANCE,
        AppScreen.MANUAL_EVENTS,
        AppScreen.MANAGED_SYNC_PREVIEW,
        AppScreen.BACKGROUND_CHECK,
        AppScreen.RULE_BACKUP,
        -> WorkflowScreenContent(state, screen)
    }
}

@Composable
private fun CourseScreenContent(state: CalendarControlState, screen: AppScreen) {
    when (screen) {
        AppScreen.OVERVIEW -> OverviewScreen(state)
        AppScreen.DETAIL -> DetailScreen(state)
        AppScreen.COURSE_EDIT -> CourseEditScreen(state)
        AppScreen.SEARCH -> SearchScreen(state)
        AppScreen.FAVORITES -> FavoritesScreen(state)
        else -> error("课程页面路由只接受课程屏幕")
    }
}

@Composable
private fun SettingsScreenContent(state: CalendarControlState, screen: AppScreen) {
    when (screen) {
        AppScreen.MORE -> MoreScreen(state)
        AppScreen.SETTINGS -> SettingsScreen(state)
        AppScreen.PROFILE -> ProfileScreen(state)
        AppScreen.SYNC_CONFIGURATION -> SyncConfigurationScreen(state)
        AppScreen.SYNC_PREVIEW -> SyncPreviewScreen(state)
        else -> error("设置页面路由只接受设置屏幕")
    }
}

@Composable
private fun WorkflowScreenContent(state: CalendarControlState, screen: AppScreen) {
    when (screen) {
        AppScreen.ATTENDANCE -> AttendanceScreen(state)
        AppScreen.SOURCE_RANGE -> SourceRangeScreen(state)
        AppScreen.SOURCE_PREVIEW -> SourceCourseSelectorScreen(state)
        AppScreen.SOURCE_ATTENDANCE -> SourceAttendanceScreen(state)
        AppScreen.MANUAL_EVENTS -> ManualEventsScreen(state)
        AppScreen.MANAGED_SYNC_PREVIEW -> ManagedSyncPreviewScreen(state)
        AppScreen.BACKGROUND_CHECK -> BackgroundCheckScreen(state)
        AppScreen.RULE_BACKUP -> RuleBackupScreen(state)
        else -> error("工作流页面路由只接受工作流屏幕")
    }
}
