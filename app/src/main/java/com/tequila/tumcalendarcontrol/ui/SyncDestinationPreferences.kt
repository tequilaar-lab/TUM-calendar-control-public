/*
 * 职责：在应用私有 SharedPreferences 中保存同步目标的开关选择。
 * 输入／输出：输入 SyncDestinations；输出恢复的 SyncDestinations，缺失时安全地全部关闭。
 * 外部副作用：只写入本应用沙盒；不访问日历 Provider、Google、网络或账户。
 * 不负责：令牌、账户标识、日历 ID、真实同步队列或跨设备配置同步。
 */
package com.tequila.tumcalendarcontrol.ui

import android.content.Context

class SyncDestinationPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): SyncDestinations = SyncDestinations(
        localCalendarEnabled = preferences.getBoolean(KEY_LOCAL_CALENDAR, false),
        googleCalendarEnabled = preferences.getBoolean(KEY_GOOGLE_CALENDAR, false),
    )

    fun save(destinations: SyncDestinations) {
        preferences.edit()
            .putBoolean(KEY_LOCAL_CALENDAR, destinations.localCalendarEnabled)
            .putBoolean(KEY_GOOGLE_CALENDAR, destinations.googleCalendarEnabled)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "sync_destinations"
        const val KEY_LOCAL_CALENDAR = "local_calendar_enabled"
        const val KEY_GOOGLE_CALENDAR = "google_calendar_enabled"
    }
}
