/*
 * 职责：将私有同步配置以 Android Keystore 加密后保存到应用私有偏好。
 * 输入／输出：输入 PrivateSyncConfiguration；输出配置或安全默认值。
 * 外部副作用：写应用私有 SharedPreferences 与 Android Keystore；不联网、不访问日历或账户。
 * 不负责：令牌交换、自动备份、导出私有地址或恢复到另一设备。
 */
package com.tequila.tumcalendarcontrol.sync

import android.content.Context

class PrivateSyncConfigurationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val cipher = KeystoreTextCipher()

    fun load(): PrivateSyncConfiguration = runCatching {
        PrivateSyncConfiguration(
            tumSubscriptionUrl = preferences.getString(KEY_TUM_URL, null)?.let(cipher::decrypt).orEmpty(),
            googleOAuthClientId = preferences.getString(KEY_GOOGLE_CLIENT_ID, null)?.let(cipher::decrypt).orEmpty(),
            googleCalendarName = preferences.getString(KEY_GOOGLE_CALENDAR_NAME, null)?.let(cipher::decrypt)
                ?: PrivateSyncConfiguration.DEFAULT_GOOGLE_CALENDAR_NAME,
        )
    }.getOrElse { PrivateSyncConfiguration() }

    fun save(configuration: PrivateSyncConfiguration) {
        require(configuration.validate().isEmpty()) { "私有同步配置格式无效，拒绝保存。" }
        check(preferences.edit()
            .putString(KEY_TUM_URL, cipher.encrypt(configuration.tumSubscriptionUrl.trim()))
            .putString(KEY_GOOGLE_CLIENT_ID, cipher.encrypt(configuration.googleOAuthClientId.trim()))
            .putString(KEY_GOOGLE_CALENDAR_NAME, cipher.encrypt(configuration.googleCalendarName.trim()))
            .commit()) { "私有配置未能保存到应用本机存储。" }
    }

    private companion object {
        const val FILE_NAME = "private_sync_configuration"
        const val KEY_TUM_URL = "tum_subscription_url"
        const val KEY_GOOGLE_CLIENT_ID = "google_oauth_client_id"
        const val KEY_GOOGLE_CALENDAR_NAME = "google_calendar_name"
    }
}
