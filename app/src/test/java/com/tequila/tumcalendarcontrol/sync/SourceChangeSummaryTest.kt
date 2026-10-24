/*
 * Aufgabe: Ausführlichen Quellvergleich prüfen: Arten je Termin, Identität nur über UID/Instanz, Reihenfolge,
 *   betroffene Auswahl und Einschränkung auf den gemeinsamen Zeitraum (Hintergrundprüfung).
 * Ein-/Ausgabe: Synthetische Termine -> erwartete Änderungen.
 * Nebenwirkungen: Keine.
 * Nicht zuständig: Darstellung oder Kurzzusammenfassung der Datenversionen.
 */
package com.tequila.tumcalendarcontrol.sync

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceChangeSummaryTest {
    private val base = Instant.parse("2026-10-05T08:00:00Z")
    private fun occ(uid: String, inst: String, day: Long, title: String = "Synthetik $uid", location: String = "Raum A", cancelled: Boolean = false, shift: Long = 0) =
        SourceCalendarOccurrence(uid, inst, title, base.plusSeconds(day * 86_400 + shift), base.plusSeconds(day * 86_400 + shift + 5_400), cancelled, location)

    @Test fun `unveränderte Lesung ergibt keine Änderungen`() {
        val items = listOf(occ("a", "1", 0), occ("b", "1", 1))
        assertTrue(SourceChangeSummary.between(items, items).isEmpty)
    }

    @Test fun `Arten je Termin und mehrere Arten zugleich`() {
        val previous = listOf(occ("a", "1", 0), occ("a", "2", 7), occ("b", "1", 1), occ("c", "1", 2, cancelled = true), occ("d", "1", 3))
        val latest = listOf(
            occ("a", "1", 0, shift = 900, location = "Raum B"),
            occ("a", "2", 7, cancelled = true),
            occ("c", "1", 2),
            occ("d", "1", 3, title = "Synthetik d neu"),
            occ("e", "1", 4),
        )
        val summary = SourceChangeSummary.between(previous, latest)
        assertEquals(setOf(SourceChangeKind.TIME, SourceChangeKind.LOCATION), summary.kindsOf(SourceOccurrenceIdentity("a", "1")))
        assertEquals(setOf(SourceChangeKind.CANCELLED), summary.kindsOf(SourceOccurrenceIdentity("a", "2")))
        assertEquals(setOf(SourceChangeKind.RESTORED), summary.kindsOf(SourceOccurrenceIdentity("c", "1")))
        assertEquals(setOf(SourceChangeKind.TITLE), summary.kindsOf(SourceOccurrenceIdentity("d", "1")))
        assertEquals(setOf(SourceChangeKind.ADDED), summary.kindsOf(SourceOccurrenceIdentity("e", "1")))
        assertEquals(setOf(SourceChangeKind.REMOVED), summary.kindsOf(SourceOccurrenceIdentity("b", "1")))
        assertEquals(6, summary.changes.size)
        assertEquals(1, summary.count(SourceChangeKind.TIME))
        // Geordnet nach Beginn des aktuellen (bzw. letzten bekannten) Stands.
        assertEquals(listOf("a", "b", "c", "d", "e", "a"), summary.changes.map { it.current.sourceUid })
    }

    /** Gleicher Titel, andere UID = neu + entfallen; niemals über den Titel zusammenführen. */
    @Test fun `Identität nur über UID und Instanz`() {
        val summary = SourceChangeSummary.between(listOf(occ("a", "1", 0, title = "Gleich")), listOf(occ("z", "1", 0, title = "Gleich")))
        assertEquals(setOf(SourceChangeKind.REMOVED), summary.kindsOf(SourceOccurrenceIdentity("a", "1")))
        assertEquals(setOf(SourceChangeKind.ADDED), summary.kindsOf(SourceOccurrenceIdentity("z", "1")))
    }

    @Test fun `betroffene Auswahl zählt auch entfallene Termine`() {
        val summary = SourceChangeSummary.between(listOf(occ("a", "1", 0), occ("b", "1", 1)), listOf(occ("a", "1", 0, shift = 600)))
        assertEquals(2, summary.affecting(setOf(SourceOccurrenceIdentity("a", "1"), SourceOccurrenceIdentity("b", "1"))))
        assertEquals(0, summary.affecting(setOf(SourceOccurrenceIdentity("x", "1"))))
    }

    /** Gespeicherte Lesung deckt Tag 0–3 ab, Hintergrundlesung Tag 2–9: neu/entfallen nur in Tag 2–3. */
    @Test fun `gemeinsamer Zeitraum begrenzt neu und entfallen`() {
        val saved = listOf(occ("a", "0", 0), occ("a", "2", 2), occ("a", "3", 3))
        val read = listOf(occ("a", "2", 2, shift = 300), occ("b", "3", 3), occ("a", "9", 9))
        val summary = SourceChangeSummary.between(saved, read, restrictToCommonSpan = true)
        assertEquals(setOf(SourceChangeKind.TIME), summary.kindsOf(SourceOccurrenceIdentity("a", "2")))
        assertEquals(setOf(SourceChangeKind.ADDED), summary.kindsOf(SourceOccurrenceIdentity("b", "3")))
        assertEquals(setOf(SourceChangeKind.REMOVED), summary.kindsOf(SourceOccurrenceIdentity("a", "3")))
        assertTrue("außerhalb: kein Entfallen", summary.kindsOf(SourceOccurrenceIdentity("a", "0")).isEmpty())
        assertTrue("außerhalb: nicht neu", summary.kindsOf(SourceOccurrenceIdentity("a", "9")).isEmpty())
        assertEquals(3, summary.changes.size)
    }

    @Test fun `ohne gemeinsamen Zeitraum nur gemeinsame Termine vergleichen`() {
        val summary = SourceChangeSummary.between(listOf(occ("a", "0", 0)), listOf(occ("b", "9", 9)), restrictToCommonSpan = true)
        assertTrue(summary.isEmpty)
    }
}
