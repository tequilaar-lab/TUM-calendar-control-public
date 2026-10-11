/*
 * Aufgabe: synthetische Regressionstests für IcalSourceParser.
 * Ein-/Ausgabe: künstliche Zustände und Fixtures -> JUnit-Prüfergebnisse.
 * Nebenwirkungen: nur lokaler Testprozess und gegebenenfalls Robolectric-Präferenzen.
 * Nicht zuständig: echte Konten, private Quellen, Geräte oder reale Kalenderausgaben.
 */
package com.tequila.tumcalendarcontrol.sync

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IcalSourceParserTest {
    private val parser = IcalSourceParser()
    private val windowStart = Instant.parse("2026-09-01T00:00:00Z")
    private val windowEnd = Instant.parse("2026-10-01T00:00:00Z")

    @Test
    fun `展开重复 排除例外并以原始实例键合并改期`() {
        val result = parser.parse(
            icsText = syntheticCalendar(),
            windowStart = windowStart,
            windowEnd = windowEnd,
        )

        val fixedOccurrences = result.occurrences.filter { it.sourceUid == "fixed-course" }
        assertEquals(3, fixedOccurrences.size)
        assertFalse(fixedOccurrences.any { it.startsAt == Instant.parse("2026-09-14T08:00:00Z") })
        assertTrue(fixedOccurrences.any { it.startsAt == Instant.parse("2026-09-22T10:00:00Z") })
        assertTrue(fixedOccurrences.all { it.instanceKey.startsWith("2026-") })
    }

    @Test
    fun `保留取消事件并默认接受全部来源 UID`() {
        val result = parser.parse(
            icsText = syntheticCalendar(),
            windowStart = windowStart,
            windowEnd = windowEnd,
        )

        assertTrue(result.occurrences.single { it.sourceUid == "cancelled-course" }.isCancelled)
        assertTrue(result.occurrences.any { it.sourceUid == "timezone-course" })
        assertTrue(result.occurrences.any { it.sourceUid == "not-confirmed" })
        assertEquals(emptyList<IcalSkippedEvent>(), result.skipped)
    }

    private fun syntheticCalendar(): String = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//Synthetic//TUM Calendar Control//EN
        BEGIN:VEVENT
        UID:fixed-course
        DTSTART:20260907T080000Z
        DTEND:20260907T100000Z
        RRULE:FREQ=WEEKLY;COUNT=4
        EXDATE:20260914T080000Z
        SUMMARY:合成重复课程
        END:VEVENT
        BEGIN:VEVENT
        UID:fixed-course
        RECURRENCE-ID:20260921T080000Z
        DTSTART:20260922T100000Z
        DTEND:20260922T120000Z
        SUMMARY:合成改期课程
        END:VEVENT
        BEGIN:VEVENT
        UID:cancelled-course
        DTSTART:20260909T090000Z
        DTEND:20260909T100000Z
        STATUS:CANCELLED
        SUMMARY:合成取消课程
        END:VEVENT
        BEGIN:VEVENT
        UID:timezone-course
        DTSTART;TZID=Europe/Berlin:20260910T100000
        DTEND;TZID=Europe/Berlin:20260910T110000
        SUMMARY:合成时区课程
        END:VEVENT
        BEGIN:VEVENT
        UID:not-confirmed
        DTSTART:20260911T090000Z
        DTEND:20260911T100000Z
        SUMMARY:未确认课程
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()
}
