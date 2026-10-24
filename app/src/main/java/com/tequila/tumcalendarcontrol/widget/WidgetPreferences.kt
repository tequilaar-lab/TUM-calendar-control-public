/*
 * Aufgabe: Einstellungen der Startbildschirm-Widgets halten und privat speichern: je platziertem Widget (ID) eigene
 *   Werte, dazu ein Standard für neue Widgets (die bisherigen gemeinsamen Werte werden zum Standard).
 * Ein-/Ausgabe: WidgetSettings <-> app-private SharedPreferences „widgets“ (Schlüssel ohne bzw. mit Präfix „w<ID>_“).
 * Nebenwirkungen: Nur diese privaten Präferenzen; keine Kursinhalte, Konten oder Kalenderdaten.
 * Nicht zuständig: Aktualisieren der Widgets (ScheduleWidgets.refreshAll), Größenbestimmung oder Darstellung.
 */
package com.tequila.tumcalendarcontrol.widget

import android.content.Context
import android.content.SharedPreferences

/** Manueller Eckenradius in fünf Stufen (dp); Hintergründe gibt es je ganzem dp 0–32 (auch für den Systemradius). */
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
    /** Block innerhalb der belegten Zellen: Breite/Höhe in Prozent (60–100), mittig; square = kürzere Seite für beide. */
    val blockWidthPercent: Int = 100,
    val blockHeightPercent: Int = 100,
    val square: Boolean = false,
    /**
     * Systemaussehen folgen: Ecke vom System (sonst die manuelle) und Block passend zu Systemkarten; die manuellen Werte
     * bleiben gespeichert und gelten wieder, sobald der Schalter aus ist.
     */
    val followSystem: Boolean = false,
)

/** Zulässiger Bereich der Blockgröße in Prozent der belegten Zellen. */
val BlockPercentRange = 60..100

class WidgetPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Standard für neue Widgets (bis 1.4.0 die gemeinsamen Werte aller Widgets). */
    fun load(): WidgetSettings = read("")

    fun save(settings: WidgetSettings) = write("", settings)

    /**
     * Werte eines platzierten Widgets; noch ohne eigene Werte: Standard, bei klein/Countdown als Quadrat (passt in
     * hochformatige 2×2-Felder wie auf HONOR), beim Prüf-Widget nie als Quadrat (Gerätefund: geerbtes Quadrat machte
     * das Hochziehen auf 1×2 wirkungslos). Wird beim ersten Zeichnen gespeichert, damit spätere Standardänderungen
     * vorhandene Widgets nicht verändern.
     */
    fun load(widgetId: Int, size: WidgetSize): WidgetSettings {
        if (preferences.getBoolean(prefix(widgetId) + KEY_PRESENT, false)) return read(prefix(widgetId))
        val initial = load().let {
            when (size) {
                WidgetSize.SMALL, WidgetSize.COUNTDOWN -> it.copy(square = true)
                WidgetSize.CHECK -> it.copy(square = false)
                else -> it
            }
        }
        save(widgetId, initial)
        return initial
    }

    fun save(widgetId: Int, settings: WidgetSettings) {
        write(prefix(widgetId), settings)
        preferences.edit().putBoolean(prefix(widgetId) + KEY_PRESENT, true).apply()
    }

    /** Entferntes Widget: eigene Werte löschen (nur Schlüssel mit seinem Präfix). */
    fun remove(widgetId: Int) {
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith(prefix(widgetId)) }.forEach(editor::remove)
        editor.apply()
    }

    private fun prefix(widgetId: Int) = "w${widgetId}_"

    private fun read(p: String): WidgetSettings = with(preferences) {
        WidgetSettings(
            hideStopAttending = getBoolean(p + KEY_HIDE_GRAY, true),
            hideChooseToAttend = getBoolean(p + KEY_HIDE_BLUE, false),
            showLocation = getBoolean(p + KEY_LOCATION, true),
            translucent = getBoolean(p + KEY_TRANSLUCENT, false),
            corner = getString(p + KEY_CORNER, null)?.let { name -> WidgetCorner.entries.firstOrNull { it.name == name } } ?: WidgetCorner.MEDIUM,
            calendarWeek = getBoolean(p + KEY_CALENDAR_WEEK, true),
            countdownExams = getBoolean(p + KEY_COUNTDOWN_EXAMS, true),
            countdownDeadlines = getBoolean(p + KEY_COUNTDOWN_DEADLINES, true),
            countdownPinnedId = getString(p + KEY_COUNTDOWN_PINNED, null),
            blockWidthPercent = getInt(p + KEY_BLOCK_WIDTH, 100).coerceIn(BlockPercentRange),
            blockHeightPercent = getInt(p + KEY_BLOCK_HEIGHT, 100).coerceIn(BlockPercentRange),
            square = getBoolean(p + KEY_SQUARE, false),
            followSystem = getBoolean(p + KEY_FOLLOW_SYSTEM, false),
        )
    }

    private fun write(p: String, settings: WidgetSettings) {
        val editor: SharedPreferences.Editor = preferences.edit()
            .putBoolean(p + KEY_HIDE_GRAY, settings.hideStopAttending)
            .putBoolean(p + KEY_HIDE_BLUE, settings.hideChooseToAttend)
            .putBoolean(p + KEY_LOCATION, settings.showLocation)
            .putBoolean(p + KEY_TRANSLUCENT, settings.translucent)
            .putString(p + KEY_CORNER, settings.corner.name)
            .putBoolean(p + KEY_CALENDAR_WEEK, settings.calendarWeek)
            .putBoolean(p + KEY_COUNTDOWN_EXAMS, settings.countdownExams)
            .putBoolean(p + KEY_COUNTDOWN_DEADLINES, settings.countdownDeadlines)
            .putString(p + KEY_COUNTDOWN_PINNED, settings.countdownPinnedId)
            .putInt(p + KEY_BLOCK_WIDTH, settings.blockWidthPercent.coerceIn(BlockPercentRange))
            .putInt(p + KEY_BLOCK_HEIGHT, settings.blockHeightPercent.coerceIn(BlockPercentRange))
            .putBoolean(p + KEY_SQUARE, settings.square)
            .putBoolean(p + KEY_FOLLOW_SYSTEM, settings.followSystem)
        editor.apply()
    }

    private companion object {
        const val FILE_NAME = "widgets"
        const val KEY_PRESENT = "present"
        const val KEY_HIDE_GRAY = "hide_stop_attending"
        const val KEY_HIDE_BLUE = "hide_choose_to_attend"
        const val KEY_LOCATION = "show_location"
        const val KEY_TRANSLUCENT = "translucent"
        const val KEY_CORNER = "corner"
        const val KEY_CALENDAR_WEEK = "calendar_week"
        const val KEY_COUNTDOWN_EXAMS = "countdown_exams"
        const val KEY_COUNTDOWN_DEADLINES = "countdown_deadlines"
        const val KEY_COUNTDOWN_PINNED = "countdown_pinned"
        const val KEY_BLOCK_WIDTH = "block_width"
        const val KEY_BLOCK_HEIGHT = "block_height"
        const val KEY_SQUARE = "square"
        const val KEY_FOLLOW_SYSTEM = "follow_system"
    }
}
