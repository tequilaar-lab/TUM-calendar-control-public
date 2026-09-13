/*
 * 职责：把同一来源 UID 的重复实例聚合为仅在本次运行内可见的课程目录。
 * 输入／输出：输入稳定来源实例；输出按 UID 归类的标题、地点、时间范围与代表实例。
 * 外部副作用：无；调用者决定是否仅在内存显示，不能据此写入日历。
 * 不负责：Fixplatz 判断、规则持久化、网络读取、标题去重或日历输出。
 */
package com.tequila.tumcalendarcontrol.sync

import java.time.Instant

data class SourceCourse(
    val sourceUid: String,
    val title: String,
    val location: String,
    val firstStart: Instant,
    val lastEnd: Instant,
    val representativeInstanceKey: String,
    val occurrenceCount: Int,
    val cancelledOccurrenceCount: Int,
)

object SourceCourseCatalog {
    /** UID 是唯一归属键；标题只供本机展示，绝不参与课程合并或规则身份。 */
    fun from(occurrences: List<SourceCalendarOccurrence>): List<SourceCourse> = occurrences
        .groupBy(SourceCalendarOccurrence::sourceUid)
        .map { (sourceUid, items) ->
            val ordered = items.sortedBy(SourceCalendarOccurrence::startsAt)
            val representative = ordered.firstOrNull { !it.isCancelled } ?: ordered.first()
            SourceCourse(
                sourceUid = sourceUid,
                title = representative.title,
                location = representative.location,
                firstStart = ordered.first().startsAt,
                lastEnd = ordered.maxOf(SourceCalendarOccurrence::endsAt),
                representativeInstanceKey = representative.instanceKey,
                occurrenceCount = ordered.size,
                cancelledOccurrenceCount = ordered.count(SourceCalendarOccurrence::isCancelled),
            )
        }
        .sortedWith(compareBy<SourceCourse> { it.firstStart }.thenBy { it.sourceUid })
}
