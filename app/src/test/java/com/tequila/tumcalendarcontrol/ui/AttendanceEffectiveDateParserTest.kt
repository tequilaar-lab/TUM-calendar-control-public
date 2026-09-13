/*
 * 职责：验证出席规则日期输入的字符规范化与严格日期校验。
 * 输入／输出：输入合成 ASCII／全角日期字符串；输出解析成功或失败断言。
 * 外部副作用：无。
 * 不负责：Compose 表单、规则保存、网络或真实日历验证。
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
