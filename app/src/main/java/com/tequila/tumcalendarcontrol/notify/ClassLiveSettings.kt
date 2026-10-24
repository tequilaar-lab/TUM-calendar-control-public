/*
 * Aufgabe: Einstellungen der Live-Kursbenachrichtigung halten und privat speichern (Schalter, Vorlauf, auch blaue Kurse).
 * Ein-/Ausgabe: ClassLiveSettings <-> app-private SharedPreferences „class_live“.
 * Nebenwirkungen: Nur diese privaten Präferenzen; keine Kursinhalte, Konten oder Kalenderdaten.
 * Nicht zuständig: Planung (ClassLivePlanner), Benachrichtigung (ClassLiveNotifier) oder Bedienoberfläche.
 */
package com.tequila.tumcalendarcontrol.notify

import android.content.Context

/** Standardmäßig aus; Vorlauf in Minuten aus LeadOptions; grüne Kurse immer, blaue nur mit includeChooseToAttend. */
data class ClassLiveSettings(
    val enabled: Boolean = false,
    val leadMinutes: Int = 15,
    val includeChooseToAttend: Boolean = false,
)

/** Wählbare Vorlaufzeiten in Minuten. */
val ClassLiveLeadOptions = listOf(10, 15, 30)

class ClassLiveSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): ClassLiveSettings = ClassLiveSettings(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        leadMinutes = preferences.getInt(KEY_LEAD, 15).takeIf { it in ClassLiveLeadOptions } ?: 15,
        includeChooseToAttend = preferences.getBoolean(KEY_CHOOSE, false),
    )

    fun save(settings: ClassLiveSettings) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putInt(KEY_LEAD, settings.leadMinutes.takeIf { it in ClassLiveLeadOptions } ?: 15)
            .putBoolean(KEY_CHOOSE, settings.includeChooseToAttend)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "class_live"
        const val KEY_ENABLED = "enabled"
        const val KEY_LEAD = "lead_minutes"
        const val KEY_CHOOSE = "include_choose"
    }
}
