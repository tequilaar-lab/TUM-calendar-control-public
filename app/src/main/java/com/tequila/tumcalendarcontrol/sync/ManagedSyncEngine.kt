/*
 * 职责：为启用目标生成差异预览，并顺序执行或撤销已确认的应用拥有输出。
 * 输入／输出：输入候选事件、映射、目标绑定；输出每个目标的脱敏变更与下一份映射。
 * 外部副作用：仅通过注入的 OwnedCalendarGateway 产生写入；调用者必须在用户确认后调用执行／撤销。
 * 不负责：读取来源、申请权限、OAuth、创建目标绑定、保存资料库或展示 UI。
 */
package com.tequila.tumcalendarcontrol.sync

data class ManagedSyncPreview(
    val desiredEventCount: Int,
    val targetChanges: Map<OutputTarget, List<SyncChange>>,
)

data class TargetBinding(val calendar: OwnedCalendarRef, val gateway: OwnedCalendarGateway)

data class ManagedSyncResult(
    val mappings: List<StoredEventMapping>,
    val targetResults: List<TargetSyncResult>,
) {
    val createdCount: Int get() = targetResults.sumOf { result -> result.changes.count { it.type == SyncChangeType.CREATE } }
    val updatedCount: Int get() = targetResults.sumOf { result -> result.changes.count { it.type == SyncChangeType.UPDATE } }
    val removedCount: Int get() = targetResults.sumOf { result -> result.changes.count { it.type == SyncChangeType.REMOVE } }
    val failureSummary: String? get() = targetResults.mapNotNull(TargetSyncResult::failureSummary).takeIf(List<String>::isNotEmpty)?.joinToString("；")
}

class ManagedSyncEngine(
    private val executor: OwnedCalendarSyncExecutor = OwnedCalendarSyncExecutor(),
    private val planner: SyncDiffPlanner = SyncDiffPlanner(),
) {
    /** 预览不访问目标日历；只使用应用已保存映射计算预计增删改。 */
    fun preview(desired: List<DesiredCalendarEvent>, mappings: List<StoredEventMapping>, targets: Set<OutputTarget>): ManagedSyncPreview =
        ManagedSyncPreview(
            desiredEventCount = desired.size,
            targetChanges = targets.associateWith { target ->
                planner.plan(desired, mappings.filter { it.target == target }.map { ManagedEventMapping(it.identity, it.targetEventId, it.fingerprint) })
            },
        )

    /** 某个目标失败时保留已成功目标的映射，并继续尝试其他独立目标。 */
    fun execute(desired: List<DesiredCalendarEvent>, mappings: List<StoredEventMapping>, bindings: List<TargetBinding>): ManagedSyncResult {
        var currentMappings = mappings
        val results = bindings.map { binding ->
            val result = runCatching { executor.execute(binding.calendar, desired, currentMappings, binding.gateway) }
                .getOrElse { TargetSyncResult(binding.calendar.target, currentMappings, emptyList(), "${binding.calendar.target.name} 同步失败；可检查权限或网络后重试") }
            if (result.failureSummary == null) currentMappings = result.mappings
            result
        }
        return ManagedSyncResult(currentMappings, results)
    }

    /** 撤销仍逐目标隔离；只有映射中的应用事件可被删除。 */
    fun rollback(mappings: List<StoredEventMapping>, bindings: List<TargetBinding>): ManagedSyncResult {
        var currentMappings = mappings
        val results = bindings.map { binding ->
            val result = runCatching { executor.rollback(binding.calendar, currentMappings, binding.gateway) }
                .getOrElse { TargetSyncResult(binding.calendar.target, currentMappings, emptyList(), "${binding.calendar.target.name} 撤销失败；未丢失本机映射") }
            if (result.failureSummary == null) currentMappings = result.mappings
            result
        }
        return ManagedSyncResult(currentMappings, results)
    }
}
