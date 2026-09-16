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
    /** 仅统计目标明确报告“本次删除成功”的次数；404／410 不计入本次删除。 */
    val removedCount: Int get() = targetResults.sumOf { result ->
        result.cleanupSummary?.removedNowCount ?: result.changes.count { it.type == SyncChangeType.REMOVE }
    }
    val failureSummary: String? get() = targetResults.mapNotNull(TargetSyncResult::failureSummary).takeIf(List<String>::isNotEmpty)?.joinToString("；")
    val cleanupDetailSummary: String? get() = targetResults
        .mapNotNull { result -> result.cleanupSummary?.toUserSummary(result.target) }
        .takeIf(List<String>::isNotEmpty)
        ?.joinToString("；")
}

/** 面向使用者只展示按目标的完成情况；不包含映射、标记、事件 ID 或日程正文。 */
private fun ManagedCleanupSummary.toUserSummary(target: OutputTarget): String {
    val label = when (target) {
        OutputTarget.LOCAL_CALENDAR -> "本机日历"
        OutputTarget.GOOGLE_CALENDAR -> "Google 日历"
    }
    val remaining = remainingManagedCount?.let { "删除后复核剩余 $it 个" } ?: "删除后复核未完成"
    return "$label：本次请求清理 $requestedCount 个应用日程，已删除 $removedNowCount 个，之前已不存在 $alreadyAbsentCount 个，$remaining"
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
                .getOrElse { error ->
                    TargetSyncResult(
                        target = binding.calendar.target,
                        mappings = currentMappings,
                        changes = emptyList(),
                        failureSummary = error.googleSafeSummary("${binding.calendar.target.name} 同步失败"),
                        failureCode = error.targetFailureCode(),
                    )
                }
            if (result.failureSummary == null) currentMappings = result.mappings
            result
        }
        return ManagedSyncResult(currentMappings, results)
    }

    /** 撤销仍逐目标隔离；只有映射中的应用事件可被删除。 */
    fun rollback(
        mappings: List<StoredEventMapping>,
        bindings: List<TargetBinding>,
        scope: ManagedEventCleanupScope = ManagedEventCleanupScope.AllManagedEvents,
    ): ManagedSyncResult {
        var currentMappings = mappings
        val results = bindings.map { binding ->
            val result = runCatching { executor.rollback(binding.calendar, currentMappings, binding.gateway, scope) }
                .getOrElse { TargetSyncResult(binding.calendar.target, currentMappings, emptyList(), "${binding.calendar.target.name} 撤销失败；未丢失本机映射") }
            // 清理可部分完成：已由网关确认删除的映射应保存，失败／仍存在的映射会由执行器保留。
            currentMappings = result.mappings
            result
        }
        return ManagedSyncResult(currentMappings, results)
    }
}
