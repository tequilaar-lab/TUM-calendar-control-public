/*
 * 职责：对任意来源实例应用用户选择的三态规则、未来截点与单实例覆盖。
 * 输入／输出：输入来源实例与规则；输出目标日历所需状态，或空值表示不应输出。
 * 外部副作用：无。
 * 不负责：iCalendar 解析、Fixplatz 推断、规则持久化、差异计算或写入任何日历。
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.LocalDate
import java.time.ZoneId

data class SyncRule(
    val sourceUid: String,
    /** 历史字段，仅为兼容既有私有规则资料；不再作为导入或输出门槛。 */
    val hasConfirmedFixplatz: Boolean,
    val defaultState: AttendanceState,
    val stopAttendingFrom: LocalDate? = null,
    /** 日期生效的规则时间线支持绿／蓝／灰；晚于默认规则、早于单实例例外。 */
    val futureStateOverrides: Map<LocalDate, AttendanceState> = emptyMap(),
    val instanceOverrides: Map<String, AttendanceState> = emptyMap(),
    val futureInstantOverrides: Map<java.time.Instant, AttendanceState> = emptyMap(),
)

object SyncRuleResolver {
    /** 单实例覆盖优先；灰色截点仅影响当天和未来实例，取消来源事件不会变成可输出事件。 */
    fun resolve(rule: SyncRule, occurrence: SourceCalendarOccurrence, zoneId: ZoneId): AttendanceState? {
        require(rule.sourceUid == occurrence.sourceUid) { "规则与来源实例 UID 必须相同。" }
        if (occurrence.isCancelled) return null
        rule.instanceOverrides[occurrence.instanceKey]?.let { return it }
        val date = occurrence.startsAt.atZone(zoneId).toLocalDate()
        (rule.futureStateOverrides.mapKeys { it.key.atStartOfDay(zoneId).toInstant() } + rule.futureInstantOverrides)
            .filterKeys { !it.isAfter(occurrence.startsAt) }
            .maxByOrNull { it.key }
            ?.value
            ?.let { return it }
        return if (rule.stopAttendingFrom != null && !date.isBefore(rule.stopAttendingFrom)) {
            AttendanceState.STOP_ATTENDING
        } else {
            rule.defaultState
        }
    }
}
