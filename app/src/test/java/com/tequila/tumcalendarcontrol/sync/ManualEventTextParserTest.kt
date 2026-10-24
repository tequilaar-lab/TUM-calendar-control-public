/*
 * Aufgabe: Entwurf aus geteiltem Text prüfen: Datumsformen (ISO, deutsch, chinesisch, Monatsnamen), Jahr ergänzen,
 *   Zeitspanne/Uhrzeit (24 h, am/pm, „Uhr“), Art aus Schlüsselwörtern, Titel aus Betreff oder erster Zeile.
 * Ein-/Ausgabe: synthetische Texte, „heute“ = 24.10.2026 -> ManualEventDraft.
 * Nebenwirkungen: Keine.
 * Nicht zuständig: Formular oder Empfang des Teilens.
 */
package com.tequila.tumcalendarcontrol.sync

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualEventTextParserTest {
    private val today = LocalDate.of(2026, 10, 24)
    private fun parse(text: String, subject: String? = null) = ManualEventTextParser.parse(text, subject, today)

    @Test fun `Prüfung mit deutschem Datum und Zeitspanne`() {
        val draft = parse("Klausur Synthetik Analysis\nam 02.11.2026 von 10:30 bis 12:00 im Hörsaal 0000.00.001")
        assertEquals(ManualEventKind.EXAM, draft.kind)
        assertEquals(LocalDate.of(2026, 11, 2), draft.date)
        assertEquals(LocalTime.of(10, 30) to LocalTime.of(12, 0), draft.start to draft.end)
        assertEquals("Klausur Synthetik Analysis", draft.title)
    }

    @Test fun `Frist ohne Jahr, Uhrzeit mit Uhr`() {
        val draft = parse("Bitte beachten: Abgabe des Übungsblatts bis 3.11. um 23.59 Uhr.", subject = "Übungsblatt 5 (Synthetik)")
        assertEquals(ManualEventKind.DEADLINE, draft.kind)
        assertEquals(LocalDate.of(2026, 11, 3), draft.date)
        assertEquals(LocalTime.of(23, 59), draft.start)
        assertNull(draft.end)
        assertEquals("Übungsblatt 5 (Synthetik)", draft.title)
    }

    @Test fun `weitere Datums- und Zeitformen`() {
        assertEquals(LocalDate.of(2026, 11, 2), parse("Exam on November 2, 2026 at 2 pm").date)
        assertEquals(LocalTime.of(14, 0), parse("Exam on November 2, 2026 at 2 pm").start)
        assertEquals(LocalDate.of(2026, 12, 5), parse("Treffen am 5. Dezember um 14 Uhr").date)
        assertEquals(LocalTime.of(14, 0), parse("Treffen am 5. Dezember um 14 Uhr").start)
        assertEquals(LocalDate.of(2026, 11, 20), parse("期中考试 11月20日 9:00-11:00").date)
        assertEquals(ManualEventKind.EXAM, parse("期中考试 11月20日 9:00-11:00").kind)
        assertEquals(LocalDate.of(2027, 3, 1), parse("Sprechstunde 2027-03-01").date)
        // Ohne Jahr und schon vorbei: nächstes Jahr.
        assertEquals(LocalDate.of(2027, 10, 1), parse("Termin am 1.10.").date)
    }

    @Test fun `ohne Datum und Zeit, Adressen entfernt`() {
        val draft = parse("  \nSynthetik Seminar https://example.org/x\nweitere Zeile")
        assertNull(draft.date)
        assertNull(draft.start)
        assertEquals(ManualEventKind.NORMAL, draft.kind)
        assertEquals("Synthetik Seminar", draft.title)
    }
}
