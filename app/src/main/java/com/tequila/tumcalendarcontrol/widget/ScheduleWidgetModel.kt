/*
 * Aufgabe: Inhalt der drei Startbildschirm-Widgets berechnen – klein „Wohin jetzt“ (ein Termin mit Status),
 *   mittel „Heute“ (Zeitleiste) und groß „Woche“ (Tage mit Terminen und Zählern).
 * Ein-/Ausgabe: Geschriebene Termine (aus den Zuordnungen), Einstellungen, Zeitpunkt und Zeitzone -> reine Darstellungsmodelle.
 * Nebenwirkungen: Keine; reine Berechnung ohne Speicher-, Netzwerk- oder Kalenderzugriff.
 * Nicht zuständig: Laden der Zuordnungen, RemoteViews, Texte/Übersetzung oder Aktualisierungszeitpunkte.
 */
package com.tequila.tumcalendarcontrol.widget

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.sync.StoredEventMapping
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Ein geschriebener Termin; courseId ist die Quell-UID (Ziel für „Kursdetails öffnen“). */
data class WidgetEvent(
    val key: String,
    val courseId: String,
    val title: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val state: AttendanceState,
    val location: String,
)

/** Klein: laufender Termin (minutesLeft) oder nächster (minutesUntil); null = nichts Anstehendes. */
data class WidgetFocus(val event: WidgetEvent, val inProgress: Boolean, val minutes: Long, val day: LocalDate, val today: LocalDate)

/** Mittel: heutige Termine mit Vergangen/Laufend; leer + nächster Termin morgen, wenn heute nichts (mehr) ansteht. */
data class WidgetToday(
    val date: LocalDate,
    val rows: List<WidgetTodayRow>,
    val remaining: Int,
    val tomorrowFirst: WidgetEvent?,
)
data class WidgetTodayRow(val event: WidgetEvent, val past: Boolean, val current: Boolean)

/** Groß: sieben Tage (Montag–Sonntag oder ab heute) mit Terminen; Zähler nur über die angezeigten Termine. */
data class WidgetWeek(val days: List<WidgetDay>, val today: LocalDate, val mustCount: Int, val chooseCount: Int)
data class WidgetDay(val date: LocalDate, val events: List<WidgetEvent>)

object ScheduleWidgetModel {
    /**
     * Geschriebene Termine aus den Zuordnungen: je Identität einmal (lokal und Google zählen nicht doppelt),
     * nur mit gespeichertem Inhalt. Identität bleibt UID + Originalinstanz, nie Titel.
     */
    fun eventsFrom(mappings: List<StoredEventMapping>): List<WidgetEvent> = mappings
        .mapNotNull { mapping -> mapping.writtenEvent?.let { mapping to it } }
        .distinctBy { (mapping, _) -> mapping.identity }
        .map { (mapping, event) ->
            WidgetEvent("${mapping.identity.sourceUid}|${mapping.identity.instanceKey}", mapping.identity.sourceUid, event.title, event.startsAt, event.endsAt, event.attendanceState, event.location)
        }
        .sortedWith(compareBy(WidgetEvent::startsAt, WidgetEvent::key))

    /** Einstellungen anwenden: Grau/Blau ausblenden. */
    fun visible(events: List<WidgetEvent>, settings: WidgetSettings): List<WidgetEvent> = events.filterNot {
        (settings.hideStopAttending && it.state == AttendanceState.STOP_ATTENDING) || (settings.hideChooseToAttend && it.state == AttendanceState.CHOOSE_TO_ATTEND)
    }

    fun focus(events: List<WidgetEvent>, now: Instant, zone: ZoneId): WidgetFocus? {
        val today = now.atZone(zone).toLocalDate()
        events.firstOrNull { it.startsAt <= now && now < it.endsAt }?.let {
            return WidgetFocus(it, inProgress = true, minutes = ceilMinutes(now, it.endsAt), day = today, today = today)
        }
        val next = events.firstOrNull { it.startsAt > now } ?: return null
        return WidgetFocus(next, inProgress = false, minutes = ceilMinutes(now, next.startsAt), day = next.startsAt.atZone(zone).toLocalDate(), today = today)
    }

    fun today(events: List<WidgetEvent>, now: Instant, zone: ZoneId): WidgetToday {
        val date = now.atZone(zone).toLocalDate()
        val rows = events.filter { it.startsAt.atZone(zone).toLocalDate() == date }
            .map { WidgetTodayRow(it, past = it.endsAt <= now, current = it.startsAt <= now && now < it.endsAt) }
        val remaining = rows.count { !it.past }
        val tomorrow = date.plusDays(1)
        val tomorrowFirst = if (remaining == 0) events.firstOrNull { it.startsAt.atZone(zone).toLocalDate() == tomorrow } else null
        return WidgetToday(date, rows, remaining, tomorrowFirst)
    }

    /** calendarWeek: Montag–Sonntag der aktuellen Woche; sonst sieben Tage ab heute. */
    fun week(events: List<WidgetEvent>, now: Instant, zone: ZoneId, calendarWeek: Boolean): WidgetWeek {
        val today = now.atZone(zone).toLocalDate()
        val first = if (calendarWeek) today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) else today
        val byDay = events.groupBy { it.startsAt.atZone(zone).toLocalDate() }
        val days = (0L until 7L).map { offset -> first.plusDays(offset).let { WidgetDay(it, byDay[it].orEmpty()) } }
        val shown = days.flatMap(WidgetDay::events)
        return WidgetWeek(days, today, shown.count { it.state == AttendanceState.MUST_ATTEND }, shown.count { it.state == AttendanceState.CHOOSE_TO_ATTEND })
    }

    /**
     * Nächster sinnvoller Aktualisierungszeitpunkt (ohne Genauigkeitszusage): Beginn/Ende des nächsten Termins,
     * innerhalb der letzten Stunde vor Beginn alle 5 Minuten (Countdown), sonst spätestens nach 30 Minuten; Tageswechsel zählt.
     */
    fun nextRefresh(events: List<WidgetEvent>, now: Instant, zone: ZoneId): Instant {
        val boundaries = events.flatMap { listOf(it.startsAt, it.endsAt) }.filter { it > now }
        val nextBoundary = boundaries.minOrNull()
        val midnight = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        val countdown = events.firstOrNull { it.startsAt > now && Duration.between(now, it.startsAt) <= Duration.ofHours(1) }
            ?.let { now.plus(Duration.ofMinutes(5)) }
        return listOfNotNull(nextBoundary, midnight, countdown, now.plus(Duration.ofMinutes(30))).min()
    }

    private fun ceilMinutes(from: Instant, to: Instant): Long = (Duration.between(from, to).seconds + 59) / 60
}
