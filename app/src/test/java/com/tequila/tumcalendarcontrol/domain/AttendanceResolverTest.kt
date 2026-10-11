/*
 * Aufgabe: synthetische Regressionstests für AttendanceResolver.
 * Ein-/Ausgabe: künstliche Zustände und Fixtures -> JUnit-Prüfergebnisse.
 * Nebenwirkungen: nur lokaler Testprozess und gegebenenfalls Robolectric-Präferenzen.
 * Nicht zuständig: echte Konten, private Quellen, Geräte oder reale Kalenderausgaben.
 */
package com.tequila.tumcalendarcontrol.domain

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttendanceResolverTest {
    @Test
    fun `未确认 Fixplatz 的课程不导入`() {
        val rule = rule(hasFixedPlus = false)

        assertNull(AttendanceResolver.resolve(rule, occurrence("2026-10-05T10:00")))
    }

    @Test
    fun `确认课程保留绿色或蓝色默认分类`() {
        assertEquals(
            AttendanceState.MUST_ATTEND,
            AttendanceResolver.resolve(rule(defaultState = AttendanceState.MUST_ATTEND), occurrence("2026-10-05T10:00")),
        )
        assertEquals(
            AttendanceState.CHOOSE_TO_ATTEND,
            AttendanceResolver.resolve(rule(defaultState = AttendanceState.CHOOSE_TO_ATTEND), occurrence("2026-10-05T10:00")),
        )
    }

    @Test
    fun `单次人工覆盖优先于默认规则`() {
        val rule = rule(
            defaultState = AttendanceState.CHOOSE_TO_ATTEND,
            instanceOverrides = mapOf("2026-10-05T10:00" to AttendanceState.MUST_ATTEND),
        )

        assertEquals(
            AttendanceState.MUST_ATTEND,
            AttendanceResolver.resolve(rule, occurrence("2026-10-05T10:00")),
        )
    }

    @Test
    fun `灰色截点不追溯修改历史实例`() {
        val rule = rule(
            defaultState = AttendanceState.MUST_ATTEND,
            stopAttendingFrom = LocalDate.of(2026, 10, 19),
        )

        assertEquals(
            AttendanceState.MUST_ATTEND,
            AttendanceResolver.resolve(rule, occurrence("2026-10-12T10:00")),
        )
        assertEquals(
            AttendanceState.STOP_ATTENDING,
            AttendanceResolver.resolve(rule, occurrence("2026-10-19T10:00")),
        )
    }

    private fun rule(
        hasFixedPlus: Boolean = true,
        defaultState: AttendanceState = AttendanceState.CHOOSE_TO_ATTEND,
        stopAttendingFrom: LocalDate? = null,
        instanceOverrides: Map<String, AttendanceState> = emptyMap(),
    ) = CourseRule(
        sourceUid = "synthetic-uid",
        hasFixedPlus = hasFixedPlus,
        defaultState = defaultState,
        stopAttendingFrom = stopAttendingFrom,
        instanceOverrides = instanceOverrides,
    )

    private fun occurrence(instanceKey: String) = CourseOccurrence(
        sourceUid = "synthetic-uid",
        instanceKey = instanceKey,
        title = "synthetic title",
        start = LocalDateTime.parse(instanceKey),
    )
}
