/*
 * Aufgabe: synthetische Regressionstests für AttendanceEffectiveDateParser.
 * Ein-/Ausgabe: künstliche Zustände und Fixtures -> JUnit-Prüfergebnisse.
 * Nebenwirkungen: nur lokaler Testprozess und gegebenenfalls Robolectric-Präferenzen.
 * Nicht zuständig: echte Konten, private Quellen, Geräte oder reale Kalenderausgaben.
 */
package com.tequila.tumcalendarcontrol.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttendanceEffectiveDateParserTest {
    @Test
    fun `常见全角数字与横线会规范化为同一日期`() {
        val expected = LocalDate.parse("2026-09-13")

        assertEquals(expected, AttendanceEffectiveDateParser.parseOrNull("2026-09-13"))
        assertEquals(expected, AttendanceEffectiveDateParser.parseOrNull("2026－09－13"))
        assertEquals(expected, AttendanceEffectiveDateParser.parseOrNull("２０２６—０９—１３"))
    }

    @Test
    fun `非法日期与非标准结构仍被拒绝`() {
        assertNull(AttendanceEffectiveDateParser.parseOrNull("2026-02-30"))
        assertNull(AttendanceEffectiveDateParser.parseOrNull("13-09-2026"))
    }
}
