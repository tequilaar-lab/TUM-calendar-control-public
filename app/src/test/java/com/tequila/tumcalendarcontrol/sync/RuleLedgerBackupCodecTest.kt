/*
 * 职责：验证规则备份只包含可恢复规则和手动事项，并拒绝非备份／含映射内容。
 * 输入／输出：输入合成资料库和文本；输出编解码及隐私边界断言。
 * 外部副作用：无。
 * 不负责：SAF 文件选择、偏好持久化、来源读取或日历验证。
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
    fun `备份只往返规则和手动事项 不含映射或摘要`() {
        val ledger = RuleLedger(
            rules = listOf(SyncRule("synthetic-uid", false, AttendanceState.MUST_ATTEND)),
            manualEvents = listOf(ManualScheduleItem("manual-1", "合成事项", Instant.parse("2026-10-01T08:00:00Z"), Instant.parse("2026-10-01T09:00:00Z"), AttendanceState.CHOOSE_TO_ATTEND)),
            mappings = listOf(StoredEventMapping(OutputTarget.LOCAL_CALENDAR, ManagedEventIdentity("synthetic-uid", "instance"), "event-id", "fingerprint")),
            lastSummary = SyncRunSummary(Instant.parse("2026-10-01T10:00:00Z"), 1, 0, 0, 0),
        )

        val text = RuleLedgerBackupCodec.encode(ledger)
        val decoded = RuleLedgerBackupCodec.decode(text)

        assertEquals(ledger.rules, decoded!!.rules)
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
}
