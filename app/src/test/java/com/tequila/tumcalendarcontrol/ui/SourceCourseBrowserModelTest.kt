/*
 * 职责：验证来源目录的视觉分组和筛选不改变“UID + 实例键”的课次身份。
 * 输入／输出：输入合成课次、选择和日期窗口；断言显示组与课次成员。
 * 外部副作用：无。
 * 不负责：网络读取、真实日历或 Compose 截图。
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceOccurrenceIdentity
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
    fun `同标题课次只视觉分组 仍保留每个独立实例`() {
        val occurrences = listOf(
            occurrence("uid-a", "first", "同名课程", "2026-10-01T08:00:00Z"),
            occurrence("uid-b", "second", "同名课程", "2026-10-02T08:00:00Z"),
        )

        val groups = SourceCourseBrowserModel.groups(
            occurrences = occurrences,
            selectedOccurrences = setOf(SourceOccurrenceIdentity("uid-a", "first")),
            query = "",
            filter = SourceCourseFilter.ALL,
            outputWindow = SourceOutputDateWindow(),
            zoneId = berlin,
        )

        assertEquals(1, groups.size)
        assertEquals(setOf("uid-a", "uid-b"), groups.single().occurrences.map(SourceCalendarOccurrence::sourceUid).toSet())
        assertEquals(1, groups.single().selectedOccurrenceCount)
    }

    @Test
    fun `范围内筛选只显示相交课次 查询仍可缩小结果`() {
        val occurrences = listOf(
            occurrence("uid-a", "inside", "线性代数", "2026-10-01T08:00:00Z"),
            occurrence("uid-b", "outside", "物理", "2026-12-01T08:00:00Z"),
        )
        val window = SourceOutputDateWindow(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))

        val groups = SourceCourseBrowserModel.groups(
            occurrences = occurrences,
            selectedOccurrences = emptySet(),
            query = "线性",
            filter = SourceCourseFilter.IN_OUTPUT_RANGE,
            outputWindow = window,
            zoneId = berlin,
        )

        assertEquals(1, groups.size)
        assertTrue(groups.single().occurrences.all { it.sourceUid == "uid-a" })
    }

    private fun occurrence(uid: String, key: String, title: String, start: String): SourceCalendarOccurrence {
        val startsAt = Instant.parse(start)
        return SourceCalendarOccurrence(uid, key, title, startsAt, startsAt.plusSeconds(3_600), false, "合成地点")
    }
}
