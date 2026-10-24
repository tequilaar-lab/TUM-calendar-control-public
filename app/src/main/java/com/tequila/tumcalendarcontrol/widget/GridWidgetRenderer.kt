/*
 * Aufgabe: RemoteViews des Widgets „Stundenplan“: Zeitachse links, Tagesspalten (Mo–Fr, Wochenende bei Terminen), je
 *   Spalte Spuren mit Lücken und Terminblöcken, deren Höhe der Dauer entspricht; heute hervorgehoben, Vergangenes gedämpft.
 * Ein-/Ausgabe: WidgetFrame (Termine, Einstellungen, Kurznamen, Blockgröße in dp) -> RemoteViews mit Klickzielen.
 * Nebenwirkungen: Keine; erzeugt nur PendingIntents (Startseite, Kursdetails).
 * Nicht zuständig: Spurberechnung (GridWidgetModel), Laden der Daten oder Aktualisieren der Widgets.
 */
package com.tequila.tumcalendarcontrol.widget

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.tequila.tumcalendarcontrol.R
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.ui.AppEntry
import java.time.format.DateTimeFormatter

object GridWidgetRenderer {
    private val Time = DateTimeFormatter.ofPattern("HH:mm")
    /** Feste Anteile der Höhe (dp): Innenabstand oben+unten, Kopf mit Abstand, Tagesbeschriftung (wie widget_grid*.xml). */
    private const val PADDING_DP = 20
    private const val HEADER_DP = 22
    private const val DAY_LABEL_DP = 16
    private const val AXIS_DP = 16
    private const val MIN_BLOCK_DP = 12f
    private const val PAST_ALPHA = .45f

    fun render(context: Context, frame: WidgetFrame): RemoteViews {
        val text = ScheduleWidgetRenderer.WidgetTexts(context)
        val views = RemoteViews(context.packageName, R.layout.widget_grid)
        ScheduleWidgetRenderer.resetForReapply(views, listOf(R.id.grid_axis, R.id.grid_days), emptyList())
        views.setInt(R.id.widget_root, "setBackgroundResource", ScheduleWidgetRenderer.background(context, frame.settings))
        ScheduleWidgetRenderer.setDefaultClick(views, ScheduleWidgetRenderer.openApp(context, AppEntry.Overview))
        if (!frame.hasData) return ScheduleWidgetRenderer.empty(views, text(message(797)) + "\n" + text(message(798)))
        val week = ScheduleWidgetModel.week(ScheduleWidgetModel.visible(frame.events, frame.settings), frame.now, frame.zone, calendarWeek = true)
        val grid = GridWidgetModel.layout(week, frame.zone)
        views.setTextViewText(R.id.widget_header, text(message(915, text.shortDate(week.days.first().date), text.shortDate(week.days.last().date))))
        views.setTextViewText(R.id.widget_header_side, if (grid.hidden > 0) text(message(917, grid.hidden)) else "")
        if (!grid.hasEvents) return ScheduleWidgetRenderer.empty(views, text(message(916)))
        // Höhe je Minute aus der verfügbaren Rasterhöhe; ab Android 12 Höhen in dp (Startbildschirm rechnet um).
        val gridHeight = (frame.heightDp - PADDING_DP - HEADER_DP - DAY_LABEL_DP).coerceAtLeast(40).toFloat()
        val perMinute = gridHeight / (grid.endMinute - grid.startMinute)
        val proportional = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        if (proportional && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) axis(context, views, grid, perMinute) else views.setViewVisibility(R.id.grid_axis, View.GONE)
        val columnDp = (frame.widthDp - PADDING_DP - AXIS_DP) / grid.days.size
        grid.days.forEach { day -> views.addView(R.id.grid_days, column(context, text, frame, grid, day, perMinute, columnDp, proportional)) }
        return views
    }

    /** Zeitachse: Abstand der Tagesbeschriftung, dann volle Stunden; bei wenig Platz nur jede zweite Stunde. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun axis(context: Context, views: RemoteViews, grid: GridWeek, perMinute: Float) {
        views.addView(R.id.grid_axis, gap(context, DAY_LABEL_DP.toFloat()))
        val step = if (60 * perMinute >= 12) 1 else 2
        var hour = grid.startMinute / 60
        while (hour * 60 < grid.endMinute) {
            val label = RemoteViews(context.packageName, R.layout.widget_grid_hour)
            label.setTextViewText(R.id.grid_hour, hour.toString())
            val span = (minOf(grid.endMinute, (hour + step) * 60) - hour * 60) * perMinute
            label.setViewLayoutHeight(R.id.grid_hour, span, TypedValue.COMPLEX_UNIT_DIP)
            views.addView(R.id.grid_axis, label)
            hour += step
        }
    }

    /** Tagesspalte: Beschriftung („Mi 21“, ggf. „+N“), heute hinterlegt, vergangene Tage gedämpft, Spuren nebeneinander. */
    private fun column(
        context: Context, text: ScheduleWidgetRenderer.WidgetTexts, frame: WidgetFrame, grid: GridWeek, day: GridDay,
        perMinute: Float, columnDp: Int, proportional: Boolean,
    ): RemoteViews {
        val column = RemoteViews(context.packageName, R.layout.widget_grid_day)
        val label = "${text.weekday(day.date)} ${day.date.dayOfMonth}" + if (day.hidden > 0) " +${day.hidden}" else ""
        column.setTextViewText(R.id.grid_day_label, label)
        if (day.today) column.setInt(R.id.grid_day_root, "setBackgroundResource", R.drawable.widget_today_wash)
        if (day.past) column.setFloat(R.id.grid_day_root, "setAlpha", PAST_ALPHA)
        val laneDp = columnDp / day.lanes.size.coerceAtLeast(1)
        day.lanes.forEach { lane ->
            val laneViews = RemoteViews(context.packageName, R.layout.widget_grid_lane)
            // Blöcke untereinander: Lücke bis zum Beginn, dann Block in Höhe der Dauer (mindestens MIN_BLOCK_DP).
            var cursor = 0f
            lane.forEach { block ->
                val top = (block.startMinute - grid.startMinute) * perMinute
                val height = maxOf(MIN_BLOCK_DP, (block.endMinute - block.startMinute) * perMinute)
                if (proportional && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && top - cursor > .5f) laneViews.addView(R.id.grid_lane_root, gap(context, top - cursor))
                laneViews.addView(R.id.grid_lane_root, block(context, frame, block, if (proportional) height else null, laneDp))
                cursor = maxOf(cursor, top) + height
            }
            column.addView(R.id.grid_day_lanes, laneViews)
        }
        return column
    }

    /** Terminblock: Kurzname (sonst Titel), ab 30 dp zweizeilig, ab 40 dp mit Beginn, ab 56 dp mit Raum; Klick -> Kurs. */
    private fun block(context: Context, frame: WidgetFrame, block: GridBlock, heightDp: Float?, widthDp: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_grid_block)
        val event = block.event
        val height = heightDp ?: 40f
        if (heightDp != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) views.setViewLayoutHeight(R.id.grid_block_root, heightDp, TypedValue.COMPLEX_UNIT_DIP)
        views.setInt(R.id.grid_block_root, "setBackgroundResource", background(event.state))
        views.setTextViewText(R.id.grid_block_title, ScheduleWidgetRenderer.weekTitle(event, frame))
        views.setInt(R.id.grid_block_title, "setMaxLines", if (height >= 30) 2 else 1)
        val showTime = height >= 40 && widthDp >= 34
        views.setViewVisibility(R.id.grid_block_time, if (showTime) View.VISIBLE else View.GONE)
        if (showTime) views.setTextViewText(R.id.grid_block_time, Time.format(event.startsAt.atZone(frame.zone)))
        val room = event.location.trim()
        val showRoom = height >= 56 && frame.settings.showLocation && room.isNotEmpty()
        views.setViewVisibility(R.id.grid_block_room, if (showRoom) View.VISIBLE else View.GONE)
        if (showRoom) views.setTextViewText(R.id.grid_block_room, room)
        views.setOnClickPendingIntent(R.id.grid_block_root, ScheduleWidgetRenderer.openApp(context, AppEntry.Course(event.courseId)))
        return views
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun gap(context: Context, heightDp: Float): RemoteViews = RemoteViews(context.packageName, R.layout.widget_grid_gap).apply {
        setViewLayoutHeight(R.id.grid_gap, heightDp, TypedValue.COMPLEX_UNIT_DIP)
    }

    /** Blockfläche nach sichtbarer Regel: Grün verbindlich, Blau frei gewählt, Grau ab Termin nicht mehr. */
    private fun background(state: AttendanceState): Int = when (state) {
        AttendanceState.MUST_ATTEND -> R.drawable.widget_grid_block_must
        AttendanceState.CHOOSE_TO_ATTEND -> R.drawable.widget_grid_block_choose
        AttendanceState.STOP_ATTENDING -> R.drawable.widget_grid_block_stop
    }
}
