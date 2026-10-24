/*
 * Aufgabe: RemoteViews der drei Widget-Größen aus den Darstellungsmodellen bauen; Inhalt passt sich Höhe/Breite an
 *   (kompakte Einzeile bis Liste mit Details), Hintergrund nach Deckkraft und Eckenstufe.
 * Ein-/Ausgabe: Termine, Einstellungen, Zeit, Größe in dp, Sprache -> RemoteViews mit Klickzielen.
 * Nebenwirkungen: Keine; erzeugt nur PendingIntents (App öffnen, Kursdetails, Raumkarte im Browser).
 * Nicht zuständig: Laden der Daten, Auslösen von Aktualisierungen oder Speichern der Einstellungen.
 */
package com.tequila.tumcalendarcontrol.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.tequila.tumcalendarcontrol.R
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.domain.RoomLink
import com.tequila.tumcalendarcontrol.i18n.AppLanguagePreferences
import com.tequila.tumcalendarcontrol.i18n.localizedAppContext
import com.tequila.tumcalendarcontrol.i18n.localizedMessage
import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.ui.AppEntry
import com.tequila.tumcalendarcontrol.ui.AppEntryIntents
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class WidgetSize { SMALL, MEDIUM, LARGE }

/** Eingaben eines Zeichendurchgangs; hasData = überhaupt geschriebene Termine (sonst Hinweis „noch nichts geschrieben“). */
data class WidgetFrame(
    val events: List<WidgetEvent>,
    val hasData: Boolean,
    val settings: WidgetSettings,
    val now: Instant,
    val zone: ZoneId,
    val widthDp: Int,
    val heightDp: Int,
)

object ScheduleWidgetRenderer {
    private val Time = DateTimeFormatter.ofPattern("HH:mm")
    private const val PAST_ALPHA = .45f

    fun render(context: Context, size: WidgetSize, frame: WidgetFrame): RemoteViews {
        val text = Texts(context)
        val layout = when (size) { WidgetSize.SMALL -> R.layout.widget_small; WidgetSize.MEDIUM -> R.layout.widget_list; WidgetSize.LARGE -> R.layout.widget_week }
        val views = RemoteViews(context.packageName, layout)
        views.setInt(R.id.widget_root, "setBackgroundResource", background(frame.settings))
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, null))
        val visible = ScheduleWidgetModel.visible(frame.events, frame.settings)
        if (!frame.hasData) return empty(views, text(message(797)) + "\n" + text(message(798)))
        when (size) {
            WidgetSize.SMALL -> small(context, views, text, visible, frame)
            WidgetSize.MEDIUM -> medium(context, views, text, visible, frame)
            WidgetSize.LARGE -> large(context, views, text, visible, frame)
        }
        return views
    }

    /** Klein: ein Termin. Unter 80 dp nur Status und Kurs; ab 130 dp Kurs zweizeilig; Raum ab 110 dp (antippbar). */
    private fun small(context: Context, views: RemoteViews, text: Texts, events: List<WidgetEvent>, frame: WidgetFrame) {
        val focus = ScheduleWidgetModel.focus(events, frame.now, frame.zone) ?: run { empty(views, text(message(799))); return }
        val event = focus.event
        val start = event.startsAt.atZone(frame.zone)
        val status = when {
            focus.inProgress -> message(791, focus.minutes)
            focus.day == focus.today && focus.minutes <= 60 -> message(792, focus.minutes)
            focus.day == focus.today -> message(793, Time.format(start))
            focus.day == focus.today.plusDays(1) -> message(794, Time.format(start))
            else -> message(765, text.date(focus.day), Time.format(start))
        }
        views.setTextViewText(R.id.widget_status, text(status))
        views.setTextViewText(R.id.widget_time, timeRange(event, frame.zone))
        views.setImageViewResource(R.id.widget_dot, dot(event.state))
        views.setTextViewText(R.id.widget_title, event.title)
        views.setInt(R.id.widget_title, "setMaxLines", if (frame.heightDp >= 130) 2 else 1)
        val compact = frame.heightDp < 80
        views.setViewVisibility(R.id.widget_time, if (compact) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_dot, if (compact) View.GONE else View.VISIBLE)
        val room = event.location.trim()
        val showRoom = frame.settings.showLocation && room.isNotEmpty() && frame.heightDp >= 110
        views.setViewVisibility(R.id.widget_location, if (showRoom) View.VISIBLE else View.GONE)
        if (showRoom) {
            views.setTextViewText(R.id.widget_location, room)
            RoomLink.url(room)?.let { views.setOnClickPendingIntent(R.id.widget_location, openUrl(context, it)) }
        }
        views.setOnClickPendingIntent(R.id.widget_body, openApp(context, AppEntry.Course(event.courseId)))
    }

    /** Mittel: heutige Zeitleiste; Zeilen nach Höhe (je 38 dp), vergangene zuerst weglassen; einzeilig nur Kopf mit Nächstem. */
    private fun medium(context: Context, views: RemoteViews, text: Texts, events: List<WidgetEvent>, frame: WidgetFrame) {
        val today = ScheduleWidgetModel.today(events, frame.now, frame.zone)
        views.setTextViewText(R.id.widget_header, text(message(800, text.date(today.date))))
        val nextToday = today.rows.firstOrNull { !it.past }?.event
        val capacity = ((frame.heightDp - 28 - 22 - 6) / 38).coerceAtLeast(0)
        if (capacity == 0) {
            views.setTextViewText(R.id.widget_header_side, nextToday?.let { "${Time.format(it.startsAt.atZone(frame.zone))} ${it.title}" } ?: text(message(801, today.remaining)))
            views.setViewVisibility(R.id.widget_rows, View.GONE)
            return
        }
        views.setTextViewText(R.id.widget_header_side, text(message(801, today.rows.size)))
        var rows = today.rows
        while (rows.size > capacity && rows.first().past) rows = rows.drop(1)
        val overflow = (rows.size - capacity).coerceAtLeast(0)
        rows.take(capacity).forEach { row ->
            val item = RemoteViews(context.packageName, R.layout.widget_row)
            val start = row.event.startsAt.atZone(frame.zone)
            item.setTextViewText(R.id.row_time, if (frame.widthDp >= 250) timeRange(row.event, frame.zone) else Time.format(start))
            item.setImageViewResource(R.id.row_dot, dot(row.event.state))
            item.setTextViewText(R.id.row_title, row.event.title)
            val room = row.event.location.trim()
            val showRoom = frame.settings.showLocation && room.isNotEmpty()
            item.setViewVisibility(R.id.row_detail, if (showRoom) View.VISIBLE else View.GONE)
            if (showRoom) item.setTextViewText(R.id.row_detail, room)
            item.setViewVisibility(R.id.row_now, if (row.current) View.VISIBLE else View.INVISIBLE)
            if (row.past) item.setFloat(R.id.row_root, "setAlpha", PAST_ALPHA)
            item.setOnClickPendingIntent(R.id.row_root, openApp(context, AppEntry.Course(row.event.courseId)))
            views.addView(R.id.widget_rows, item)
        }
        // Fußzeile: Rest, „heute nichts/vorbei“ und ggf. der erste Termin morgen.
        val footer = when {
            overflow > 0 -> text(message(810, overflow))
            today.rows.isEmpty() -> listOfNotNull(text(message(802)), today.tomorrowFirst?.let { text(message(804, Time.format(it.startsAt.atZone(frame.zone)), it.title)) }).joinToString(" · ")
            today.remaining == 0 -> listOfNotNull(text(message(803)), today.tomorrowFirst?.let { text(message(804, Time.format(it.startsAt.atZone(frame.zone)), it.title)) }).joinToString(" · ")
            else -> null
        }
        views.setViewVisibility(R.id.widget_footer, if (footer == null) View.GONE else View.VISIBLE)
        footer?.let { views.setTextViewText(R.id.widget_footer, it) }
    }

    /** Groß: sieben Tageszeilen mit Terminblöcken; ab 44 dp je Tag mit Raumzeile; unter 120 dp nur Kopf und Zähler. */
    private fun large(context: Context, views: RemoteViews, text: Texts, events: List<WidgetEvent>, frame: WidgetFrame) {
        val week = ScheduleWidgetModel.week(events, frame.now, frame.zone, frame.settings.calendarWeek)
        views.setTextViewText(R.id.widget_header, text(if (frame.settings.calendarWeek) message(805, text.shortDate(week.days.first().date), text.shortDate(week.days.last().date)) else message(806)))
        views.setTextViewText(R.id.widget_header_side, "")
        views.setViewVisibility(R.id.widget_footer, View.VISIBLE)
        views.setTextViewText(R.id.widget_footer, text(message(807, week.mustCount, week.chooseCount)))
        if (frame.heightDp < 120) { views.setViewVisibility(R.id.widget_rows, View.GONE); return }
        val perDay = (frame.heightDp - 28 - 22 - 6 - 20) / 7
        val tall = perDay >= 44
        val maxChips = ((frame.widthDp - 28 - 48) / (if (tall) 84 else 64)).coerceAtLeast(1)
        week.days.forEach { day ->
            val row = RemoteViews(context.packageName, R.layout.widget_day)
            row.setTextViewText(R.id.day_label, text.dayLabel(day.date))
            if (day.date == week.today) row.setInt(R.id.day_root, "setBackgroundResource", R.drawable.widget_today_wash)
            if (day.date < week.today) row.setFloat(R.id.day_root, "setAlpha", PAST_ALPHA)
            day.events.take(maxChips).forEach { event ->
                val chip = RemoteViews(context.packageName, R.layout.widget_chip)
                chip.setImageViewResource(R.id.chip_dot, dot(event.state))
                chip.setTextViewText(R.id.chip_text, "${Time.format(event.startsAt.atZone(frame.zone))} ${event.title}")
                val room = event.location.trim()
                val showRoom = tall && frame.settings.showLocation && room.isNotEmpty()
                chip.setViewVisibility(R.id.chip_detail, if (showRoom) View.VISIBLE else View.GONE)
                if (showRoom) chip.setTextViewText(R.id.chip_detail, room)
                chip.setOnClickPendingIntent(R.id.chip_root, openApp(context, AppEntry.Course(event.courseId)))
                row.addView(R.id.day_chips, chip)
            }
            val more = day.events.size - maxChips
            row.setViewVisibility(R.id.day_more, if (more > 0) View.VISIBLE else View.GONE)
            if (more > 0) row.setTextViewText(R.id.day_more, "+$more")
            views.addView(R.id.widget_rows, row)
        }
    }

    private fun empty(views: RemoteViews, note: String): RemoteViews {
        views.setViewVisibility(R.id.widget_body, View.GONE)
        views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
        views.setTextViewText(R.id.widget_empty, note)
        return views
    }

    private fun timeRange(event: WidgetEvent, zone: ZoneId) = "${Time.format(event.startsAt.atZone(zone))}–${Time.format(event.endsAt.atZone(zone))}"

    /** Sichtbare Regeln: Grün verbindlich, Blau frei gewählt, Grau ab Termin nicht mehr. */
    private fun dot(state: AttendanceState): Int = when (state) {
        AttendanceState.MUST_ATTEND -> R.drawable.widget_dot_must
        AttendanceState.CHOOSE_TO_ATTEND -> R.drawable.widget_dot_choose
        AttendanceState.STOP_ATTENDING -> R.drawable.widget_dot_stop
    }

    internal fun background(settings: WidgetSettings): Int = if (settings.translucent) {
        when (settings.corner) {
            WidgetCorner.SQUARE -> R.drawable.widget_bg_glass_r0; WidgetCorner.SMALL -> R.drawable.widget_bg_glass_r8
            WidgetCorner.MEDIUM -> R.drawable.widget_bg_glass_r16; WidgetCorner.LARGE -> R.drawable.widget_bg_glass_r24
            WidgetCorner.ROUND -> R.drawable.widget_bg_glass_r32
        }
    } else {
        when (settings.corner) {
            WidgetCorner.SQUARE -> R.drawable.widget_bg_solid_r0; WidgetCorner.SMALL -> R.drawable.widget_bg_solid_r8
            WidgetCorner.MEDIUM -> R.drawable.widget_bg_solid_r16; WidgetCorner.LARGE -> R.drawable.widget_bg_solid_r24
            WidgetCorner.ROUND -> R.drawable.widget_bg_solid_r32
        }
    }

    /** App öffnen (ohne Ziel) bzw. Kursdetails; Daten-URI macht je Kurs eine eigene PendingIntent. */
    private fun openApp(context: Context, entry: AppEntry?): PendingIntent {
        val intent = AppEntryIntents.intent(context, entry).apply {
            data = Uri.fromParts("tumwidget", (entry as? AppEntry.Course)?.courseId ?: "app", null)
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun openUrl(context: Context, url: String): PendingIntent =
        PendingIntent.getActivity(context, 1, Intent(Intent.ACTION_VIEW, Uri.parse(url)), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Texte in der App-Sprache (nicht Systemsprache), Datumsformate aus den Sprachressourcen. */
    private class Texts(context: Context) {
        private val language = AppLanguagePreferences(context).load()
        private val localized = localizedAppContext(context, language)
        private val locale = Locale.forLanguageTag(language.tag)
        operator fun invoke(value: String): String = localizedMessage(localized, value)
        fun date(date: LocalDate): String = DateTimeFormatter.ofPattern(this(message(795)), locale).format(date)
        fun shortDate(date: LocalDate): String = DateTimeFormatter.ofPattern(this(message(809)), locale).format(date)
        fun dayLabel(date: LocalDate): String =
            DateTimeFormatter.ofPattern(this(message(796)), locale).format(date) + "\n" + DateTimeFormatter.ofPattern(this(message(808)), locale).format(date)
    }
}
