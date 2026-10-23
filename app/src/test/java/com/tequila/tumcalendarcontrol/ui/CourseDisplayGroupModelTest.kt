/*
 * Aufgabe: synthetische Regressionstests für CourseDisplayGroupModel.
 * Ein-/Ausgabe: künstliche Zustände und Fixtures -> JUnit-Prüfergebnisse.
 * Nebenwirkungen: nur lokaler Testprozess und gegebenenfalls Robolectric-Präferenzen.
 * Nicht zuständig: echte Konten, private Quellen, Geräte oder reale Kalenderausgaben.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.domain.Course
import com.tequila.tumcalendarcontrol.domain.displayTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CourseDisplayGroupModelTest {
    @Test
    fun `同名课程只形成一个视觉组且保留独立课程记录`() {
        val groups = CourseDisplayGroupModel.groups(
            listOf(
                course("a", "VHDL 设计", "周一 08:00–10:00", "A 教室"),
                course("b", "  vhdl   设计 ", "周四 14:00–16:00", "B 教室"),
                course("c", "嵌入式系统"),
            ),
        )

        assertEquals(2, groups.size)
        assertEquals(listOf("a", "b"), groups.first { it.displayTitle.contains("VHDL", ignoreCase = true) }.courses.map(Course::id))
    }

    /** Übersetzbare Beispielnamen bleiben dasselbe Objekt, sonst zeigte die deutsche Übersicht „课程 A“ statt „Kurs A“. */
    @Test
    fun `未改动的课名保留原字符串对象以便翻译`() {
        val sample = com.tequila.tumcalendarcontrol.domain.SampleCourses.all().first()

        val group = CourseDisplayGroupModel.groups(listOf(sample)).single()

        assertSame(sample.displayTitle, group.displayTitle)
    }

    @Test
    fun `展开一个课程组不会改变其它课程组`() {
        val initiallyExpanded = setOf("course-a")

        val expanded = initiallyExpanded.toggleCourseDisplayGroup("course-b")

        assertEquals(setOf("course-a", "course-b"), expanded)
        assertEquals(setOf("course-b"), expanded.toggleCourseDisplayGroup("course-a"))
    }

    @Test
    fun `同一课程的 VO UE PR 进入二级类型目录而不合并 UID`() {
        val groups = CourseDisplayGroupModel.groups(
            listOf(
                course("vo", "Messsystem- und Sensortechnik (VO)"),
                course("ue", "Messsystem- und Sensortechnik (UE)"),
                course("pr", "Messsystem- und Sensortechnik (PR)"),
                course("other", "Messsystem- und Sensortechnik"),
            ),
        )

        val group = groups.single()
        assertEquals("Messsystem- und Sensortechnik", group.displayTitle)
        assertEquals(listOf("PR", "UE", "VO", "其他课次"), group.teachingTypes.map { it.label })
        assertEquals(listOf("vo"), group.teachingTypes.single { it.label == "VO" }.courses.map(Course::id))
        assertEquals(listOf("other"), group.teachingTypes.single { it.label == "其他课次" }.courses.map(Course::id))
    }

    @Test
    fun `二级类型展开状态不会影响顶层课程状态`() {
        val expandedTypes = setOf("mess:vo").toggleCourseTeachingTypeGroup("mess:ue")

        assertEquals(setOf("mess:vo", "mess:ue"), expandedTypes)
        assertEquals(setOf("mess:ue"), expandedTypes.toggleCourseTeachingTypeGroup("mess:vo"))
    }

    private fun course(id: String, title: String, schedule: String = "周一 10:00–12:00", location: String = "合成地点") = Course(
        id = id,
        title = title,
        schedule = schedule,
        location = location,
        attendance = AttendanceState.CHOOSE_TO_ATTEND,
        isFavorite = false,
    )
}
