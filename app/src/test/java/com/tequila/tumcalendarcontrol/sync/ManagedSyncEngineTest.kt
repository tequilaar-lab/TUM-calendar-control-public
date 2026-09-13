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
}
