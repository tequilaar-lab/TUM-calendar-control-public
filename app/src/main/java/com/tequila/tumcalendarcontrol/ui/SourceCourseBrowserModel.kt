/*
 * 职责：把仅本次运行可见的来源课程变为可搜索、可筛选的视觉分组。
 * 输入／输出：输入 UID 课程、实例、选择集和可选输出窗口；输出只供 Compose 浏览器显示的分组。
 * 外部副作用：无；不持久化标题、地点、搜索词或来源内容。
 * 不负责：课程身份合并、规则编辑、iCal 读取或目标日历写入。
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceCourse
import com.tequila.tumcalendarcontrol.sync.SourceOutputDateWindow
import java.time.Instant
import java.time.ZoneId

/** 过滤条件只影响当前目录显示，不能改变 UID 选择或同步候选。 */
enum class SourceCourseFilter(val label: String) {
    ALL("全部"),
    SELECTED("已选择"),
    IN_OUTPUT_RANGE("范围内有日程"),
}

/** 同名课程的视觉容器；成员 UID 仍必须逐个选择与同步。 */
data class SourceCourseDisplayGroup(
    val displayTitle: String,
    val courses: List<SourceCourse>,
    val selectedCount: Int,
    val inOutputRangeCount: Int,
    val totalOccurrenceCount: Int,
    val earliestStart: Instant,
) {
    val sourceUidCount: Int get() = courses.size
}

object SourceCourseBrowserModel {
    /**
     * 标题仅作为视觉分组键；故意不用于任何规则、候选或持久化身份。
     * 空白归一化让同一可见标题不会被无意义空格拆成多个分组。
     */
    fun groups(
        courses: List<SourceCourse>,
        occurrences: List<SourceCalendarOccurrence>,
        selectedSourceUids: Set<String>,
        query: String,
        filter: SourceCourseFilter,
        outputWindow: SourceOutputDateWindow,
        zoneId: ZoneId,
    ): List<SourceCourseDisplayGroup> {
        val occurrencesByUid = occurrences.groupBy(SourceCalendarOccurrence::sourceUid)
        val searchable = query.trim()
        fun isInWindow(course: SourceCourse): Boolean = occurrencesByUid[course.sourceUid].orEmpty()
            .any { outputWindow.includes(it.startsAt, it.endsAt, zoneId) }
        fun matches(course: SourceCourse): Boolean {
            val queryMatches = searchable.isBlank() || course.title.contains(searchable, ignoreCase = true) ||
                course.location.contains(searchable, ignoreCase = true)
            val filterMatches = when (filter) {
                SourceCourseFilter.ALL -> true
                SourceCourseFilter.SELECTED -> course.sourceUid in selectedSourceUids
                SourceCourseFilter.IN_OUTPUT_RANGE -> isInWindow(course)
            }
            return queryMatches && filterMatches
        }
        return courses.asSequence()
            .filter(::matches)
            .groupBy { normalizedDisplayKey(it.title) }
            .map { (_, members) ->
                val ordered = members.sortedWith(compareBy(SourceCourse::firstStart).thenBy(SourceCourse::sourceUid))
                SourceCourseDisplayGroup(
                    displayTitle = ordered.first().title,
                    courses = ordered,
                    selectedCount = ordered.count { it.sourceUid in selectedSourceUids },
                    inOutputRangeCount = ordered.count(::isInWindow),
                    totalOccurrenceCount = ordered.sumOf(SourceCourse::occurrenceCount),
                    earliestStart = ordered.minOf(SourceCourse::firstStart),
                )
            }
            .sortedWith(compareBy(SourceCourseDisplayGroup::earliestStart).thenBy(SourceCourseDisplayGroup::displayTitle))
            .toList()
    }

    private fun normalizedDisplayKey(title: String): String = title.trim().replace(Regex("\\s+"), " ").lowercase()
}
