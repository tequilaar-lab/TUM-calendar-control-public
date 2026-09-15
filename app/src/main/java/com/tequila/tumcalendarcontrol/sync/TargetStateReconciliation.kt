/*
 * 职责：比较应用映射、期望日程与已读取的应用专用目标事件，生成可展示的真实状态诊断。
 * 输入／输出：输入合成或已授权读取的目标快照；输出稳定代码与中文解释。
 * 外部副作用：无。
 * 不负责：读取日历、修改规则、写入或删除事件。
 */
package com.tequila.tumcalendarcontrol.sync

data class ActualManagedEvent(val eventId: String, val fingerprint: String)

enum class TargetStateCode(val explanation: String) {
    IN_SYNC("应用记录与日历实际内容一致。"),
    MISSING_IN_TARGET("应用记录显示已写入，但日历中已找不到该日程；下次确认可重新创建。"),
    CHANGED_IN_TARGET("日历中的日程已在应用外被修改；请先选择采用日历实际状态，或按应用规则修复。"),
}

data class TargetStateFinding(
    val identity: ManagedEventIdentity,
    val code: TargetStateCode,
    val eventId: String,
)

/** 只比较同一映射 ID；不同来源身份即使时间冲突，也不会互相影响。 */
class TargetStateReconciler(private val planner: SyncDiffPlanner = SyncDiffPlanner()) {
    fun reconcile(
        desired: List<DesiredCalendarEvent>,
        mappings: List<StoredEventMapping>,
        actualByEventId: Map<String, ActualManagedEvent>,
    ): List<TargetStateFinding> {
        val desiredByIdentity = desired.associateBy(DesiredCalendarEvent::identity)
        return mappings.mapNotNull { mapping ->
            val event = desiredByIdentity[mapping.identity] ?: return@mapNotNull null
            val actual = actualByEventId[mapping.targetEventId]
            val code = when {
                actual == null -> TargetStateCode.MISSING_IN_TARGET
                actual.fingerprint == planner.run { event.fingerprint() } -> TargetStateCode.IN_SYNC
                else -> TargetStateCode.CHANGED_IN_TARGET
            }
            TargetStateFinding(mapping.identity, code, mapping.targetEventId)
        }
    }
}
