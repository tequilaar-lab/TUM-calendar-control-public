/*
 * 职责：定义每个 AppScreen 的静态展示元数据和主导航属性。
 * 输入／输出：输入 AppScreen；输出标题、副标题、底栏索引及导航控件适用性。
 * 外部副作用：无。
 * 不负责：渲染页面、处理用户点击、保存状态、请求权限或读写日历。
 */
package com.tequila.tumcalendarcontrol.ui

internal fun AppScreen.usesBottomNavigation(): Boolean = this in setOf(
    AppScreen.OVERVIEW,
    AppScreen.SEARCH,
    AppScreen.FAVORITES,
    AppScreen.SETTINGS,
)

internal fun AppScreen.usesBackArrow(): Boolean = this in setOf(
    AppScreen.DETAIL,
    AppScreen.ATTENDANCE,
    AppScreen.COURSE_EDIT,
    AppScreen.PROFILE,
    AppScreen.SYNC_CONFIGURATION,
    AppScreen.SYNC_PREVIEW,
    AppScreen.SOURCE_RANGE,
    AppScreen.SOURCE_PREVIEW,
    AppScreen.SOURCE_ATTENDANCE,
    AppScreen.MANUAL_EVENTS,
    AppScreen.MANAGED_SYNC_PREVIEW,
    AppScreen.OUTPUT_CLEANUP,
    AppScreen.BACKGROUND_CHECK,
    AppScreen.RULE_BACKUP,
)

internal fun AppScreen.bottomNavigationIndex(): Int = when (this) {
    AppScreen.OVERVIEW -> 0
    AppScreen.SEARCH -> 1
    AppScreen.FAVORITES -> 2
    AppScreen.SETTINGS -> 3
    else -> 0
}

internal fun AppScreen.title(): String = when (this) {
    AppScreen.OVERVIEW -> "TUM 日程控制"
    AppScreen.DETAIL -> "课程详情"
    AppScreen.ATTENDANCE -> "状态选择"
    AppScreen.COURSE_EDIT -> "编辑课程"
    AppScreen.SEARCH -> "搜索课程"
    AppScreen.FAVORITES -> "收藏课程"
    AppScreen.MORE -> "更多操作"
    AppScreen.SETTINGS -> "设置与同步"
    AppScreen.PROFILE -> "个人资料"
    AppScreen.SYNC_CONFIGURATION -> "日历连接"
    AppScreen.SYNC_PREVIEW -> "离线示例"
    AppScreen.SOURCE_RANGE -> "选择读取时间"
    AppScreen.SOURCE_PREVIEW -> "选择课程"
    AppScreen.SOURCE_ATTENDANCE -> "设置出席与收藏"
    AppScreen.MANUAL_EVENTS -> "手动校园事项"
    AppScreen.MANAGED_SYNC_PREVIEW -> "确认写入日历"
    AppScreen.OUTPUT_CLEANUP -> "清理已写入日程"
    AppScreen.BACKGROUND_CHECK -> "检查课程更新"
    AppScreen.RULE_BACKUP -> "备份课程设置"
}

internal fun AppScreen.subtitle(): String? = when (this) {
    AppScreen.OVERVIEW -> "读取课程后会显示在这里"
    AppScreen.SETTINGS -> "读取课程、设置写入位置与外观"
    AppScreen.SYNC_CONFIGURATION -> "保存到这台设备，不会联网"
    AppScreen.SYNC_PREVIEW -> "示例不会写入日历"
    AppScreen.SOURCE_RANGE -> "选择时间后读取课程"
    AppScreen.SOURCE_PREVIEW -> "选择后设置出席与收藏"
    AppScreen.SOURCE_ATTENDANCE -> "保存后可返回课程列表写入日历"
    AppScreen.MANUAL_EVENTS -> "保存后可与课程一起写入日历"
    AppScreen.MANAGED_SYNC_PREVIEW -> "确认后才会修改所选日历"
    AppScreen.OUTPUT_CLEANUP -> "选择范围并确认后才会清理应用日程"
    AppScreen.BACKGROUND_CHECK -> "只检查课程变化，不会自动写入日历"
    AppScreen.RULE_BACKUP -> "备份课程设置；恢复前需确认"
    else -> null
}
