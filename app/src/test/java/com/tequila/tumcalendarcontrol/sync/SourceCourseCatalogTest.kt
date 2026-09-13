/*
 * 职责：验证来源实例仅按 UID 聚合为课程目录。
 * 输入／输出：输入合成实例；输出课程聚合和代表实例断言。
 * 外部副作用：无。
 * 不负责：iCal 解析、规则持久化、界面或日历写入。
 */
package com.tequila.tumcalendarcontrol.sync

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceCourseCatalogTest {
    @Test
    fun `同标题不同 UID 不合并 同 UID 重复实例合并`() {
        val courses = SourceCourseCatalog.from(
            listOf(
                occurrence("uid-a", "a-1", "同名课程", "2026-09-14T08:00:00Z"),
                occurrence("uid-a", "a-2", "更名后的标题", "2026-09-21T08:00:00Z", cancelled = true),
                occurrence("uid-b", "b-1", "同名课程", "2026-09-15T08:00:00Z"),
            ),
        )

        assertEquals(2, courses.size)
        val first = courses.first { it.sourceUid == "uid-a" }
        assertEquals(2, first.occurrenceCount)
        assertEquals(1, first.cancelledOccurrenceCount)
        assertEquals("a-1", first.representativeInstanceKey)
        assertTrue(courses.any { it.sourceUid == "uid-b" && it.occurrenceCount == 1 })
    }

    private fun occurrence(uid: String, key: String, title: String, start: String, cancelled: Boolean = false) = SourceCalendarOccurrence(
        sourceUid = uid,
        instanceKey = key,
        title = title,
        startsAt = Instant.parse(start),
        endsAt = Instant.parse(start).plusSeconds(3_600),
        isCancelled = cancelled,
        location = "合成地点",
    )
}
