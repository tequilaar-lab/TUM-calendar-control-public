/*
 * 职责：验证来源课程投影的 UID 身份、显示字段与本机偏好保留规则。
 * 输入／输出：输入合成来源目录、实例、规则与旧课程；输出纯内存断言。
 * 外部副作用：无。
 * 不负责：真实 iCal、Compose 渲染、权限、Google 或本机日历写入。
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.domain.Course
import com.tequila.tumcalendarcontrol.domain.CourseLocalOverride
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceCourse
import com.tequila.tumcalendarcontrol.sync.SyncRule
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceCourseProjectionTest {
    @Test
    fun `投影以 UID 保留收藏 覆盖与三态规则`() {
        val projected = SourceCourseProjection.courses(
            sourceCourses = listOf(sourceCourse()),
            occurrences = listOf(occurrence()),
            rules = listOf(SyncRule("uid-a", true, AttendanceState.MUST_ATTEND)),
            previousCourses = listOf(
                Course(
                    id = "uid-a",
                    title = "旧标题",
                    schedule = "旧时间",
                    location = "旧地点",
                    attendance = AttendanceState.CHOOSE_TO_ATTEND,
                    isFavorite = true,
                    localOverride = CourseLocalOverride(title = "本机名称"),
                ),
            ),
        )

        assertEquals("uid-a", projected.single().id)
        assertEquals("来源标题", projected.single().title)
        assertEquals("本机名称", projected.single().localOverride.title)
        assertTrue(projected.single().isFavorite)
        assertEquals(AttendanceState.MUST_ATTEND, projected.single().attendance)
        assertTrue(projected.single().schedule.contains("周一"))
    }

    @Test
    fun `投影不会保留不再存在于来源的课程`() {
        val projected = SourceCourseProjection.courses(
            sourceCourses = listOf(sourceCourse()),
            occurrences = listOf(occurrence()),
            rules = emptyList(),
            previousCourses = listOf(
                Course("removed", "已删除", "", "", AttendanceState.MUST_ATTEND, true),
            ),
        )

        assertEquals(listOf("uid-a"), projected.map(Course::id))
        assertFalse(projected.single().isFavorite)
        assertEquals(AttendanceState.CHOOSE_TO_ATTEND, projected.single().attendance)
    }

    private fun sourceCourse() = SourceCourse(
        sourceUid = "uid-a",
        title = "来源标题",
        location = "来源地点",
        firstStart = Instant.parse("2026-09-14T08:00:00Z"),
        lastEnd = Instant.parse("2026-09-14T10:00:00Z"),
        representativeInstanceKey = "instance-a",
        occurrenceCount = 1,
        cancelledOccurrenceCount = 0,
    )

    private fun occurrence() = SourceCalendarOccurrence(
        sourceUid = "uid-a",
        instanceKey = "instance-a",
        title = "来源标题",
        startsAt = Instant.parse("2026-09-14T08:00:00Z"),
        endsAt = Instant.parse("2026-09-14T10:00:00Z"),
        isCancelled = false,
        location = "来源地点",
    )
}
