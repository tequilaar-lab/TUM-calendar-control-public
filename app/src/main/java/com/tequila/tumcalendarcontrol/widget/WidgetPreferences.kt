/*
 * Aufgabe: Gemeinsame Einstellungen aller Startbildschirm-Widgets halten und privat speichern.
 * Ein-/Ausgabe: WidgetSettings (Kurs- und Countdown-Widgets) <-> app-private SharedPreferences „widgets“.
 * Nebenwirkungen: Nur diese privaten Präferenzen; keine Kursinhalte, Konten oder Kalenderdaten.
 * Nicht zuständig: Aktualisieren der Widgets (ScheduleWidgets.refreshAll) oder Darstellung.
 */
package com.tequila.tumcalendarcontrol.widget

import android.content.Context

/** Eckenradius in fünf Stufen (dp); die Hintergründe liegen als feste Ressourcen vor (alle Android-Versionen gleich). */
enum class WidgetCorner(val dp: Int) { SQUARE(0), SMALL(8), MEDIUM(16), LARGE(24), ROUND(32) }

data class WidgetSettings(
    /** Grau = ab einem Termin nicht mehr teilnehmen; standardmäßig ausgeblendet (Wunsch des Nutzers). */
    val hideStopAttending: Boolean = true,
    val hideChooseToAttend: Boolean = false,
    val showLocation: Boolean = true,
    val translucent: Boolean = false,
    val corner: WidgetCorner = WidgetCorner.MEDIUM,
    /** Großes Widget: Montag–Sonntag (true) oder sieben Tage ab heute. */
    val calendarWeek: Boolean = true,
    /** Countdown-Widget: Prüfungen bzw. Fristen anzeigen. */
    val countdownExams: Boolean = true,
    val countdownDeadlines: Boolean = true,
    /** Angeheftete Prüfung/Frist (ID des eigenen Termins); null = automatisch die nächste. */
    val countdownPinnedId: String? = null,
)

class WidgetPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): WidgetSettings = WidgetSettings(
        hideStopAttending = preferences.getBoolean(KEY_HIDE_GRAY, true),
        hideChooseToAttend = preferences.getBoolean(KEY_HIDE_BLUE, false),
        showLocation = preferences.getBoolean(KEY_LOCATION, true),
        translucent = preferences.getBoolean(KEY_TRANSLUCENT, false),
        corner = preferences.getString(KEY_CORNER, null)?.let { name -> WidgetCorner.entries.firstOrNull { it.name == name } } ?: WidgetCorner.MEDIUM,
        calendarWeek = preferences.getBoolean(KEY_CALENDAR_WEEK, true),
        countdownExams = preferences.getBoolean(KEY_COUNTDOWN_EXAMS, true),
        countdownDeadlines = preferences.getBoolean(KEY_COUNTDOWN_DEADLINES, true),
        countdownPinnedId = preferences.getString(KEY_COUNTDOWN_PINNED, null),
    )

    fun save(settings: WidgetSettings) {
        preferences.edit()
            .putBoolean(KEY_HIDE_GRAY, settings.hideStopAttending)
            .putBoolean(KEY_HIDE_BLUE, settings.hideChooseToAttend)
            .putBoolean(KEY_LOCATION, settings.showLocation)
            .putBoolean(KEY_TRANSLUCENT, settings.translucent)
            .putString(KEY_CORNER, settings.corner.name)
            .putBoolean(KEY_CALENDAR_WEEK, settings.calendarWeek)
            .putBoolean(KEY_COUNTDOWN_EXAMS, settings.countdownExams)
            .putBoolean(KEY_COUNTDOWN_DEADLINES, settings.countdownDeadlines)
            .putString(KEY_COUNTDOWN_PINNED, settings.countdownPinnedId)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "widgets"
        const val KEY_HIDE_GRAY = "hide_stop_attending"
        const val KEY_HIDE_BLUE = "hide_choose_to_attend"
        const val KEY_LOCATION = "show_location"
        const val KEY_TRANSLUCENT = "translucent"
        const val KEY_CORNER = "corner"
        const val KEY_CALENDAR_WEEK = "calendar_week"
        const val KEY_COUNTDOWN_EXAMS = "countdown_exams"
        const val KEY_COUNTDOWN_DEADLINES = "countdown_deadlines"
        const val KEY_COUNTDOWN_PINNED = "countdown_pinned"
    }
}
