/*
 * Aufgabe: Ausgabezielschalter in app-privaten SharedPreferences speichern.
 * Ein-/Ausgabe: SyncDestinations -> privat wiederhergestellte Schalter, standardmäßig alle aus.
 * Nebenwirkungen: Nur private Ausgabeschalterpräferenzen.
 * Nicht zuständig: Tokens, Kontoidentitäten, Kalender-IDs, echte Synchronisierungswarteschlangen oder geräteübergreifende Konfiguration.
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
