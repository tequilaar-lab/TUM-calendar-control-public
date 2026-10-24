/*
 * Aufgabe: Kurznamen der Kurse für das Widget „Woche“ halten (Wunsch: lange Titel endeten dort mit Auslassungspunkten).
 * Ein-/Ausgabe: Quell-UID <-> Kurzname in app-privaten SharedPreferences „widget_short_names“.
 * Nebenwirkungen: Nur diese privaten Präferenzen; bewusst getrennt von lokalen Anzeigeänderungen der Kurse, damit
 *   Kurznamen nie in Schreibvorschau, Kalenderausgabe, Quelldaten oder Identitäten gelangen.
 * Nicht zuständig: Darstellung (ScheduleWidgetRenderer), Bedienoberfläche oder Aktualisieren der Widgets.
 */
package com.tequila.tumcalendarcontrol.widget

import android.content.Context

class CourseShortNames(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Alle Kurznamen je Quell-UID (gleiche UID = alle Termine dieser Kursreihe). */
    fun load(): Map<String, String> = preferences.all.mapNotNull { (uid, value) -> (value as? String)?.let { uid to it } }.toMap()

    fun get(sourceUid: String): String = preferences.getString(sourceUid, null).orEmpty()

    /** Speichert den bereinigten Kurznamen; leer entfernt ihn (Widget zeigt dann wieder den vollen Titel). */
    fun set(sourceUid: String, shortName: String) {
        if (sourceUid.isBlank()) return
        val cleaned = clean(shortName)
        preferences.edit().apply { if (cleaned.isEmpty()) remove(sourceUid) else putString(sourceUid, cleaned) }.apply()
    }

    companion object {
        /** Höchstlänge eines Kurznamens (Zeichen); mehr passt in schmale Wochenblöcke ohnehin nicht. */
        const val MAX_LENGTH = 16
        private const val FILE_NAME = "widget_short_names"

        /** Ränder und Zeilenumbrüche entfernen, auf MAX_LENGTH Zeichen kürzen. */
        fun clean(shortName: String): String = shortName.replace('\n', ' ').trim().take(MAX_LENGTH).trim()
    }
}
