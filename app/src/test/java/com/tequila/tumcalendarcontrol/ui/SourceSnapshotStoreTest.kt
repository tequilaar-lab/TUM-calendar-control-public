/*
 * 职责：验证来源快照的私有编码、旧／新选择和实例级差异说明。
 * 输入／输出：输入合成来源快照；输出纯内存断言。
 * 外部副作用：无；不使用真实订阅、账户、日历或 Android 偏好。
 * 不负责：iCal 解析、Compose 渲染、Google 授权或任何实际日历写入。
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.sync.IcalReadOnlyPreview
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceCourse
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSnapshotStoreTest {
    @Test
    fun `快照编码恢复 UID 实例键和展示字段且不要求网络`() {
        val original = SavedSourceSnapshots(latest = snapshot("uid-a", "synthetic-a", "合成课程 (VO)"))

        val restored = SourceSnapshotCodec.decode(SourceSnapshotCodec.encode(original))

        assertEquals("uid-a", restored.active?.occurrences?.single()?.sourceUid)
        assertEquals("synthetic-a", restored.active?.occurrences?.single()?.instanceKey)
        assertEquals("合成课程 (VO)", restored.active?.occurrences?.single()?.title)
    }

    @Test
    fun `切换较早快照会恢复其课程目录但不会合并身份`() {
        val state = CalendarControlState(
            initialSourceSnapshots = SavedSourceSnapshots(
                latest = snapshot("uid-new", "new-instance", "新版课程 (UE)"),
                previous = snapshot("uid-old", "old-instance", "旧版课程 (VO)"),
            ),
        )

        assertEquals(listOf("uid-new"), state.displayedCourses.map { it.id })
        state.selectSourceSnapshot(SourceSnapshotSlot.PREVIOUS)

        assertEquals(SourceSnapshotSlot.PREVIOUS, state.activeSourceSnapshotSlot)
        assertEquals(listOf("uid-old"), state.displayedCourses.map { it.id })
        assertEquals("old-instance", state.currentSourceOccurrences.single().instanceKey)
    }

    @Test
    fun `差异以 UID 加实例键判断且说明同名与新名称课程`() {
        val previous = SavedSourceSnapshot(
            capturedAt = Instant.parse("2026-09-16T00:00:00Z"),
            sourceEventCount = 2,
            skippedByReason = emptyMap(),
            occurrences = listOf(
                occurrence("uid-a", "first", "系统课程 (VO)", "2026-10-01T08:00:00Z"),
                occurrence("uid-b", "old", "旧课程 (UE)", "2026-10-02T08:00:00Z"),
            ),
        )
        val latest = SavedSourceSnapshot(
            capturedAt = Instant.parse("2026-09-17T00:00:00Z"),
            sourceEventCount = 3,
            skippedByReason = emptyMap(),
            occurrences = listOf(
                occurrence("uid-a", "first", "系统课程 (VO)", "2026-10-01T09:00:00Z"),
                occurrence("uid-c", "same-title", "系统课程 (PR)", "2026-10-03T08:00:00Z"),
                occurrence("uid-d", "new-title", "全新课程 (VO)", "2026-10-04T08:00:00Z"),
            ),
        )

        val difference = SourceSnapshotDifferenceModel.compare(previous, latest)

        assertEquals(2, difference.addedOccurrenceCount)
        assertEquals(1, difference.removedOccurrenceCount)
        assertEquals(1, difference.changedOccurrenceCount)
        assertEquals(1, difference.sameTitleNewCourseCount)
        assertEquals(1, difference.newTitleCourseCount)
        assertFalse(difference.isEmpty)
        assertTrue(difference.summary().contains("同名课程 1 门"))
    }

    private fun snapshot(uid: String, instanceKey: String, title: String): SavedSourceSnapshot = SavedSourceSnapshot.from(
        IcalReadOnlyPreview(
            sourceEventCount = 1,
            manageableOccurrenceCount = 1,
            skippedByReason = emptyMap(),
            firstManageableStart = Instant.parse("2026-10-01T08:00:00Z"),
            lastManageableEnd = Instant.parse("2026-10-01T10:00:00Z"),
            courses = listOf(SourceCourse(uid, title, "合成地点", Instant.parse("2026-10-01T08:00:00Z"), Instant.parse("2026-10-01T10:00:00Z"), instanceKey, 1, 0)),
            occurrences = listOf(occurrence(uid, instanceKey, title, "2026-10-01T08:00:00Z")),
        ),
        capturedAt = Instant.parse("2026-09-16T00:00:00Z"),
    )

    private fun occurrence(uid: String, instanceKey: String, title: String, start: String): SourceCalendarOccurrence {
        val startsAt = Instant.parse(start)
        return SourceCalendarOccurrence(uid, instanceKey, title, startsAt, startsAt.plusSeconds(3_600), false, "合成地点")
    }
}
