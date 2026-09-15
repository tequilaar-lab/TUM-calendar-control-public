/*
 * 职责：把仅本次运行可见的来源课程变为可搜索、可筛选的视觉分组。
 * 输入／输出：输入 UID 课程、实例、选择集和可选输出窗口；输出只供 Compose 浏览器显示的分组。
 * 外部副作用：无；不持久化标题、地点、搜索词或来源内容。
 * 不负责：课程身份合并、规则编辑、iCal 读取或目标日历写入。
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceOccurrenceIdentity
import com.tequila.tumcalendarcontrol.sync.SourceOutputDateWindow
import java.time.Instant
import java.time.ZoneId

/** 过滤条件只影响当前目录显示，不能改变 UID 选择或同步候选。 */
enum class SourceCourseFilter(val label: String) {
    ALL("全部"),
    SELECTED("已选择"),
    IN_OUTPUT_RANGE("范围内有日程"),
}

/** 同名课程的视觉容器；成员仍按 UID + 实例键逐个选择与同步。 */
data class SourceCourseDisplayGroup(
    val displayTitle: String,
    val occurrences: List<SourceCalendarOccurrence>,
    val selectedOccurrenceCount: Int,
    val inOutputRangeOccurrenceCount: Int,
    val earliestStart: Instant,
) {
    val sourceUidCount: Int get() = occurrences.map(SourceCalendarOccurrence::sourceUid).distinct().size
    val totalOccurrenceCount: Int get() = occurrences.size
}

object SourceCourseBrowserModel {
    /**
     * 标题仅作为视觉分组键；故意不用于任何规则、候选或持久化身份。
     * 空白归一化让同一可见标题不会被无意义空格拆成多个分组。
     */
    fun groups(
        occurrences: List<SourceCalendarOccurrence>,
        selectedOccurrences: Set<SourceOccurrenceIdentity>,
        query: String,
        filter: SourceCourseFilter,
        outputWindow: SourceOutputDateWindow,
        zoneId: ZoneId,
    ): List<SourceCourseDisplayGroup> {
        val searchable = query.trim()
        fun isInWindow(occurrence: SourceCalendarOccurrence): Boolean = outputWindow.includes(occurrence.startsAt, occurrence.endsAt, zoneId)
        fun matches(occurrence: SourceCalendarOccurrence): Boolean {
            val queryMatches = searchable.isBlank() || occurrence.title.contains(searchable, ignoreCase = true) ||
                occurrence.location.contains(searchable, ignoreCase = true)
            val filterMatches = when (filter) {
                SourceCourseFilter.ALL -> true
                SourceCourseFilter.SELECTED -> occurrence.identity in selectedOccurrences
                SourceCourseFilter.IN_OUTPUT_RANGE -> isInWindow(occurrence)
            }
            return queryMatches && filterMatches
        }
        return occurrences.asSequence()
            .filterNot(SourceCalendarOccurrence::isCancelled)
            .filter(::matches)
            .groupBy { normalizedDisplayKey(it.title) }
            .map { (_, members) ->
                val ordered = members.sortedWith(compareBy(SourceCalendarOccurrence::startsAt).thenBy(SourceCalendarOccurrence::sourceUid).thenBy(SourceCalendarOccurrence::instanceKey))
                SourceCourseDisplayGroup(
                    displayTitle = ordered.first().title,
                    occurrences = ordered,
                    selectedOccurrenceCount = ordered.count { it.identity in selectedOccurrences },
                    inOutputRangeOccurrenceCount = ordered.count(::isInWindow),
                    earliestStart = ordered.minOf(SourceCalendarOccurrence::startsAt),
                )
            }
            .sortedWith(compareBy(SourceCourseDisplayGroup::earliestStart).thenBy(SourceCourseDisplayGroup::displayTitle))
            .toList()
    }

    private fun normalizedDisplayKey(title: String): String = title.trim().replace(Regex("\\s+"), " ").lowercase()
}
