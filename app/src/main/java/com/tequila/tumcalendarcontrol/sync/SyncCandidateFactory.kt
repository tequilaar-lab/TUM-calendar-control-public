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
     * 仅将明确选中的来源 UID 转为候选；未配置规则的选中来源默认蓝色，取消实例不会输出。
     * 强制传入选择集，避免调用方遗漏来源目录这一人工确认边界。
     */
    fun build(
        occurrences: List<SourceCalendarOccurrence>,
        selectedSourceCourseUids: Set<String>,
        rules: List<SyncRule>,
        manualEvents: List<ManualScheduleItem>,
    ): List<DesiredCalendarEvent> {
        val rulesByUid = rules.associateBy(SyncRule::sourceUid)
        val sourceEvents = occurrences.asSequence()
            .filter { it.sourceUid in selectedSourceCourseUids }
            .mapNotNull { occurrence ->
            val rule = rulesByUid[occurrence.sourceUid]
                ?: SyncRule(occurrence.sourceUid, hasConfirmedFixplatz = true, defaultState = AttendanceState.CHOOSE_TO_ATTEND)
            SyncRuleResolver.resolve(rule, occurrence, zoneId)?.let { state ->
                DesiredCalendarEvent(
                    identity = ManagedEventIdentity(occurrence.sourceUid, occurrence.instanceKey),
                    title = occurrence.title,
                    startsAt = occurrence.startsAt,
                    endsAt = occurrence.endsAt,
                    attendanceState = state,
                    location = occurrence.location,
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
        return (sourceEvents + manual).sortedWith(compareBy<DesiredCalendarEvent> { it.startsAt }.thenBy { it.identity.sourceUid }.thenBy { it.identity.instanceKey })
    }
}
