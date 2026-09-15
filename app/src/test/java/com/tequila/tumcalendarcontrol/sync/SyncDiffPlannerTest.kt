/*
 * 职责：验证同步差异只以 UID 与实例键定位，正确生成新增、更新、移除和保持不变操作。
 * 输入／输出：输入合成来源实例及既有应用映射；输出差异类型断言。
 * 外部副作用：无。
 * 不负责：真实日历 API、网络或数据库验证。
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncDiffPlannerTest {
    private val firstIdentity = ManagedEventIdentity("synthetic-uid", "2026-10-01T08:00:00Z")
    private val secondIdentity = ManagedEventIdentity("synthetic-uid", "2026-10-08T08:00:00Z")
    private val planner = SyncDiffPlanner()

    @Test
    fun `同一稳定身份会保持或更新 不按标题去重`() {
        val first = desired(firstIdentity, "合成课程", AttendanceState.MUST_ATTEND)
        val changedTitle = desired(secondIdentity, "同名合成课程", AttendanceState.CHOOSE_TO_ATTEND)
        val existing = listOf(
            ManagedEventMapping(firstIdentity, "local-1", planner.run { first.fingerprint() }),
            ManagedEventMapping(secondIdentity, "local-2", "过期指纹"),
        )

        val changes = planner.plan(listOf(first, changedTitle), existing)

        assertEquals(listOf(SyncChangeType.KEEP, SyncChangeType.UPDATE), changes.map(SyncChange::type))
        assertEquals("local-2", changes[1].targetEventId)
    }

    @Test
    fun `本次未选择的旧课程在普通写入中保持不变`() {
        val existing = listOf(ManagedEventMapping(firstIdentity, "local-1", "旧指纹"))

        val changes = planner.plan(emptyList(), existing)

        assertEquals(emptyList<SyncChangeType>(), changes.map(SyncChange::type))
    }

    @Test
    fun `旧版未带状态颜色修订的映射会安全更新而不会删除重建`() {
        val event = desired(firstIdentity, "合成课程", AttendanceState.MUST_ATTEND)
        val legacyFingerprint = listOf(
            event.title,
            event.startsAt.toString(),
            event.endsAt.toString(),
            event.attendanceState.name,
            event.location,
        ).joinToString("|")

        val changes = planner.plan(listOf(event), listOf(ManagedEventMapping(firstIdentity, "google-1", legacyFingerprint)))

        assertEquals(listOf(SyncChangeType.UPDATE), changes.map(SyncChange::type))
        assertEquals("google-1", changes.single().targetEventId)
    }

    @Test
    fun `上游重复同一身份在差异中也只产生一个新增`() {
        val duplicate = desired(firstIdentity, "合成课程", AttendanceState.MUST_ATTEND)

        val changes = planner.plan(listOf(duplicate, duplicate), emptyList())

        assertEquals(1, changes.size)
        assertEquals(SyncChangeType.CREATE, changes.single().type)
    }

    private fun desired(
        identity: ManagedEventIdentity,
        title: String,
        state: AttendanceState,
    ): DesiredCalendarEvent = DesiredCalendarEvent(
        identity = identity,
        title = title,
        startsAt = Instant.parse(identity.instanceKey),
        endsAt = Instant.parse(identity.instanceKey).plusSeconds(3600),
        attendanceState = state,
    )
}
