/*
 * Aufgabe: Art eigener Termine im Regelbestand prüfen: alte Zeilen lesbar, normale Termine Zeichen für Zeichen wie bisher,
 *   Prüfung/Frist im Hin- und Rückweg und in der Sicherung, unbekannte Art verwirft nichts, Kalenderausgabe unverändert.
 * Ein-/Ausgabe: Synthetische Termine -> Kodierung, Dekodierung und Ausgabekandidaten.
 * Nebenwirkungen: Keine.
 * Nicht zuständig: Oberfläche (ManualEventKindPagesTest) oder Countdown.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualEventKindCodecTest {
    private val start = Instant.parse("2026-11-05T08:00:00Z")
    private fun item(id: String, kind: ManualEventKind = ManualEventKind.NORMAL) =
        ManualScheduleItem(id, "Synthetik $id", start, start.plusSeconds(7_200), AttendanceState.MUST_ATTEND, kind)

    @Test fun `normaler Termin wird wie bisher kodiert und alte Zeilen bleiben lesbar`() {
        val encoded = RuleLedgerCodec.encode(RuleLedger(manualEvents = listOf(item("a"))))
        val line = encoded.lines().single { it.startsWith("m|") }
        assertEquals("Sechs Felder wie vor 1.4.0", 6, line.split('|').size)
        assertEquals(ManualEventKind.NORMAL, RuleLedgerCodec.decode(encoded).manualEvents.single().kind)
    }

    @Test fun `Prüfung und Frist im Hin- und Rückweg und in der Sicherung`() {
        val ledger = RuleLedger(manualEvents = listOf(item("e", ManualEventKind.EXAM), item("d", ManualEventKind.DEADLINE), item("n")))
        val decoded = RuleLedgerCodec.decode(RuleLedgerCodec.encode(ledger))
        assertEquals(ledger.manualEvents.sortedBy { it.id }, decoded.manualEvents.sortedBy { it.id })
        val backup = RuleLedgerBackupCodec.decode(RuleLedgerBackupCodec.encode(ledger))!!
        assertEquals(setOf(ManualEventKind.EXAM, ManualEventKind.DEADLINE, ManualEventKind.NORMAL), backup.manualEvents.map { it.kind }.toSet())
    }

    @Test fun `unbekannte Art einer neueren Version wird als normal gelesen`() {
        val encoded = RuleLedgerCodec.encode(RuleLedger(manualEvents = listOf(item("x", ManualEventKind.EXAM)))).replace("|EXAM", "|KUENFTIG")
        val decoded = RuleLedgerCodec.decodeOrNull(encoded)
        assertTrue("Bestand bleibt erhalten", decoded != null)
        assertEquals(ManualEventKind.NORMAL, decoded!!.manualEvents.single().kind)
    }

    /** Die Art ändert nichts am geschriebenen Kalendertermin (keine Synchronisierungsänderung). */
    @Test fun `Art ändert den Ausgabekandidaten nicht`() {
        val normal = SyncCandidateFactory().build(emptyList(), emptySet(), emptyList(), listOf(item("a")), emptyMap())
        val exam = SyncCandidateFactory().build(emptyList(), emptySet(), emptyList(), listOf(item("a", ManualEventKind.EXAM)), emptyMap())
        assertEquals(normal, exam)
        assertFalse(exam.single().title.contains("Prüfung"))
    }
}
