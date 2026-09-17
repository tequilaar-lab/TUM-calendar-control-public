/*
 * 职责：验证来源、规则和手动事项转换为目标事件的纯逻辑。
 * 输入／输出：输入合成实例、规则与手动事项；输出稳定身份、默认状态和过滤断言。
 * 外部副作用：无。
 * 不负责：网络、Android Provider、Google 或真实日历验证。
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCandidateFactoryTest {
    private val factory = SyncCandidateFactory()

    @Test
    fun `未设规则来源默认蓝色 标题不参与身份`() {
        val events = factory.build(
            occurrences = listOf(occurrence("uid-a", "key-a", "同名课程"), occurrence("uid-b", "key-b", "同名课程")),
            selectedSourceOccurrences = setOf(SourceOccurrenceIdentity("uid-a", "key-a"), SourceOccurrenceIdentity("uid-b", "key-b")),
            rules = emptyList(),
            manualEvents = emptyList(),
        )

        assertEquals(2, events.size)
        assertEquals(AttendanceState.CHOOSE_TO_ATTEND, events.first().attendanceState)
        assertEquals(setOf("uid-a", "uid-b"), events.map { it.identity.sourceUid }.toSet())
    }

    @Test
    fun `灰色未来规则与取消实例正确过滤 手动事项保留独立身份`() {
        val events = factory.build(
            occurrences = listOf(
                occurrence("uid-a", "past", "来源", "2026-10-01T08:00:00Z"),
                occurrence("uid-a", "future", "来源", "2026-10-20T08:00:00Z"),
                occurrence("uid-b", "cancelled", "取消", "2026-10-21T08:00:00Z", cancelled = true),
            ),
            selectedSourceOccurrences = setOf(
                SourceOccurrenceIdentity("uid-a", "past"),
                SourceOccurrenceIdentity("uid-a", "future"),
                SourceOccurrenceIdentity("uid-b", "cancelled"),
            ),
            rules = listOf(SyncRule("uid-a", true, AttendanceState.CHOOSE_TO_ATTEND, stopAttendingFrom = java.time.LocalDate.parse("2026-10-10"))),
            manualEvents = listOf(ManualScheduleItem("manual-1", "手动校园事项", Instant.parse("2026-10-22T08:00:00Z"), Instant.parse("2026-10-22T09:00:00Z"), AttendanceState.MUST_ATTEND)),
        )

        assertEquals(3, events.size)
        assertEquals(AttendanceState.CHOOSE_TO_ATTEND, events.single { it.identity.instanceKey == "past" }.attendanceState)
        assertEquals(AttendanceState.STOP_ATTENDING, events.single { it.identity.instanceKey == "future" }.attendanceState)
        assertEquals("manual:manual-1", events.single { it.title == "手动校园事项" }.identity.sourceUid)
        assertFalse(events.any { it.identity.instanceKey == "cancelled" })
    }

    @Test
    fun `同一来源只选择一个具体课次时 不会输出该来源的其他课次`() {
        val events = factory.build(
            occurrences = listOf(occurrence("uid-a", "first", "来源 A"), occurrence("uid-a", "second", "来源 A")),
            selectedSourceOccurrences = setOf(SourceOccurrenceIdentity("uid-a", "second")),
            rules = emptyList(),
            manualEvents = emptyList(),
        )

        assertEquals(1, events.size)
        assertEquals("second", events.single().identity.instanceKey)
    }

    @Test
    fun `相同课次的来源重复只输出一次 不同课程同一时段仍分别输出`() {
        val events = factory.build(
            occurrences = listOf(
                occurrence("uid-a", "same", "课程 A"),
                occurrence("uid-a", "same", "课程 A"),
                occurrence("uid-b", "other", "课程 B"),
            ),
            selectedSourceOccurrences = setOf(SourceOccurrenceIdentity("uid-a", "same"), SourceOccurrenceIdentity("uid-b", "other")),
            rules = emptyList(),
            manualEvents = emptyList(),
        )

        assertEquals(2, events.size)
        assertEquals(setOf("uid-a", "uid-b"), events.map { it.identity.sourceUid }.toSet())
    }

    @Test
    fun `未选择来源时仅保留独立手动事项`() {
        val events = factory.build(
            occurrences = listOf(occurrence("uid-a", "key-a", "来源课程")),
            selectedSourceOccurrences = emptySet(),
            rules = emptyList(),
            manualEvents = listOf(ManualScheduleItem("manual-1", "手动测试", Instant.parse("2026-10-22T08:00:00Z"), Instant.parse("2026-10-22T09:00:00Z"), AttendanceState.MUST_ATTEND)),
        )

        assertEquals(1, events.size)
        assertEquals("manual:manual-1", events.single().identity.sourceUid)
    }

    @Test
    fun `已选活动快照不会被读取范围二次过滤`() {
        val events = factory.build(
            occurrences = listOf(
                occurrence("uid-a", "inside", "来源", "2026-10-20T08:00:00Z"),
                occurrence("uid-a", "outside", "来源", "2026-11-20T08:00:00Z"),
            ),
            selectedSourceOccurrences = setOf(SourceOccurrenceIdentity("uid-a", "inside"), SourceOccurrenceIdentity("uid-a", "outside")),
            rules = emptyList(),
            manualEvents = listOf(ManualScheduleItem("manual-1", "手动测试", Instant.parse("2026-11-22T08:00:00Z"), Instant.parse("2026-11-22T09:00:00Z"), AttendanceState.MUST_ATTEND)),
        )

        assertEquals(setOf("inside", "outside", "manual-1"), events.map { it.identity.instanceKey }.toSet())
    }

    @Test
    fun `用户修改后的最终时间不改变已选课次的写入资格`() {
        val source = occurrence("uid-a", "outside-source", "来源", "2026-10-31T08:00:00Z")
        val moved = factory.build(
            occurrences = listOf(source),
            selectedSourceOccurrences = setOf(source.identity),
            rules = emptyList(),
            manualEvents = emptyList(),
            overrides = mapOf("uid-a" to com.tequila.tumcalendarcontrol.domain.CourseLocalOverride(
                meetingTime = com.tequila.tumcalendarcontrol.domain.CourseMeetingTime(
                    com.tequila.tumcalendarcontrol.domain.CourseWeekday.SUNDAY, 9, 0, 10, 0,
                ),
            )),
        )

        assertEquals(1, moved.size)
        assertEquals(Instant.parse("2026-11-01T08:00:00Z"), moved.single().startsAt)
    }

    private fun occurrence(uid: String, key: String, title: String, start: String = "2026-10-01T08:00:00Z", cancelled: Boolean = false) = SourceCalendarOccurrence(
        sourceUid = uid,
        instanceKey = key,
        title = title,
        startsAt = Instant.parse(start),
        endsAt = Instant.parse(start).plusSeconds(3_600),
        isCancelled = cancelled,
        location = "合成地点",
    )
}
