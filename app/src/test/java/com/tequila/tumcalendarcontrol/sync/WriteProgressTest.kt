/*
 * Aufgabe: Fortschritt und Anhalten der Ausgabe prüfen: Meldung je Termin, Anhalten nur zwischen zwei Terminen mit
 *   vollständigen Prüfpunkten, Fortsetzen ohne erneutes Schreiben, Anhalten vor einem weiteren Ziel, Ergebnistext.
 * Ein-/Ausgabe: Synthetische Termine und Ersatz-Gateways -> Ergebnisse, Prüfpunkte und Meldungen.
 * Nebenwirkungen: Keine; keine Kalender, Konten oder Netzwerke.
 * Nicht zuständig: Darstellung (WriteProgressPagesTest) oder echte Laufzeiten.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WriteProgressTest {
    private val local = OwnedCalendarRef(OutputTarget.LOCAL_CALENDAR, "owned", "tum-calendar-control")
    private val second = OwnedCalendarRef(OutputTarget.GOOGLE_CALENDAR, "owned", "tum-calendar-control")
    private fun events(n: Int) = (1..n).map {
        DesiredCalendarEvent(ManagedEventIdentity("synthetic", "i$it"), "Synthetik $it", Instant.parse("2026-10-01T08:00:00Z").plusSeconds(it * 86_400L),
            Instant.parse("2026-10-01T09:00:00Z").plusSeconds(it * 86_400L), AttendanceState.MUST_ATTEND, "Raum")
    }

    @Test fun `jeder Termin meldet Fortschritt`() {
        val reports = mutableListOf<WriteProgress>()
        OwnedCalendarSyncExecutor().execute(local, events(4), emptyList(), FakeOwnedCalendarGateway(setOf("owned")), control = WriteControl { reports += it })
        assertEquals(listOf(1, 2, 3, 4), reports.map { it.done })
        assertTrue(reports.all { it.total == 4 && it.target == OutputTarget.LOCAL_CALENDAR })
    }

    /** Anhalten nach der dritten Meldung: drei geschrieben und gesichert, sieben übersprungen; Fortsetzen schreibt nur den Rest. */
    @Test fun `anhalten zwischen Terminen und danach fortsetzen`() {
        val gateway = FakeOwnedCalendarGateway(setOf("owned"))
        val checkpoints = mutableListOf<List<StoredEventMapping>>()
        lateinit var control: WriteControl
        control = WriteControl { if (it.done == 3) control.requestStop() }
        val first = OwnedCalendarSyncExecutor().execute(local, events(10), emptyList(), gateway, control) { checkpoints += it }
        assertEquals(3, first.changes.count { it.type == SyncChangeType.CREATE })
        assertEquals(7, first.skippedCount)
        assertEquals(3, checkpoints.size)
        assertEquals(3, checkpoints.last().size)
        assertEquals(3, first.mappings.size)

        val again = OwnedCalendarSyncExecutor().execute(local, events(10), first.mappings, gateway)
        assertEquals(3, again.changes.count { it.type == SyncChangeType.KEEP })
        assertEquals(7, again.changes.count { it.type == SyncChangeType.CREATE })
        assertEquals("keine doppelten Termine", 10, gateway.operations.count { it.startsWith("create:") })
    }

    @Test fun `Anhalten vor weiterem Ziel lässt es unberührt`() {
        val firstGateway = FakeOwnedCalendarGateway(setOf("owned"))
        val secondGateway = FakeOwnedCalendarGateway(setOf("owned"))
        lateinit var control: WriteControl
        control = WriteControl { if (it.done == it.total) control.requestStop() }
        val result = ManagedSyncEngine().execute(events(2), emptyList(), listOf(TargetBinding(local, firstGateway), TargetBinding(second, secondGateway)), control = control)
        assertEquals(2, firstGateway.operations.size)
        assertTrue(secondGateway.operations.isEmpty())
        assertEquals(listOf(0, 2), result.targetResults.map { it.skippedCount })
        assertTrue(result.stoppedEarly)
        assertTrue(result.writeDetailSummary.contains("已停止，2 个课次未处理"))
    }

    @Test fun `ohne Anhalten kein Hinweis`() {
        val result = ManagedSyncEngine().execute(events(2), emptyList(), listOf(TargetBinding(local, FakeOwnedCalendarGateway(setOf("owned")))))
        assertFalse(result.stoppedEarly)
        assertFalse(result.writeDetailSummary.contains("已停止"))
    }
}
