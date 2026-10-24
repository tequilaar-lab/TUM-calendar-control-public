/*
 * Aufgabe: RemoteViews des Countdown-Widgets: Hauptziel groß (Art, Zahl mit Einheit, Name, Zeitpunkt), breit daneben
 *   die nächsten zwei, hoch darunter eine Liste mit Rest-Hinweis; flach nur das Nötigste.
 * Ein-/Ausgabe: Eigene Termine, Einstellungen (Arten, Anheftung, Grau), Zeit und Größe -> RemoteViews.
 * Nebenwirkungen: Keine; erzeugt nur PendingIntents zu den Campusterminen.
 * Nicht zuständig: Countdown-Berechnung (DeadlineCountdown), Laden der Daten oder Aktualisierungszeitpunkte.
 */
package com.tequila.tumcalendarcontrol.widget

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.tequila.tumcalendarcontrol.R
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.sync.CountdownEntry
import com.tequila.tumcalendarcontrol.sync.CountdownRemaining
import com.tequila.tumcalendarcontrol.sync.DeadlineCountdown
import com.tequila.tumcalendarcontrol.sync.ManualEventKind
import com.tequila.tumcalendarcontrol.ui.AppEntry
import com.tequila.tumcalendarcontrol.ui.label
import com.tequila.tumcalendarcontrol.ui.text
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CountdownWidgetRenderer {
    private val Time = DateTimeFormatter.ofPattern("HH:mm")

    /** Sichtbare Einträge nach Einstellungen: Arten an/aus, Grau ausblenden wie bei den Kurs-Widgets. */
    fun entries(frame: WidgetFrame): List<CountdownEntry> {
        val kinds = buildSet {
            if (frame.settings.countdownExams) add(ManualEventKind.EXAM)
            if (frame.settings.countdownDeadlines) add(ManualEventKind.DEADLINE)
        }
        val items = frame.manualEvents.filterNot { frame.settings.hideStopAttending && it.attendanceState == AttendanceState.STOP_ATTENDING }
        return DeadlineCountdown.upcoming(items, frame.now, frame.zone, kinds)
    }

    fun render(context: Context, frame: WidgetFrame): RemoteViews {
        val text = ScheduleWidgetRenderer.WidgetTexts(context)
        val views = RemoteViews(context.packageName, R.layout.widget_countdown)
        // Erneutes Anwenden auf die vorhandene Ansicht: Listen leeren, Zusatzbereiche zunächst ausblenden.
        ScheduleWidgetRenderer.resetForReapply(views, listOf(R.id.cd_side, R.id.cd_list), listOf(R.id.cd_side, R.id.cd_list, R.id.widget_footer))
        views.setInt(R.id.widget_root, "setBackgroundResource", ScheduleWidgetRenderer.background(frame.settings))
        views.setOnClickPendingIntent(R.id.widget_root, ScheduleWidgetRenderer.openApp(context, AppEntry.ManualEvents()))
        val entries = entries(frame)
        val main = DeadlineCountdown.featured(entries, frame.settings.countdownPinnedId)
        if (main == null) {
            // Leer: Antippen öffnet die Campustermine mit vorgewählter Art „Prüfung“.
            views.setOnClickPendingIntent(R.id.widget_root, ScheduleWidgetRenderer.openApp(context, AppEntry.ManualEvents(ManualEventKind.EXAM)))
            return ScheduleWidgetRenderer.empty(views, text(message(854)) + "\n" + text(message(855)))
        }
        val flat = frame.heightDp < 90
        views.setTextViewText(R.id.cd_kind, text(main.item.kind.label()))
        val (number, unit) = numberAndUnit(main.remaining, text)
        views.setTextViewText(R.id.cd_number, number)
        views.setTextViewTextSize(R.id.cd_number, TypedValue.COMPLEX_UNIT_SP, if (main.remaining == CountdownRemaining.InProgress) 24f else if (flat) 30f else 44f)
        views.setTextViewText(R.id.cd_unit, unit)
        views.setViewVisibility(R.id.cd_unit, if (unit.isEmpty()) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.cd_title, main.item.title)
        views.setInt(R.id.cd_title, "setMaxLines", if (flat) 1 else 2)
        views.setTextViewText(R.id.cd_when, whenText(main, frame.zone, frame, text))
        views.setViewVisibility(R.id.cd_when, if (flat) View.GONE else View.VISIBLE)

        val others = entries.filterNot { it === main }
        val wide = frame.widthDp >= 250
        val tall = frame.heightDp >= 180
        when {
            // Hoch: Liste unter dem Hauptziel (je 34 dp), Rest als Hinweis.
            tall -> {
                val capacity = ((frame.heightDp - 28 - 130 - 10) / 34).coerceAtLeast(0)
                others.take(capacity).forEach { views.addView(R.id.cd_list, row(context, it, frame, text)) }
                views.setViewVisibility(R.id.cd_list, if (others.isEmpty() || capacity == 0) View.GONE else View.VISIBLE)
                val rest = others.size - capacity
                views.setViewVisibility(R.id.widget_footer, if (rest > 0) View.VISIBLE else View.GONE)
                if (rest > 0) views.setTextViewText(R.id.widget_footer, text(message(853, rest)))
            }
            // Breit und flach: die nächsten zwei rechts neben dem Hauptziel.
            wide && others.isNotEmpty() -> {
                others.take(2).forEach { views.addView(R.id.cd_side, row(context, it, frame, text, compact = true)) }
                views.setViewVisibility(R.id.cd_side, View.VISIBLE)
            }
        }
        return views
    }

    /** compact (schmale Spalte neben dem Hauptziel): nur Art und kurzes Datum, ohne Wochentag und Uhrzeit. */
    private fun row(context: Context, entry: CountdownEntry, frame: WidgetFrame, text: ScheduleWidgetRenderer.WidgetTexts, compact: Boolean = false): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_countdown_row)
        row.setTextViewText(R.id.cdrow_title, entry.item.title)
        val time = if (compact) text.shortDate(entry.target.atZone(frame.zone).toLocalDate()) else whenText(entry, frame.zone, frame, text)
        row.setTextViewText(R.id.cdrow_detail, text(message(147, entry.item.kind.label(), time)))
        val (number, unit) = numberAndUnit(entry.remaining, text)
        row.setTextViewText(R.id.cdrow_left, if (unit.isEmpty()) number else "$number $unit")
        row.setOnClickPendingIntent(R.id.cdrow_root, ScheduleWidgetRenderer.openApp(context, AppEntry.ManualEvents()))
        return row
    }

    /** Große Zahl und Einheit; laufende Prüfung ohne Zahl („Läuft“). */
    private fun numberAndUnit(remaining: CountdownRemaining, text: ScheduleWidgetRenderer.WidgetTexts): Pair<String, String> = when (remaining) {
        is CountdownRemaining.Days -> remaining.days.toString() to text(message(850))
        is CountdownRemaining.Hours -> remaining.hours.toString() to text(message(851))
        is CountdownRemaining.Minutes -> remaining.minutes.toString() to text(message(852))
        CountdownRemaining.InProgress -> text(remaining.text()) to ""
    }

    /** Zeitpunkt: „Heute 09:00“, „Morgen 09:00“, „Mo 09:00“ oder kurzes Datum mit Uhrzeit. */
    private fun whenText(entry: CountdownEntry, zone: ZoneId, frame: WidgetFrame, text: ScheduleWidgetRenderer.WidgetTexts): String {
        val target = entry.target.atZone(zone)
        val today = frame.now.atZone(zone).toLocalDate()
        val time = Time.format(target)
        return text(
            when (target.toLocalDate()) {
                today -> message(793, time)
                today.plusDays(1) -> message(794, time)
                // Kurz, damit es in schmale/quadratische 2×2-Blöcke passt: Wochentag in der nächsten Woche, sonst kurzes Datum.
                in today.plusDays(2)..today.plusDays(6) -> message(765, text.weekday(target.toLocalDate()), time)
                else -> message(765, text.shortDate(target.toLocalDate()), time)
            },
        )
    }
}
