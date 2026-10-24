/*
 * Aufgabe: Aufstellung der Quelländerungen je Kurs prüfen: Arten je Kurs zusammengefasst, Reihenfolge nach frühestem
 *   Termin, höchstens drei Kurse und „另有 N 门课“.
 * Ein-/Ausgabe: synthetische Lesungen vorher/nachher -> Textzeilen (chinesische Vorlagen).
 * Nebenwirkungen: Keine.
 * Nicht zuständig: Benachrichtigung selbst (PeriodicSourceCheckWorker) oder Erkennung (SourceChangeSummary).
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceChangeSummary
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceChangeDigestTest {
    private fun occ(uid: String, title: String, day: Int, hour: Int, room: String = "Raum") =
        SourceCalendarOccurrence(uid, "$uid-$day", title, Instant.parse("2026-10-%02dT%02d:00:00Z".format(day, hour)), Instant.parse("2026-10-%02dT%02d:30:00Z".format(day, hour + 1)), false, room)

    @Test fun `je Kurs Arten, höchstens drei, Rest gezählt`() {
        val before = listOf(occ("a", "Synthetik A", 20, 8), occ("a", "Synthetik A", 21, 8), occ("b", "Synthetik B", 19, 10), occ("c", "Synthetik C", 22, 9), occ("d", "Synthetik D", 23, 9))
        val after = listOf(
            occ("a", "Synthetik A", 20, 9), occ("a", "Synthetik A", 21, 8, room = "Neu"),
            occ("b", "Synthetik B", 19, 10, room = "Neu"), occ("c", "Synthetik C", 22, 10), occ("d", "Synthetik D", 23, 11),
        )
        val digest = SourceChangeSummary.between(before, after).courseDigest()
        assertEquals(4, digest.size)
        assertEquals("Synthetik B：地点已变", digest[0])
        assertEquals("Synthetik A：时间已变 · 地点已变", digest[1])
        assertEquals("另有 1 门课", digest[3])
    }

    @Test fun `ohne Änderungen leer`() {
        val same = listOf(occ("a", "Synthetik A", 20, 8))
        assertEquals(emptyList<String>(), SourceChangeSummary.between(same, same).courseDigest())
    }
}
