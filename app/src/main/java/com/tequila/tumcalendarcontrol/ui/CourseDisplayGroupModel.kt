/*
 * 职责：把同一显示名称的课程组织成纯视觉分组，供首页、搜索和收藏共用。
 * 输入／输出：输入独立 Course 列表；输出保留每条 Course 身份的名称分组。
 * 外部副作用：无；不保存分组、不修改课程、规则、收藏或日历。
 * 不负责：以标题作为同步身份、合并 UID／实例、筛选来源或处理用户点击。
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.domain.Course
import com.tequila.tumcalendarcontrol.domain.displayTitle
import java.util.Locale

/** 标题只是浏览容器；成员仍可独立打开、收藏和写入。 */
internal data class CourseDisplayGroup(
    val key: String,
    val displayTitle: String,
    val courses: List<Course>,
)

internal object CourseDisplayGroupModel {
    /** 空白与大小写差异不会拆散同一视觉课程名；排序仅改善浏览，不参与任何业务规则。 */
    fun groups(courses: List<Course>): List<CourseDisplayGroup> = courses
        .groupBy { normalizedKey(it.displayTitle) }
        .map { (key, members) ->
            CourseDisplayGroup(
                key = key,
                displayTitle = members.first().displayTitle.trim().ifBlank { "未命名课程" },
                courses = members.sortedBy(Course::id),
            )
        }
        .sortedWith(compareBy<CourseDisplayGroup> { it.displayTitle.lowercase(Locale.ROOT) }.thenBy(CourseDisplayGroup::key))

    private fun normalizedKey(title: String): String =
        title.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT).ifBlank { "(untitled)" }
}

/** 展开状态只保存视觉组键；切换一个组不会改变其它组或课程成员。 */
internal fun Set<String>.toggleCourseDisplayGroup(key: String): Set<String> =
    if (key in this) this - key else this + key
