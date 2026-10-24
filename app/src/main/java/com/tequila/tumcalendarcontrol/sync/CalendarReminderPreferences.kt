/*
 * Aufgabe: Einstellung „Erinnerung beim Schreiben“ privat speichern: nie verwaltet (null), verwaltet ohne Erinnerung
 *   (NO_REMINDER, nach dem Ausschalten) oder Minuten vor Beginn für grüne Termine.
 * Ein-/Ausgabe: Int? <-> app-private SharedPreferences „calendar_reminders“.
 * Nebenwirkungen: Nur diese privaten Präferenzen; keine Kalenderzugriffe.
 * Nicht zuständig: Schreiben der Erinnerungen (Gateways) oder Auswahl im Zustand.
 */
package com.tequila.tumcalendarcontrol.sync

import android.content.Context

/** Wählbare Vorlaufzeiten der Kalendererinnerung in Minuten. */
val CalendarReminderOptions = listOf(10, 15, 30)

class CalendarReminderPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): Int? = if (!preferences.contains(KEY_MINUTES)) null else preferences.getInt(KEY_MINUTES, NO_REMINDER).takeIf { it == NO_REMINDER || it in CalendarReminderOptions }

    fun save(minutes: Int?) {
        preferences.edit().apply { if (minutes == null) remove(KEY_MINUTES) else putInt(KEY_MINUTES, minutes) }.apply()
    }

    companion object {
        private const val FILE_NAME = "calendar_reminders"
        private const val KEY_MINUTES = "must_attend_minutes"

        /**
         * Neue Auswahl aus dem Bildschirm (null = „keine“): war schon verwaltet, bleibt es verwaltet (NO_REMINDER entfernt
         * früher geschriebene Erinnerungen beim nächsten Schreiben); nie verwaltet bleibt null (keine Massenänderung).
         */
        fun next(current: Int?, chosen: Int?): Int? = chosen ?: if (current == null) null else NO_REMINDER
    }
}
