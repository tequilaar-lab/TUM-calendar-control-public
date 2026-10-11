/*
 * Aufgabe: synthetische Regressionstests für RuleLedgerBackupCodec.
 * Ein-/Ausgabe: künstliche Zustände und Fixtures -> JUnit-Prüfergebnisse.
 * Nebenwirkungen: nur lokaler Testprozess und gegebenenfalls Robolectric-Präferenzen.
 * Nicht zuständig: echte Konten, private Quellen, Geräte oder reale Kalenderausgaben.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RuleLedgerBackupCodecTest {
    @Test
    fun `备份只往返 UID 与课程组规则和手动事项 不含映射或摘要`() {
        val groupRule = GroupAttendanceRule(
            SourceCourseGroupKey.fromTitle("Synthetic Course VI, Group 1"),
            AttendanceState.MUST_ATTEND,
        )
        val ledger = RuleLedger(
            rules = listOf(SyncRule("synthetic-uid", false, AttendanceState.MUST_ATTEND)),
            manualEvents = listOf(ManualScheduleItem("manual-1", "合成事项", Instant.parse("2026-10-01T08:00:00Z"), Instant.parse("2026-10-01T09:00:00Z"), AttendanceState.CHOOSE_TO_ATTEND)),
            mappings = listOf(StoredEventMapping(OutputTarget.LOCAL_CALENDAR, ManagedEventIdentity("synthetic-uid", "instance"), "event-id", "fingerprint")),
            lastSummary = SyncRunSummary(Instant.parse("2026-10-01T10:00:00Z"), 1, 0, 0, 0),
            groupRules = listOf(groupRule),
        )

        val text = RuleLedgerBackupCodec.encode(ledger)
        val decoded = RuleLedgerBackupCodec.decode(text)

        assertEquals(ledger.rules, decoded!!.rules)
        assertEquals(listOf(groupRule), decoded.groupRules)
        assertEquals(ledger.manualEvents, decoded.manualEvents)
        assertFalse(text.contains("event-id"))
        assertFalse(text.contains("fingerprint"))
        assertFalse(text.contains("2026-10-01T10:00:00Z"))
    }

    @Test
    fun `非备份头或恶意映射内容会被拒绝`() {
        assertNull(RuleLedgerBackupCodec.decode("v1"))
        val unsafe = "tum-calendar-rule-backup-v1\nv1\np|LOCAL_CALENDAR|dWlk|aW5zdGFuY2U|ZXZlbnQ|ZmluZ2VycHJpbnQ"
        assertNull(RuleLedgerBackupCodec.decode(unsafe))
    }

    @Test fun `旧版备份头仍可安全导入`() {
        val old = "tum-calendar-rule-backup-v1\nv1\nr|dWlk|true|MUST_ATTEND|"
        assertEquals("uid", RuleLedgerBackupCodec.decode(old)!!.rules.single().sourceUid)
    }
}
