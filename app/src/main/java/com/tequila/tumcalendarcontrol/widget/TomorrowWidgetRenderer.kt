/*
 * Aufgabe: RemoteViews des Widgets „Morgen“: schmal (unter 250 dp) erster Kurs groß mit Beginn, Raum und Anzahl; breit
 *   die Terminliste von morgen wie „Heute“. Ist morgen frei: Hinweis und der nächste spätere Kurs.
 * Ein-/Ausgabe: WidgetFrame (Termine, Einstellungen, Zeit, Blockgröße in dp) -> RemoteViews mit Klickzielen.
 * Nebenwirkungen: Keine; erzeugt nur PendingIntents (Startseite, Kursdetails, Raumkarte).
 * Nicht zuständig: Laden der Daten, Auswahl der Tage (ScheduleWidgetModel.tomorrow) oder Aktualisieren der Widgets.
 */
package com.tequila.tumcalendarcontrol.widget

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.tequila.tumcalendarcontrol.R
import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.ui.AppEntry
import java.time.format.DateTimeFormatter

object TomorrowWidgetRenderer {
    private val Time = DateTimeFormatter.ofPattern("HH:mm")
    /** Ab dieser Breite Liste wie „Heute“, darunter die kompakte Ansicht des ersten Kurses. */
    private const val WIDE_DP = 250

    fun render(context: Context, frame: WidgetFrame): RemoteViews {
        val text = ScheduleWidgetRenderer.WidgetTexts(context)
        val wide = frame.widthDp >= WIDE_DP
        val views = RemoteViews(context.packageName, if (wide) R.layout.widget_list else R.layout.widget_small)
        ScheduleWidgetRenderer.resetForReapply(views, if (wide) listOf(R.id.widget_rows) else emptyList(), if (wide) listOf(R.id.widget_footer) else emptyList())
        views.setInt(R.id.widget_root, "setBackgroundResource", ScheduleWidgetRenderer.background(context, frame.settings))
        ScheduleWidgetRenderer.setDefaultClick(views, ScheduleWidgetRenderer.openApp(context, AppEntry.Overview))
        if (!frame.hasData) return ScheduleWidgetRenderer.empty(views, text(message(797)) + "\n" + text(message(798)))
        val tomorrow = ScheduleWidgetModel.tomorrow(ScheduleWidgetModel.visible(frame.events, frame.settings), frame.now, frame.zone)
        if (wide) list(context, views, text, tomorrow, frame) else compact(context, views, text, tomorrow, frame)
        return views
    }

    /** „Nächster Kurs: Mi 10:00“ (innerhalb einer Woche Wochentag, sonst kurzes Datum). */
    private fun nextHint(text: ScheduleWidgetRenderer.WidgetTexts, tomorrow: WidgetTomorrow, frame: WidgetFrame): String? = tomorrow.nextEvent?.let { next ->
        val start = next.startsAt.atZone(frame.zone)
        val day = if (start.toLocalDate() <= tomorrow.date.plusDays(5)) text.weekday(start.toLocalDate()) else text.shortDate(start.toLocalDate())
        text(message(923, "$day ${Time.format(start)}"))
    }

    /** Schmal: Status „Morgen · N Kurse“, Beginn groß, erster Kurs, Raum; ganzes Widget -> erster Kurs. */
    private fun compact(context: Context, views: RemoteViews, text: ScheduleWidgetRenderer.WidgetTexts, tomorrow: WidgetTomorrow, frame: WidgetFrame) {
        val first = tomorrow.events.firstOrNull() ?: run {
            ScheduleWidgetRenderer.empty(views, listOfNotNull(text(message(922)), nextHint(text, tomorrow, frame)).joinToString("\n"))
            return
        }
        views.setTextViewText(R.id.widget_status, text(message(921, tomorrow.events.size)))
        views.setTextViewText(R.id.widget_time, Time.format(first.startsAt.atZone(frame.zone)))
        views.setTextViewTextSize(R.id.widget_time, TypedValue.COMPLEX_UNIT_SP, 20f)
        views.setImageViewResource(R.id.widget_dot, ScheduleWidgetRenderer.dot(first.state))
        views.setTextViewText(R.id.widget_title, first.title)
        views.setInt(R.id.widget_title, "setMaxLines", if (frame.heightDp >= 120) 2 else 1)
        val compactHeight = frame.heightDp < 80
        views.setViewVisibility(R.id.widget_time, if (compactHeight) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_dot, if (compactHeight) View.GONE else View.VISIBLE)
        val room = first.location.trim()
        val showRoom = frame.settings.showLocation && room.isNotEmpty() && frame.heightDp >= 110
        views.setViewVisibility(R.id.widget_location, if (showRoom) View.VISIBLE else View.GONE)
        if (showRoom) views.setTextViewText(R.id.widget_location, room)
        val course = ScheduleWidgetRenderer.openApp(context, AppEntry.Course(first.courseId))
        views.setOnClickPendingIntent(R.id.widget_body, course)
        ScheduleWidgetRenderer.setDefaultClick(views, course)
    }

    /** Breit: Kopf „Morgen · Datum“, Anzahl, Zeilen nach Höhe (je 38 dp), Rest bzw. „frei“ mit nächstem Kurs im Fuß. */
    private fun list(context: Context, views: RemoteViews, text: ScheduleWidgetRenderer.WidgetTexts, tomorrow: WidgetTomorrow, frame: WidgetFrame) {
        views.setTextViewText(R.id.widget_header, text(message(920, text.date(tomorrow.date))))
        views.setTextViewText(R.id.widget_header_side, text(message(801, tomorrow.events.size)))
        val capacity = ((frame.heightDp - 28 - 22 - 6) / 38).coerceAtLeast(0)
        tomorrow.events.take(capacity).forEach { views.addView(R.id.widget_rows, ScheduleWidgetRenderer.courseRow(context, frame, it)) }
        if (capacity == 0 || tomorrow.events.isEmpty()) views.setViewVisibility(R.id.widget_rows, View.GONE)
        val overflow = (tomorrow.events.size - capacity).coerceAtLeast(0)
        val footer = when {
            tomorrow.events.isEmpty() -> listOfNotNull(text(message(922)), nextHint(text, tomorrow, frame)).joinToString(" · ")
            overflow > 0 -> text(message(810, overflow))
            else -> null
        }
        views.setViewVisibility(R.id.widget_footer, if (footer == null) View.GONE else View.VISIBLE)
        footer?.let { views.setTextViewText(R.id.widget_footer, it) }
    }
}
