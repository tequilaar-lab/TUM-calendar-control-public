/*
 * 职责：验证 iCal 来源输出日期窗口的解析、边界和跨日相交语义。
 * 输入／输出：输入合成日期文本与时间实例；输出解析或保留断言。
 * 外部副作用：无。
 * 不负责：网络、课程选择、日历写入或真实日程验证。
 */
package com.tequila.tumcalendarcontrol.sync

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceOutputDateWindowTest {
    private val zone = ZoneId.of("Europe/Berlin")

    @Test
    fun `空边界与全角输入会得到预期窗口`() {
        assertEquals(SourceOutputDateWindow(), SourceOutputDateWindow.parseOrNull("", ""))
        assertEquals(
            SourceOutputDateWindow(LocalDate.parse("2026-09-13"), LocalDate.parse("2026-10-01")),
            SourceOutputDateWindow.parseOrNull("２０２６－０９－１３", "2026—10—01"),
        )
        assertNull(SourceOutputDateWindow.parseOrNull("2026-10-02", "2026-10-01"))
    }

    @Test
    fun `窗口包含边界日及相交的跨日实例`() {
        val window = SourceOutputDateWindow(LocalDate.parse("2026-09-13"), LocalDate.parse("2026-09-13"))

        assertTrue(window.includes(Instant.parse("2026-09-12T22:30:00Z"), Instant.parse("2026-09-13T01:00:00Z"), zone))
        assertTrue(window.includes(Instant.parse("2026-09-13T20:00:00Z"), Instant.parse("2026-09-13T21:00:00Z"), zone))
        assertFalse(window.includes(Instant.parse("2026-09-11T20:00:00Z"), Instant.parse("2026-09-12T20:00:00Z"), zone))
        assertFalse(window.includes(Instant.parse("2026-09-14T00:00:00Z"), Instant.parse("2026-09-14T01:00:00Z"), zone))
    }
}
