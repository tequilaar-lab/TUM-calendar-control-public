/*
 * 职责：验证手动事项更新与删除只按应用生成 ID 工作。
 * 输入／输出：输入合成事项；输出确定性的项目列表断言。
 * 外部副作用：无。
 * 不负责：页面输入、来源数据或任何日历输出。
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualScheduleEditorTest {
    private val editor = ManualScheduleEditor()

    @Test
    fun `相同 ID 替换 不同同名事项并存 删除只影响指定 ID`() {
        val first = item("one", "同名事项", "2026-10-01T08:00:00Z")
        val sameTitle = item("two", "同名事项", "2026-10-02T08:00:00Z")
        val updated = editor.upsert(editor.upsert(listOf(first), sameTitle), first.copy(attendanceState = AttendanceState.MUST_ATTEND))

        assertEquals(2, updated.size)
        assertEquals(AttendanceState.MUST_ATTEND, updated.single { it.id == "one" }.attendanceState)
        assertEquals(listOf("one"), editor.remove(updated, "two").map { it.id })
    }

    private fun item(id: String, title: String, start: String) = ManualScheduleItem(id, title, Instant.parse(start), Instant.parse(start).plusSeconds(3_600), AttendanceState.CHOOSE_TO_ATTEND)
}
