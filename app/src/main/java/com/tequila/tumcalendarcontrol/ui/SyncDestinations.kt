/*
 * 职责：定义同步结果可输出到的独立目标及其本机配置值。
 * 输入／输出：输入某个目标和开关值；输出不可变的 SyncDestinations 配置。
 * 外部副作用：无。
 * 不负责：账户授权、日历权限、日历创建、事件写入或网络同步。
 */
package com.tequila.tumcalendarcontrol.ui

/** 输出目标彼此独立；它们都只会消费同一份已通过本地规则筛选的结果。 */
enum class SyncDestination(
    val title: String,
    val disabledSummary: String,
    val enabledSummary: String,
) {
    LOCAL_CALENDAR(
        title = "本机荣耀日历",
        disabledSummary = "关闭：不会请求日历权限，也不会写入本机日历",
        enabledSummary = "已允许：完成日历授权后，写入本应用专用本机日历",
    ),
    GOOGLE_CALENDAR(
        title = "Google 日历镜像",
        disabledSummary = "关闭：不会连接 Google 账户或发送日程数据",
        enabledSummary = "已允许：完成授权后，只写入本应用创建的 Google 次级日历",
    ),
}

/** 用户切换输出开关前的暂存意图；确认前不改变偏好、权限或 Google 运行内授权。 */
data class PendingSyncDestinationChange(
    val destination: SyncDestination,
    val enabled: Boolean,
) {
    val title: String get() = if (enabled) "启用${destination.title}" else "关闭${destination.title}"
    /** 仅描述尚未确认的输出变化；不读取账户、日程或日历内容。 */
    fun consequence(current: SyncDestinations): String = when (destination) {
        SyncDestination.LOCAL_CALENDAR -> if (enabled) {
            "确认后会请求本机日历权限；获得权限后，未来确认写入才会写入应用专用本机日历。" +
                current.duplicateDisplayHint(otherEnabled = current.googleCalendarEnabled)
        } else {
            "确认后不再向本机日历写入；已经写入的日程不会被删除。"
        }
        SyncDestination.GOOGLE_CALENDAR -> if (enabled) {
            "确认后可手动连接 Google 日历；连接和确认写入前不会发送任何日程。" +
                current.duplicateDisplayHint(otherEnabled = current.localCalendarEnabled)
        } else {
            "确认后会结束本次 Google 授权并停止未来镜像；已经写入的日程不会被删除。"
        }
    }
}

private fun SyncDestinations.duplicateDisplayHint(otherEnabled: Boolean): String =
    if (otherEnabled) {
        " 另一种输出已开启；若荣耀日历同时显示两本应用日历，同一课次会出现两份。想只显示一份，请只启用一种输出，或在荣耀日历隐藏其中一本。"
    } else {
        ""
    }

/**
 * 只保存“未来允许输出到哪里”的用户意图。
 * 默认全部关闭，防止原型阶段产生任何意外外部写入。
 */
data class SyncDestinations(
    val localCalendarEnabled: Boolean = false,
    val googleCalendarEnabled: Boolean = false,
) {
    fun isEnabled(destination: SyncDestination): Boolean = when (destination) {
        SyncDestination.LOCAL_CALENDAR -> localCalendarEnabled
        SyncDestination.GOOGLE_CALENDAR -> googleCalendarEnabled
    }

    fun withEnabled(destination: SyncDestination, enabled: Boolean): SyncDestinations = when (destination) {
        SyncDestination.LOCAL_CALENDAR -> copy(localCalendarEnabled = enabled)
        SyncDestination.GOOGLE_CALENDAR -> copy(googleCalendarEnabled = enabled)
    }
}
