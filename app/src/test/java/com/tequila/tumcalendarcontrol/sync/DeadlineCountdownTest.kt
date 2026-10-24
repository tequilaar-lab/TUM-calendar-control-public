/*
 * Aufgabe: Countdown prüfen: Zielzeit je Art, laufende Prüfung zuerst, Vergangenes entfällt, Horizont, Kalendertage
 *   ab 48 Stunden, Stunden/Minuten aufgerundet, angeheftetes Hauptziel mit Rückfall.
 * Ein-/Ausgabe: Synthetische Termine und feste Zeitpunkte -> erwartete Einträge.
 * Nebenwirkungen: Keine.
 * Nicht zuständig: Texte und Darstellung.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeadlineCountdownTest {
    private val zone = ZoneId.of("Europe/Berlin")
    // Mittwoch, 21.10.2026, 22:00 Ortszeit.
    private val now = LocalDateTime.of(2026, 10, 21, 22, 0).atZone(zone).toInstant()
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant()
    private fun item(id: String, kind: ManualEventKind, start: Instant, end: Instant = start.plusSeconds(7_200)) =
        ManualScheduleItem(id, "Synthetik $id", start, end, AttendanceState.MUST_ATTEND, kind)

    @Test fun `Ziel je Art, normale Termine ohne Countdown`() {
        val exam = item("e", ManualEventKind.EXAM, at(25, 9))
        val deadline = item("d", ManualEventKind.DEADLINE, at(24, 23, 29), at(24, 23, 59))
        assertEquals(at(25, 9), DeadlineCountdown.target(exam))
        assertEquals(at(24, 23, 59), DeadlineCountdown.target(deadline))
        assertEquals(null, DeadlineCountdown.target(item("n", ManualEventKind.NORMAL, at(24, 9))))
    }

    @Test fun `Reihenfolge, laufend zuerst, Vergangenes und Horizont`() {
        val items = listOf(
            item("later", ManualEventKind.EXAM, at(30, 9)),
            item("running", ManualEventKind.EXAM, at(21, 21), at(21, 23)),
            item("past", ManualEventKind.DEADLINE, at(20, 9), at(20, 10)),
            item("soon", ManualEventKind.DEADLINE, at(22, 8), at(22, 9)),
            item("normal", ManualEventKind.NORMAL, at(22, 9)),
        )
        val all = DeadlineCountdown.upcoming(items, now, zone)
        assertEquals(listOf("running", "soon", "later"), all.map { it.item.id })
        assertEquals(CountdownRemaining.InProgress, all.first().remaining)
        assertEquals(listOf("running", "soon"), DeadlineCountdown.upcoming(items, now, zone, horizon = Duration.ofDays(5)).map { it.item.id })
        assertEquals(listOf("later"), DeadlineCountdown.upcoming(items, now, zone, kinds = setOf(ManualEventKind.EXAM)).map { it.item.id }.filter { it != "running" })
    }

    /** Ab 48 Stunden Kalendertage (22:00 bis Prüfung am 30. um 9:00 = 9 Tage); darunter aufgerundete Stunden/Minuten. */
    @Test fun `Restzeit Tage, Stunden, Minuten`() {
        assertEquals(CountdownRemaining.Days(9), DeadlineCountdown.remaining(now, at(30, 9), zone))
        assertEquals(CountdownRemaining.Hours(11), DeadlineCountdown.remaining(now, at(22, 8, 30), zone))
        assertEquals(CountdownRemaining.Minutes(35), DeadlineCountdown.remaining(now, at(21, 22, 35), zone))
        assertEquals(CountdownRemaining.Minutes(1), DeadlineCountdown.remaining(now, now.plusSeconds(10), zone))
    }

    @Test fun `angeheftetes Hauptziel mit Rückfall auf das nächste`() {
        val entries = DeadlineCountdown.upcoming(listOf(item("a", ManualEventKind.EXAM, at(25, 9)), item("b", ManualEventKind.EXAM, at(28, 9))), now, zone)
        assertEquals("b", DeadlineCountdown.featured(entries, "b")!!.item.id)
        assertEquals("a", DeadlineCountdown.featured(entries, "gelöscht")!!.item.id)
        assertEquals("a", DeadlineCountdown.featured(entries, null)!!.item.id)
        assertTrue(DeadlineCountdown.featured(emptyList(), "a") == null)
    }
}
