/*
 * 职责：表示从 iCalendar 标准库展开后得到的一次来源日程及其稳定身份。
 * 输入／输出：输入来源 UID、原始实例键和时间范围；输出供规则和差异层消费的不可变实例。
 * 外部副作用：无。
 * 不负责：判断 Fixplatz、用户出席规则、持久化或写入任何目标日历。
 */
package com.tequila.tumcalendarcontrol.sync

import java.time.Instant

data class SourceCalendarOccurrence(
    val sourceUid: String,
    val instanceKey: String,
    val title: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val isCancelled: Boolean,
)

data class IcalImportResult(
    val occurrences: List<SourceCalendarOccurrence>,
    val skipped: List<IcalSkippedEvent>,
)

data class IcalSkippedEvent(
    val sourceUid: String?,
    val reason: IcalSkipReason,
)

enum class IcalSkipReason(val label: String) {
    MISSING_UID("缺少 UID"),
    MISSING_TIME("缺少起止时间"),
    NOT_CONFIRMED_FIXPLATZ("未确认 Fixplatz"),
}
