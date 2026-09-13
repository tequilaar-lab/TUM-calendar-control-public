/*
 * 职责：验证来源目录的视觉分组和筛选不改变 UID 身份。
 * 输入／输出：输入合成课程、实例、选择和日期窗口；断言显示组与 UID 成员。
 * 外部副作用：无。
 * 不负责：网络读取、真实日历或 Compose 截图。
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceCourse
import com.tequila.tumcalendarcontrol.sync.SourceOutputDateWindow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceCourseBrowserModelTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun `同标题课程只视觉分组 仍保留两个独立 UID`() {
        val courses = listOf(course("uid-a", "同名课程", "2026-10-01T08:00:00Z"), course("uid-b", "同名课程", "2026-10-02T08:00:00Z"))

        val groups = SourceCourseBrowserModel.groups(courses, occurrences(courses), setOf("uid-a"), "", SourceCourseFilter.ALL, SourceOutputDateWindow(), berlin)

        assertEquals(1, groups.size)
        assertEquals(setOf("uid-a", "uid-b"), groups.single().courses.map(SourceCourse::sourceUid).toSet())
        assertEquals(1, groups.single().selectedCount)
    }

    @Test
    fun `范围内筛选只显示相交 UID 查询仍可缩小结果`() {
        val courses = listOf(course("uid-a", "线性代数", "2026-10-01T08:00:00Z"), course("uid-b", "物理", "2026-12-01T08:00:00Z"))
        val window = SourceOutputDateWindow(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))

        val groups = SourceCourseBrowserModel.groups(courses, occurrences(courses), emptySet(), "线性", SourceCourseFilter.IN_OUTPUT_RANGE, window, berlin)

        assertEquals(1, groups.size)
        assertTrue(groups.single().courses.all { it.sourceUid == "uid-a" })
    }

    private fun occurrences(courses: List<SourceCourse>): List<SourceCalendarOccurrence> = courses.map {
        SourceCalendarOccurrence(it.sourceUid, "${it.sourceUid}:instance", it.title, it.firstStart, it.lastEnd, false, it.location)
    }

    private fun course(uid: String, title: String, start: String): SourceCourse {
        val startsAt = Instant.parse(start)
        return SourceCourse(uid, title, "合成地点", startsAt, startsAt.plusSeconds(3_600), "$uid:instance", 1, 0)
    }
}
