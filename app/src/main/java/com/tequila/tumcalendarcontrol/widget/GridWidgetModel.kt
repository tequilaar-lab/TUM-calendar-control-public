/*
 * Aufgabe: Darstellungsmodell des Widgets „Stundenplan“: Tagesspalten der Kalenderwoche, Zeitspanne der Achse und
 *   Spuren für überlappende Termine (Konflikte sind gewollt sichtbar, nebeneinander statt verdeckt).
 * Ein-/Ausgabe: WidgetWeek (Montag–Sonntag) und Zeitzone -> GridWeek mit Minuten ab Mitternacht.
 * Nebenwirkungen: Keine; reine Berechnung.
 * Nicht zuständig: Laden der Termine, Ausblenden nach Regeln (ScheduleWidgetModel) oder Zeichnen (GridWidgetRenderer).
 */
package com.tequila.tumcalendarcontrol.widget

import java.time.LocalDate
import java.time.ZoneId

/** Terminblock in Minuten ab Mitternacht des Spaltentags (Ende höchstens 24:00). */
data class GridBlock(val event: WidgetEvent, val startMinute: Int, val endMinute: Int)

/** Tagesspalte: Spuren nebeneinander, je Spur Blöcke ohne Überlappung; hidden = Blöcke über der Spurgrenze. */
data class GridDay(val date: LocalDate, val today: Boolean, val past: Boolean, val lanes: List<List<GridBlock>>, val hidden: Int)

/** Woche des Rasters: Spalten (Mo–Fr, Wochenende nur mit Terminen) und Achse von startMinute bis endMinute. */
data class GridWeek(val days: List<GridDay>, val startMinute: Int, val endMinute: Int) {
    val hasEvents: Boolean get() = days.any { day -> day.lanes.isNotEmpty() || day.hidden > 0 }
    val hidden: Int get() = days.sumOf(GridDay::hidden)
}

object GridWidgetModel {
    /** Höchstens so viele Spuren je Tag; weitere überlappende Termine werden als „+N“ gezählt. */
    const val MAX_LANES = 3
    /** Achse ohne Termine bzw. Mindestspanne (Stunden), damit Blöcke nicht übergroß werden. */
    private const val DEFAULT_START_HOUR = 8
    private const val DEFAULT_END_HOUR = 18
    private const val MIN_SPAN_HOURS = 4

    fun layout(week: WidgetWeek, zone: ZoneId): GridWeek {
        val shown = week.days.filterIndexed { index, day -> index < 5 || day.events.isNotEmpty() }
        val blocksByDay = shown.associate { day -> day.date to day.events.map { block(it, day.date, zone) } }
        val all = blocksByDay.values.flatten()
        // Achse: auf volle Stunden gerundet, mindestens MIN_SPAN_HOURS; ohne Termine 8–18 Uhr.
        var start = all.minOfOrNull { it.startMinute }?.let { it / 60 * 60 } ?: (DEFAULT_START_HOUR * 60)
        var end = all.maxOfOrNull { it.endMinute }?.let { (it + 59) / 60 * 60 } ?: (DEFAULT_END_HOUR * 60)
        if (end - start < MIN_SPAN_HOURS * 60) end = (start + MIN_SPAN_HOURS * 60).coerceAtMost(24 * 60)
        if (end - start < MIN_SPAN_HOURS * 60) start = end - MIN_SPAN_HOURS * 60
        val days = shown.map { day ->
            val (lanes, hidden) = lanes(blocksByDay.getValue(day.date))
            GridDay(day.date, day.date == week.today, day.date < week.today, lanes, hidden)
        }
        return GridWeek(days, start, end)
    }

    /**
     * Intervallaufteilung: nach Beginn sortiert, jeder Block in die erste Spur, deren letzter Block schon geendet hat;
     * sonst neue Spur bis MAX_LANES, darüber hinaus gezählt (hidden).
     */
    internal fun lanes(blocks: List<GridBlock>): Pair<List<List<GridBlock>>, Int> {
        val lanes = mutableListOf<MutableList<GridBlock>>()
        var hidden = 0
        blocks.sortedWith(compareBy(GridBlock::startMinute, GridBlock::endMinute)).forEach { block ->
            val free = lanes.firstOrNull { it.last().endMinute <= block.startMinute }
            when {
                free != null -> free += block
                lanes.size < MAX_LANES -> lanes += mutableListOf(block)
                else -> hidden++
            }
        }
        return lanes to hidden
    }

    /** Minuten ab Mitternacht des Spaltentags; über Mitternacht reichende Termine enden in dieser Spalte um 24:00. */
    private fun block(event: WidgetEvent, date: LocalDate, zone: ZoneId): GridBlock {
        val start = event.startsAt.atZone(zone).toLocalTime().let { it.hour * 60 + it.minute }
        val endZoned = event.endsAt.atZone(zone)
        val end = if (endZoned.toLocalDate() > date) 24 * 60 else endZoned.toLocalTime().let { it.hour * 60 + it.minute }
        return GridBlock(event, start, end.coerceAtLeast(start + 1))
    }
}
