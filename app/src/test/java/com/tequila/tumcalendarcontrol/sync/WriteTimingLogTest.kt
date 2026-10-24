/*
 * Aufgabe: Protokollzeile der Ausgabe prüfen: nur Zahlen und Zielarten, keine Titel, Orte, IDs oder Fehlertexte.
 * Ein-/Ausgabe: Synthetisches Ergebnis -> erwartete Zeile.
 * Nebenwirkungen: Keine.
 * Nicht zuständig: Systemprotokoll selbst.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WriteTimingLogTest {
    @Test fun `nur Zahlen und Zielarten`() {
        val identity = ManagedEventIdentity("geheime-uid", "i1")
        val event = DesiredCalendarEvent(identity, "Geheimer Kurs", Instant.parse("2026-10-01T08:00:00Z"), Instant.parse("2026-10-01T09:00:00Z"), AttendanceState.MUST_ATTEND, "Geheimer Raum")
        val result = ManagedSyncResult(
            listOf(StoredEventMapping(OutputTarget.LOCAL_CALENDAR, identity, "event-123", "f", "calendar-9", event)),
            listOf(
                TargetSyncResult(OutputTarget.LOCAL_CALENDAR, emptyList(), listOf(SyncChange(SyncChangeType.CREATE, identity, "event-123"), SyncChange(SyncChangeType.KEEP, identity, "e2")), failureSummary = "Fehler mit Details", skippedCount = 3),
                TargetSyncResult(OutputTarget.GOOGLE_CALENDAR, emptyList(), listOf(SyncChange(SyncChangeType.UPDATE, identity, "g1")), failedCount = 1),
            ),
        )
        val line = WriteTimingLog.line(result, 1234, rollback = false)
        assertEquals("stage=finished kind=write elapsedMs=1234 created=1 updated=1 kept=1 removed=0 failed=1 skipped=3 targets=GOOGLE_CALENDAR,LOCAL_CALENDAR", line)
        listOf("Geheim", "geheime", "event-123", "calendar-9", "Fehler").forEach { assertFalse(it, line.contains(it)) }
    }
}
