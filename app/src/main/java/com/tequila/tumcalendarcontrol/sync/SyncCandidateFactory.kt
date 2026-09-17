/*
 * 职责：将来源实例、手动事项与本机三态规则转换为确定性的目标日历候选。
 * 输入／输出：输入当前进程来源实例、规则与手动事项；输出按稳定身份排序的 DesiredCalendarEvent。
 * 外部副作用：无。
 * 不负责：读取 iCal、保存来源数据、创建日历、差异执行或按标题推断身份。
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.ZoneId

class SyncCandidateFactory(private val zoneId: ZoneId = ZoneId.of("Europe/Berlin")) {
    /**
     * 仅将明确选中的来源课次转为候选；未配置规则的选中课次默认蓝色，取消实例不会输出。
     * 身份同时含 UID 与原始实例键，避免用户选一节课却输出整门课程的所有课次。
     */
    fun build(
        occurrences: List<SourceCalendarOccurrence>,
        selectedSourceOccurrences: Set<SourceOccurrenceIdentity>,
        sourceOutputWindow: SourceOutputDateWindow = SourceOutputDateWindow(),
        rules: List<SyncRule>,
        manualEvents: List<ManualScheduleItem>,
        overrides: Map<String, com.tequila.tumcalendarcontrol.domain.CourseLocalOverride> = emptyMap(),
    ): List<DesiredCalendarEvent> {
        val rulesByUid = rules.associateBy(SyncRule::sourceUid)
        val sourceEvents = occurrences.asSequence()
            .filter { it.identity in selectedSourceOccurrences }
            .mapNotNull { occurrence ->
            val rule = rulesByUid[occurrence.sourceUid]
                ?: SyncRule(occurrence.sourceUid, hasConfirmedFixplatz = true, defaultState = AttendanceState.CHOOSE_TO_ATTEND)
            SyncRuleResolver.resolve(rule, occurrence, zoneId)?.let { state ->
                val override = overrides[occurrence.sourceUid]
                val meeting = override?.meetingTime
                val originalDay = occurrence.startsAt.atZone(zoneId).toLocalDate()
                val newDay = meeting?.let { originalDay.plusDays((it.weekday.ordinal + 1 - originalDay.dayOfWeek.value).toLong()) }
                val start = meeting?.let { newDay!!.atTime(it.startHour, it.startMinute).atZone(zoneId).toInstant() } ?: occurrence.startsAt
                val end = meeting?.let { newDay!!.atTime(it.endHour, it.endMinute).atZone(zoneId).toInstant() } ?: occurrence.endsAt
                if (!sourceOutputWindow.includes(start, end, zoneId)) return@let null
                DesiredCalendarEvent(
                    identity = ManagedEventIdentity(occurrence.sourceUid, occurrence.instanceKey),
                    title = override?.title ?: occurrence.title,
                    startsAt = start,
                    endsAt = end,
                    attendanceState = state,
                    location = override?.location ?: occurrence.location,
                )
            }
        }.toList()
        val manual = manualEvents.map { item ->
            DesiredCalendarEvent(
                identity = ManagedEventIdentity("manual:${item.id}", item.id),
                title = item.title,
                startsAt = item.startsAt,
                endsAt = item.endsAt,
                attendanceState = item.attendanceState,
            )
        }
        // 同一 iCal 实例偶尔会被来源展开器交付两次；只按稳定来源身份折叠。
        return (sourceEvents + manual)
            .distinctBy(DesiredCalendarEvent::identity)
            .sortedWith(compareBy<DesiredCalendarEvent> { it.startsAt }.thenBy { it.identity.sourceUid }.thenBy { it.identity.instanceKey })
    }
}
