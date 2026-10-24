/*
 * Aufgabe: Widget-Inhalte prüfen: Doppelte je Identität, Grau/Blau ausblenden, „Wohin jetzt“ (laufend/als Nächstes),
 *   „Heute“ (vergangen/laufend, morgen zuerst), „Woche“ (Kalenderwoche oder ab heute, Zähler) und Aktualisierungszeitpunkt.
 * Ein-/Ausgabe: Synthetische Zuordnungen/Termine und feste Zeitpunkte -> erwartete Modelle.
 * Nebenwirkungen: Keine.
 * Nicht zuständig: RemoteViews (ScheduleWidgetRendererTest).
 */
package com.tequila.tumcalendarcontrol.widget

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.sync.DesiredCalendarEvent
import com.tequila.tumcalendarcontrol.sync.ManagedEventIdentity
import com.tequila.tumcalendarcontrol.sync.OutputTarget
import com.tequila.tumcalendarcontrol.sync.StoredEventMapping
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleWidgetModelTest {
    private val zone = ZoneId.of("Europe/Berlin")
    // Mittwoch, 21.10.2026, 10:30 Ortszeit.
    private val now = Instant.parse("2026-10-21T08:30:00Z")
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDate.of(2026, 10, day).atTime(hour, minute).atZone(zone).toInstant()
    private fun ev(key: String, day: Int, hour: Int, state: AttendanceState = AttendanceState.MUST_ATTEND, minutes: Long = 90, minute: Int = 0) =
        WidgetEvent(key, "uid-$key", "Synthetik $key", at(day, hour, minute), at(day, hour, minute).plusSeconds(minutes * 60), state, "Raum $key")

    @Test fun `Zuordnungen je Identität einmal und nur mit Inhalt`() {
        val identity = ManagedEventIdentity("uid-a", "i1")
        val written = DesiredCalendarEvent(identity, "Synthetik", at(21, 10), at(21, 11), AttendanceState.MUST_ATTEND, "Raum")
        val mappings = listOf(
            StoredEventMapping(OutputTarget.LOCAL_CALENDAR, identity, "l1", "f", writtenEvent = written),
            StoredEventMapping(OutputTarget.GOOGLE_CALENDAR, identity, "g1", "f", writtenEvent = written),
            StoredEventMapping(OutputTarget.LOCAL_CALENDAR, ManagedEventIdentity("uid-b", "i1"), "l2", "f", writtenEvent = null),
        )
        val events = ScheduleWidgetModel.eventsFrom(mappings)
        assertEquals(1, events.size)
        assertEquals("uid-a", events.single().courseId)
    }

    @Test fun `Grau standardmäßig ausgeblendet, Blau optional`() {
        val events = listOf(ev("a", 21, 12), ev("b", 21, 14, AttendanceState.CHOOSE_TO_ATTEND), ev("c", 21, 16, AttendanceState.STOP_ATTENDING))
        assertEquals(listOf("a", "b"), ScheduleWidgetModel.visible(events, WidgetSettings()).map { it.key })
        assertEquals(listOf("a"), ScheduleWidgetModel.visible(events, WidgetSettings(hideChooseToAttend = true)).map { it.key })
        assertEquals(3, ScheduleWidgetModel.visible(events, WidgetSettings(hideStopAttending = false)).size)
    }

    @Test fun `Wohin jetzt laufend, als Nächstes, nichts`() {
        val running = ScheduleWidgetModel.focus(listOf(ev("a", 21, 10), ev("b", 21, 14)), now, zone)!!
        assertTrue(running.inProgress)
        assertEquals(60, running.minutes)
        val next = ScheduleWidgetModel.focus(listOf(ev("b", 21, 11, minute = 5)), now, zone)!!
        assertFalse(next.inProgress)
        assertEquals(35, next.minutes)
        val tomorrow = ScheduleWidgetModel.focus(listOf(ev("c", 22, 9)), now, zone)!!
        assertEquals(LocalDate.of(2026, 10, 22), tomorrow.day)
        assertNull(ScheduleWidgetModel.focus(listOf(ev("old", 20, 9)), now, zone))
    }

    @Test fun `Heute mit vergangen, laufend und morgen zuerst`() {
        val today = ScheduleWidgetModel.today(listOf(ev("a", 21, 8), ev("b", 21, 10), ev("c", 21, 14), ev("d", 22, 9)), now, zone)
        assertEquals(listOf(true, false, false), today.rows.map { it.past })
        assertEquals(listOf(false, true, false), today.rows.map { it.current })
        assertEquals(2, today.remaining)
        assertNull(today.tomorrowFirst)
        val over = ScheduleWidgetModel.today(listOf(ev("a", 21, 7), ev("d", 22, 9), ev("e", 22, 12)), now, zone)
        assertEquals(0, over.remaining)
        assertEquals("d", over.tomorrowFirst!!.key)
    }

    @Test fun `Woche Montag bis Sonntag oder ab heute mit Zählern`() {
        val events = listOf(ev("mo", 19, 10), ev("mi", 21, 14, AttendanceState.CHOOSE_TO_ATTEND), ev("so", 25, 10), ev("next", 26, 10))
        val calendar = ScheduleWidgetModel.week(events, now, zone, calendarWeek = true)
        assertEquals(LocalDate.of(2026, 10, 19), calendar.days.first().date)
        assertEquals(LocalDate.of(2026, 10, 25), calendar.days.last().date)
        assertEquals(2, calendar.mustCount)
        assertEquals(1, calendar.chooseCount)
        val rolling = ScheduleWidgetModel.week(events, now, zone, calendarWeek = false)
        assertEquals(LocalDate.of(2026, 10, 21), rolling.days.first().date)
        assertEquals(listOf("mi", "so", "next"), rolling.days.flatMap { it.events }.map { it.key })
    }

    @Test fun `Aktualisierung am nächsten Rand, im Countdown alle 5 Minuten`() {
        assertEquals(now.plusSeconds(1_800), ScheduleWidgetModel.nextRefresh(emptyList(), now, zone))
        // Laufender Termin endet 10:45: dieser Rand kommt vor der 30-Minuten-Grenze.
        assertEquals(at(21, 10, 45), ScheduleWidgetModel.nextRefresh(listOf(ev("x", 21, 10, minutes = 30, minute = 15)), now, zone))
        // Beginn in 50 Minuten: Countdown alle 5 Minuten.
        assertEquals(now.plusSeconds(300), ScheduleWidgetModel.nextRefresh(listOf(ev("y", 21, 11, minute = 20)), now, zone))
        // Beginn in 2,5 Stunden: spätestens nach 30 Minuten.
        assertEquals(now.plusSeconds(1_800), ScheduleWidgetModel.nextRefresh(listOf(ev("z", 21, 13)), now, zone))
    }
}
