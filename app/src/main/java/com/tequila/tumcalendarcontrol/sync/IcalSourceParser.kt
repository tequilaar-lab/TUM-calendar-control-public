/*
 * 职责：通过 iCal4j 解析 RFC 5545 文本、展开指定窗口内的重复事件，并可选按 UID 筛选。
 * 输入／输出：输入 ICS 文本、时间窗口和可选 UID 清单；输出稳定实例与跳过原因。
 * 外部副作用：无；调用者负责以已授权方式取得文本，本类不访问网络或文件。
 * 不负责：推断 Fixplatz、手写 ICS 语法、保存规则或写入目标日历。
 */
package com.tequila.tumcalendarcontrol.sync

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Period
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Status
import java.io.StringReader
import java.time.Instant

class IcalSourceParser {
    /**
     * `null` UID 清单代表接受全部来源课程；非空清单只用于兼容旧的显式筛选场景。
     * `windowStart` 与 `windowEnd` 限制展开范围，避免无限 RRULE 占用资源。
     */
    fun parse(
        icsText: String,
        windowStart: Instant,
        windowEnd: Instant,
        allowedSourceUids: Set<String>? = null,
    ): IcalImportResult {
        require(windowEnd.isAfter(windowStart)) { "iCalendar 展开窗口必须有正长度。" }
        Ical4jRuntimeConfiguration.configureOfflineCache()
        val calendar = CalendarBuilder().build(StringReader(icsText))
        val candidates = mutableListOf<SourceCalendarOccurrence>()
        val skipped = mutableListOf<IcalSkippedEvent>()
        val overrides = mutableMapOf<String, SourceCalendarOccurrence>()

        calendar.components.filterIsInstance<VEvent>().forEach { event ->
            val uid = event.uid?.value
            if (uid.isNullOrBlank()) {
                skipped += IcalSkippedEvent(null, IcalSkipReason.MISSING_UID)
                return@forEach
            }
            if (allowedSourceUids != null && uid !in allowedSourceUids) {
                skipped += IcalSkippedEvent(uid, IcalSkipReason.NOT_CONFIRMED_FIXPLATZ)
                return@forEach
            }
            val recurrenceId = event.recurrenceId?.date?.toInstant()?.toString()
            val periods = event.calculateRecurrenceSet(
                Period(DateTime(windowStart.toEpochMilli()), DateTime(windowEnd.toEpochMilli())),
            )
            if (periods.isEmpty()) {
                skipped += IcalSkippedEvent(uid, IcalSkipReason.MISSING_TIME)
                return@forEach
            }
            periods.forEach { period ->
                val occurrence = period.toOccurrence(event, uid, recurrenceId)
                if (recurrenceId == null) candidates += occurrence else overrides["$uid|$recurrenceId"] = occurrence
            }
        }

        val merged = candidates.associateBy { "${it.sourceUid}|${it.instanceKey}" }.toMutableMap()
        overrides.forEach { (identity, occurrence) -> merged[identity] = occurrence }
        return IcalImportResult(
            occurrences = merged.values.sortedBy(SourceCalendarOccurrence::startsAt),
            skipped = skipped,
        )
    }

    private fun Period.toOccurrence(event: VEvent, uid: String, recurrenceId: String?): SourceCalendarOccurrence {
        val start = start.toInstant()
        val end = end.toInstant()
        return SourceCalendarOccurrence(
            sourceUid = uid,
            instanceKey = recurrenceId ?: start.toString(),
            title = event.summary?.value.orEmpty().ifBlank { "未命名来源日程" },
            location = event.location?.value.orEmpty(),
            startsAt = start,
            endsAt = end,
            isCancelled = event.status?.value.equals(Status.VEVENT_CANCELLED.value, ignoreCase = true),
        )
    }

}
