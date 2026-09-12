/*
 * 职责：在首次解析前配置 iCal4j 使用进程内 Map 时区缓存，避免要求 Android 提供 JCache provider。
 * 输入／输出：无输入；输出为已设置或已尊重既有设置的 iCal4j 系统属性。
 * 外部副作用：仅设置当前 App 进程的 iCal4j 配置属性；不联网、不读写日历或用户数据。
 * 不负责：下载时区、改变设备系统设置或覆盖调用方已经设置的缓存实现。
 */
package com.tequila.tumcalendarcontrol.sync

internal object Ical4jRuntimeConfiguration {
    private const val CACHE_PROPERTY = "net.fortuna.ical4j.timezone.cache.impl"
    private const val MAP_CACHE = "net.fortuna.ical4j.util.MapTimeZoneCache"

    /** iCal4j 在构造 CalendarBuilder 时读取此属性，必须早于该构造动作调用。 */
    fun configureOfflineCache() {
        if (System.getProperty(CACHE_PROPERTY).isNullOrBlank()) {
            System.setProperty(CACHE_PROPERTY, MAP_CACHE)
        }
    }
}
