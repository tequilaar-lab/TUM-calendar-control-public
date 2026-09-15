/*
 * 职责：验证课程名称分组仅改变浏览结构，且不合并底层独立课程身份。
 * 输入／输出：输入合成 Course；输出分组与成员身份断言。
 * 外部副作用：无；不渲染 UI、不保存规则、不读取或写入日历。
 * 不负责：来源解析、实际搜索、导航动画或同步验证。
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.domain.Course
import org.junit.Assert.assertEquals
import org.junit.Test

class CourseDisplayGroupModelTest {
    @Test
    fun `同名课程只形成一个视觉组且保留独立课程记录`() {
        val groups = CourseDisplayGroupModel.groups(
            listOf(
                course("a", "VHDL 设计"),
                course("b", "  vhdl   设计 "),
                course("c", "嵌入式系统"),
            ),
        )

        assertEquals(2, groups.size)
        assertEquals(listOf("a", "b"), groups.first { it.displayTitle.contains("VHDL", ignoreCase = true) }.courses.map(Course::id))
    }

    private fun course(id: String, title: String) = Course(
        id = id,
        title = title,
        schedule = "周一 10:00–12:00",
        location = "合成地点",
        attendance = AttendanceState.CHOOSE_TO_ATTEND,
        isFavorite = false,
    )
}
