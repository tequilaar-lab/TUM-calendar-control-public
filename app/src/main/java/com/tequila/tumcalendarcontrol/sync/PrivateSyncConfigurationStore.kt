/*
 * Aufgabe: Private Synchronisierungskonfiguration Keystore-verschlüsselt in privaten Präferenzen speichern.
 * Ein-/Ausgabe: Private Konfiguration -> verschlüsselte Wiederherstellung aus privaten Präferenzen.
 * Nebenwirkungen: Nur Keystore-verschlüsselte app-private Speicherung.
 * Nicht zuständig: Token-Austausch, automatische Sicherung, Export privater Adressen oder Wiederherstellung auf anderen Geräten.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.i18n.message

import android.content.Context

class PrivateSyncConfigurationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val cipher = KeystoreTextCipher()

    fun load(): PrivateSyncConfiguration = runCatching {
        PrivateSyncConfiguration(
            tumSubscriptionUrl = preferences.getString(KEY_TUM_URL, null)?.let(cipher::decrypt).orEmpty(),
            sourceRangeStartDate = preferences.getString(KEY_SOURCE_RANGE_START, null)?.let(cipher::decrypt).orEmpty(),
            sourceRangeEndDate = preferences.getString(KEY_SOURCE_RANGE_END, null)?.let(cipher::decrypt).orEmpty(),
            googleOAuthClientId = preferences.getString(KEY_GOOGLE_CLIENT_ID, null)?.let(cipher::decrypt).orEmpty(),
            googleCalendarName = preferences.getString(KEY_GOOGLE_CALENDAR_NAME, null)?.let(cipher::decrypt)
                ?: PrivateSyncConfiguration.DEFAULT_GOOGLE_CALENDAR_NAME,
            localCalendarId = preferences.getString(KEY_LOCAL_CALENDAR_ID, null)?.let(cipher::decrypt).orEmpty(),
            googleCalendarId = preferences.getString(KEY_GOOGLE_CALENDAR_ID, null)?.let(cipher::decrypt).orEmpty(),
        )
    }.getOrElse { PrivateSyncConfiguration() }

    fun save(configuration: PrivateSyncConfiguration) {
        require(configuration.validate().isEmpty()) { message(116) }
        check(preferences.edit()
            .putString(KEY_TUM_URL, cipher.encrypt(configuration.tumSubscriptionUrl.trim()))
            .putString(KEY_SOURCE_RANGE_START, cipher.encrypt(configuration.sourceRangeStartDate.trim()))
            .putString(KEY_SOURCE_RANGE_END, cipher.encrypt(configuration.sourceRangeEndDate.trim()))
            .putString(KEY_GOOGLE_CLIENT_ID, cipher.encrypt(configuration.googleOAuthClientId.trim()))
            .putString(KEY_GOOGLE_CALENDAR_NAME, cipher.encrypt(configuration.googleCalendarName.trim()))
            .putString(KEY_LOCAL_CALENDAR_ID, cipher.encrypt(configuration.localCalendarId.trim()))
            .putString(KEY_GOOGLE_CALENDAR_ID, cipher.encrypt(configuration.googleCalendarId.trim()))
            .commit()) { message(117) }
    }

    private companion object {
        const val FILE_NAME = "private_sync_configuration"
        const val KEY_TUM_URL = "tum_subscription_url"
        const val KEY_SOURCE_RANGE_START = "source_range_start"
        const val KEY_SOURCE_RANGE_END = "source_range_end"
        const val KEY_GOOGLE_CLIENT_ID = "google_oauth_client_id"
        const val KEY_GOOGLE_CALENDAR_NAME = "google_calendar_name"
        const val KEY_LOCAL_CALENDAR_ID = "local_calendar_id"
        const val KEY_GOOGLE_CALENDAR_ID = "google_calendar_id"
    }
}
