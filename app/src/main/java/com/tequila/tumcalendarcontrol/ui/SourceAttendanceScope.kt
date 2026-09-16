/*
 * 职责：把用户选择的锚点与范围解析为明确的已缓存课次集合。
 * 输入／输出：来源课次、选中身份和范围；输出独立课次，不改变 UID 或实例键。
 * 外部副作用：无。不负责日历去重、写入、联网或持久化。
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceOccurrenceIdentity
import java.time.ZoneId

internal object SourceAttendanceScope {
    /** 完整原始标题保留教学类型和组别；标题仅选取成员，绝不作为事件身份。 */
    fun resolve(
        occurrences: List<SourceCalendarOccurrence>,
        selection: Set<SourceOccurrenceIdentity>,
        scope: AttendanceEditScope,
        dateText: String,
    ): List<SourceCalendarOccurrence>? {
        val selected = occurrences.filter { it.identity in selection && !it.isCancelled }
        if (scope == AttendanceEditScope.ONLY_THIS_OCCURRENCE) return selected
        val date = if (scope == AttendanceEditScope.SPECIFIED_AND_FUTURE) {
            AttendanceEffectiveDateParser.parseOrNull(dateText) ?: return null
        } else null
        val anchors = selected.groupBy { it.title.trim() }.mapValues { (_, items) -> items.minOf { it.startsAt } }
        val explicitAnchors = selected.groupBy { it.sourceUid }.mapValues { (_, items) -> items.minOf { it.startsAt } }
        return occurrences.filter { item ->
            val anchor = anchors[item.title.trim()]
            !item.isCancelled && anchor != null && when (scope) {
                AttendanceEditScope.ALL_OCCURRENCES -> true
                AttendanceEditScope.CURRENT_AND_FUTURE -> item.startsAt >= (explicitAnchors[item.sourceUid] ?: anchor)
                AttendanceEditScope.SPECIFIED_AND_FUTURE -> !item.startsAt.atZone(ZoneId.of("Europe/Berlin")).toLocalDate().isBefore(date)
                AttendanceEditScope.ONLY_THIS_OCCURRENCE -> false
            }
        }
    }
}
