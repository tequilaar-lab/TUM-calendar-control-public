/*
 * 职责：验证多目标预览、失败隔离与映射限定撤销。
 * 输入／输出：输入合成绑定和内存网关；输出差异、映射与失败摘要断言。
 * 外部副作用：无。
 * 不负责：Android Provider、OAuth、Google 网络或真机测试。
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedSyncEngineTest {
    private val event = DesiredCalendarEvent(ManagedEventIdentity("uid", "instance"), "合成", Instant.parse("2026-10-01T08:00:00Z"), Instant.parse("2026-10-01T09:00:00Z"), AttendanceState.MUST_ATTEND)

    @Test
    fun `预览不写入且每个启用目标都有独立差异`() {
        val preview = ManagedSyncEngine().preview(listOf(event), emptyList(), setOf(OutputTarget.LOCAL_CALENDAR, OutputTarget.GOOGLE_CALENDAR))

        assertEquals(1, preview.desiredEventCount)
        assertEquals(setOf(OutputTarget.LOCAL_CALENDAR, OutputTarget.GOOGLE_CALENDAR), preview.targetChanges.keys)
        assertTrue(preview.targetChanges.values.all { it.single().type == SyncChangeType.CREATE })
    }

    @Test
    fun `一个目标失败不污染另一个目标映射 且撤销只清理成功目标`() {
        val gateway = FakeOwnedCalendarGateway(setOf("local"))
        val result = ManagedSyncEngine().execute(
            desired = listOf(event),
            mappings = emptyList(),
            bindings = listOf(
                TargetBinding(OwnedCalendarRef(OutputTarget.LOCAL_CALENDAR, "local", "tum-calendar-control"), gateway),
                TargetBinding(OwnedCalendarRef(OutputTarget.GOOGLE_CALENDAR, "missing", "tum-calendar-control"), gateway),
            ),
        )

        assertEquals(1, result.mappings.size)
        assertTrue(result.failureSummary!!.contains("拒绝"))
        val rollback = ManagedSyncEngine().rollback(result.mappings, listOf(TargetBinding(OwnedCalendarRef(OutputTarget.LOCAL_CALENDAR, "local", "tum-calendar-control"), gateway)))
        assertTrue(rollback.mappings.isEmpty())
    }

    @Test
    fun `全量清理按本机和 Google 分开复核而不混淆摘要`() {
        val localGateway = FakeOwnedCalendarGateway(setOf("local"), allManagedIds = listOf("local-mapped", "local-orphan"))
        val googleGateway = FakeOwnedCalendarGateway(setOf("google"), allManagedIds = listOf("google-mapped", "google-orphan"))
        val localIdentity = ManagedEventIdentity("local-uid", "local-instance")
        val googleIdentity = ManagedEventIdentity("google-uid", "google-instance")
        val result = ManagedSyncEngine().rollback(
            mappings = listOf(
                StoredEventMapping(OutputTarget.LOCAL_CALENDAR, localIdentity, "local-mapped", "old"),
                StoredEventMapping(OutputTarget.GOOGLE_CALENDAR, googleIdentity, "google-mapped", "old"),
            ),
            bindings = listOf(
                TargetBinding(OwnedCalendarRef(OutputTarget.LOCAL_CALENDAR, "local", "tum-calendar-control"), localGateway),
                TargetBinding(OwnedCalendarRef(OutputTarget.GOOGLE_CALENDAR, "google", "tum-calendar-control"), googleGateway),
            ),
        )

        assertEquals(4, result.removedCount)
        assertEquals(0, result.targetResults.single { it.target == OutputTarget.LOCAL_CALENDAR }.cleanupSummary!!.remainingManagedCount)
        assertEquals(0, result.targetResults.single { it.target == OutputTarget.GOOGLE_CALENDAR }.cleanupSummary!!.remainingManagedCount)
        assertTrue(result.cleanupDetailSummary!!.contains("本机日历：本次请求清理 2 个应用日程，已删除 2 个"))
        assertTrue(result.cleanupDetailSummary!!.contains("Google 日历：本次请求清理 2 个应用日程，已删除 2 个"))
    }
}
