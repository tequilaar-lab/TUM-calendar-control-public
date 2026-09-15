/*
 * 职责：表示从 iCalendar 标准库展开后得到的一次来源日程及其稳定身份。
 * 输入／输出：输入来源 UID、原始实例键和时间范围；输出供规则和差异层消费的不可变实例。
 * 外部副作用：无。
 * 不负责：判断 Fixplatz、用户出席规则、持久化或写入任何目标日历。
 */
package com.tequila.tumcalendarcontrol.sync

import java.time.Instant

/**
 * 职责：给一次来源课次提供稳定、不可猜测的选择身份。
 * 输入／输出：输入 iCal 展开的 UID 与原始实例键；输出可用于 UI 选择、候选过滤和单实例规则的值对象。
 * 外部副作用：无；不包含标题、地点或账户资料。
 * 不负责：按标题合并课程、iCal 解析、规则保存或目标日历写入。
 */
data class SourceOccurrenceIdentity(
    val sourceUid: String,
    val instanceKey: String,
)

data class SourceCalendarOccurrence(
    val sourceUid: String,
    val instanceKey: String,
    val title: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val isCancelled: Boolean,
    val location: String = "",
) {
    /** 选择与同步都必须同时使用 UID 和原始实例键，避免同一重复课程的其他课次被误选。 */
    val identity: SourceOccurrenceIdentity get() = SourceOccurrenceIdentity(sourceUid, instanceKey)
}

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
