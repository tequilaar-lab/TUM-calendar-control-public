/*
 * Aufgabe: synthetische Regressionstests für CourseMeetingTime.
 * Ein-/Ausgabe: künstliche Zustände und Fixtures -> JUnit-Prüfergebnisse.
 * Nebenwirkungen: nur lokaler Testprozess und gegebenenfalls Robolectric-Präferenzen.
 * Nicht zuständig: echte Konten, private Quellen, Geräte oder reale Kalenderausgaben.
 */
package com.tequila.tumcalendarcontrol.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseMeetingTimeTest {
    @Test fun `结构化时间显示星期和补零时分`() {
        val time = CourseMeetingTime(CourseWeekday.WEDNESDAY, 8, 5, 10, 0)
        assertEquals("周三 08:05–10:00", time.display())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `结束不晚于开始会被拒绝`() {
        CourseMeetingTime(CourseWeekday.MONDAY, 10, 0, 10, 0)
    }

    @Test fun `本机结构化时间和标题优先于来源显示`() {
        val course = Course("synthetic", "来源课程", "周二 10:00–12:00", "来源地点", AttendanceState.MUST_ATTEND, false,
            localOverride = CourseLocalOverride(title = "本机课程", meetingTime = CourseMeetingTime(CourseWeekday.FRIDAY, 9, 0, 11, 30)))
        assertEquals("本机课程", course.displayTitle)
        assertEquals("周五 09:00–11:30", course.displaySchedule)
    }
}
