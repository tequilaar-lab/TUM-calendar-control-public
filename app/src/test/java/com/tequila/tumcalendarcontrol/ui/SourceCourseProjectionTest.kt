/*
 * Aufgabe: synthetische Regressionstests für SourceCourseProjection.
 * Ein-/Ausgabe: künstliche Zustände und Fixtures -> JUnit-Prüfergebnisse.
 * Nebenwirkungen: nur lokaler Testprozess und gegebenenfalls Robolectric-Präferenzen.
 * Nicht zuständig: echte Konten, private Quellen, Geräte oder reale Kalenderausgaben.
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

    @Test
    fun `新进程读取来源时按 UID 恢复已保存的收藏和本机覆盖`() {
        val projected = SourceCourseProjection.courses(
            sourceCourses = listOf(sourceCourse()),
            occurrences = listOf(occurrence()),
            rules = emptyList(),
            previousCourses = emptyList(),
            savedPresentations = mapOf(
                "uid-a" to CoursePresentation(
                    isFavorite = true,
                    localOverride = CourseLocalOverride(title = "重启后本机名称"),
                ),
            ),
        )

        assertTrue(projected.single().isFavorite)
        assertEquals("重启后本机名称", projected.single().localOverride.title)
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
