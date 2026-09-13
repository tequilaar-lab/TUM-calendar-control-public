/*
 * 职责：验证双目标日历执行器的所有权保护、幂等映射和双路隔离。
 * 输入／输出：输入合成事件、映射和内存网关；输出写入操作与映射断言。
 * 外部副作用：无。
 * 不负责：真机 Calendar Provider、Google OAuth 或真实网络验证。
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnedCalendarSyncExecutorTest {
    private val identity = ManagedEventIdentity("synthetic", "2026-10-01T08:00:00Z")
    private val event = DesiredCalendarEvent(identity, "合成课程", Instant.parse(identity.instanceKey), Instant.parse(identity.instanceKey).plusSeconds(3600), AttendanceState.MUST_ATTEND)

    @Test fun `两个拥有目标各自创建且重复运行保持不变`() {
        val executor = OwnedCalendarSyncExecutor()
        val gateway = FakeOwnedCalendarGateway(setOf("local", "google"))
        val local = OwnedCalendarRef(OutputTarget.LOCAL_CALENDAR, "local", "tum-calendar-control")
        val google = OwnedCalendarRef(OutputTarget.GOOGLE_CALENDAR, "google", "tum-calendar-control")
        val firstLocal = executor.execute(local, listOf(event), emptyList(), gateway)
        val firstGoogle = executor.execute(google, listOf(event), firstLocal.mappings, gateway)
        val repeated = executor.execute(local, listOf(event), firstGoogle.mappings, gateway)
        assertEquals(2, gateway.operations.count { it.startsWith("create:") })
        assertEquals(listOf(SyncChangeType.KEEP), repeated.changes.map { it.type })
        assertEquals(2, repeated.mappings.size)
    }

    @Test fun `未知目标拒绝且零写入`() {
        val gateway = FakeOwnedCalendarGateway(emptySet())
        val result = OwnedCalendarSyncExecutor().execute(OwnedCalendarRef(OutputTarget.LOCAL_CALENDAR, "unknown", "tum-calendar-control"), listOf(event), emptyList(), gateway)
        assertTrue(result.failureSummary!!.contains("拒绝"))
        assertTrue(gateway.operations.isEmpty())
    }

    @Test fun `撤销只删除映射事件 不触碰其他目标映射`() {
        val executor = OwnedCalendarSyncExecutor()
        val gateway = FakeOwnedCalendarGateway(setOf("local", "google"))
        val local = OwnedCalendarRef(OutputTarget.LOCAL_CALENDAR, "local", "tum-calendar-control")
        val google = OwnedCalendarRef(OutputTarget.GOOGLE_CALENDAR, "google", "tum-calendar-control")
        val localResult = executor.execute(local, listOf(event), emptyList(), gateway)
        val both = executor.execute(google, listOf(event), localResult.mappings, gateway)

        val rolledBack = executor.rollback(local, both.mappings, gateway)

        assertEquals(1, rolledBack.mappings.size)
        assertEquals(OutputTarget.GOOGLE_CALENDAR, rolledBack.mappings.single().target)
        assertEquals(listOf("create:local_calendar-1", "create:google_calendar-2", "remove:local_calendar-1"), gateway.operations)
    }
}
