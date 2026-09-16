/*
 * 职责：在应用拥有的目标日历上执行已预览的差异，并维护稳定映射。
 * 输入／输出：输入目标、期望事件、资料库映射和拥有权网关；输出更新后的映射与脱敏结果。
 * 外部副作用：仅通过传入网关写入；本文件本身不访问 Android、Google、网络或账户。
 * 不负责：获取私有 iCal、OAuth、目标日历创建、用户确认 UI 或按标题识别事件。
 */
package com.tequila.tumcalendarcontrol.sync

data class OwnedCalendarRef(val target: OutputTarget, val calendarId: String, val ownershipMarker: String)

/** 目标事件已由用户或日历端删除；执行器可安全改为恢复创建。 */
class ManagedEventMissingException : IllegalStateException()

interface OwnedCalendarGateway {
    fun verifyOwnership(calendar: OwnedCalendarRef): Boolean
    fun findEventIdsByIdentity(calendar: OwnedCalendarRef, identity: ManagedEventIdentity): List<String>
    /** 仅清理页显式选择“全部”时使用；只返回应用标记的事件 ID，绝不返回日程正文。 */
    fun findAllManagedEventIds(calendar: OwnedCalendarRef): List<String>
    fun create(calendar: OwnedCalendarRef, event: DesiredCalendarEvent): String
    fun update(calendar: OwnedCalendarRef, eventId: String, event: DesiredCalendarEvent)
    /** 删除结果只描述本次请求，不泄露事件正文或 ID 给 UI／资料库。 */
    fun remove(calendar: OwnedCalendarRef, eventId: String): ManagedEventRemovalOutcome
}

/** 404／410 代表目标此前已不存在，不能伪装成本次真的删除。 */
enum class ManagedEventRemovalOutcome { REMOVED_NOW, ALREADY_ABSENT }

/** 每个目标独立的脱敏清理证据；remaining 为 null 表示删除后复核本身失败。 */
data class ManagedCleanupSummary(
    val mappedCandidateCount: Int,
    val recoveredCandidateCount: Int,
    val requestedCount: Int,
    val removedNowCount: Int,
    val alreadyAbsentCount: Int,
    val failedCount: Int,
    val remainingManagedCount: Int?,
)

/** 脱敏失败分类供协调层做安全恢复；不保存 HTTP 响应、令牌或账户信息。 */
enum class TargetFailureCode {
    GOOGLE_AUTHORIZATION_EXPIRED,
}

data class TargetSyncResult(
    val target: OutputTarget,
    val mappings: List<StoredEventMapping>,
    val changes: List<SyncChange>,
    val failureSummary: String? = null,
    val failureCode: TargetFailureCode? = null,
    /** 旧版映射已丢失、但仍保有应用标记的事件已被显式清除；不保存其正文或身份。 */
    val recoveredRemovalCount: Int = 0,
    /** 仅独立清理路径填充；按目标保存计数，不包含事件 ID 或日程正文。 */
    val cleanupSummary: ManagedCleanupSummary? = null,
)

/** 清理必须由用户单独选择；常规写入绝不创建这个范围。 */
sealed interface ManagedEventCleanupScope {
    data object AllManagedEvents : ManagedEventCleanupScope
    data class SelectedIdentities(val identities: Set<ManagedEventIdentity>) : ManagedEventCleanupScope
}

/** 所有权先验失败时零写入；稳定映射严格以目标、UID 和原始实例键定位。 */
class OwnedCalendarSyncExecutor(private val planner: SyncDiffPlanner = SyncDiffPlanner()) {
    fun execute(
        calendar: OwnedCalendarRef,
        desired: List<DesiredCalendarEvent>,
        storedMappings: List<StoredEventMapping>,
        gateway: OwnedCalendarGateway,
    ): TargetSyncResult {
        if (!gateway.verifyOwnership(calendar)) return TargetSyncResult(calendar.target, storedMappings, emptyList(), "目标日历不属于本应用，已拒绝写入。")
        val targetMappings = storedMappings.filter { it.target == calendar.target }
        val otherMappings = storedMappings.filterNot { it.target == calendar.target }
        val changes = planner.plan(desired, targetMappings.map { ManagedEventMapping(it.identity, it.targetEventId, it.fingerprint) })
        val desiredByIdentity = desired.associateBy { it.identity }
        val next = targetMappings.associateBy { it.identity }.toMutableMap()
        changes.forEach { change ->
            when (change.type) {
                SyncChangeType.CREATE -> {
                    val event = desiredByIdentity.getValue(change.identity)
                    val recoveredIds = gateway.findEventIdsByIdentity(calendar, event.identity)
                    check(recoveredIds.size <= 1) { "目标日历存在同一课次的重复项；请先执行重复项恢复预览" }
                    val id = recoveredIds.singleOrNull()?.also { gateway.update(calendar, it, event) }
                        ?: gateway.create(calendar, event)
                    next[change.identity] = StoredEventMapping(calendar.target, change.identity, id, planner.run { event.fingerprint() })
                }
                SyncChangeType.UPDATE -> {
                    val event = desiredByIdentity.getValue(change.identity)
                    val targetId = try {
                        gateway.update(calendar, change.targetEventId!!, event)
                        change.targetEventId
                    } catch (_: ManagedEventMissingException) {
                        val recoveredIds = gateway.findEventIdsByIdentity(calendar, event.identity)
                        check(recoveredIds.size <= 1) { "目标日历存在同一课次的重复项；请先执行重复项恢复预览" }
                        recoveredIds.singleOrNull()?.also { gateway.update(calendar, it, event) }
                            ?: gateway.create(calendar, event)
                    }
                    next[change.identity] = StoredEventMapping(calendar.target, change.identity, targetId, planner.run { event.fingerprint() })
                }
                SyncChangeType.REMOVE -> { gateway.remove(calendar, change.targetEventId!!); next.remove(change.identity) }
                SyncChangeType.KEEP -> Unit
            }
        }
        return TargetSyncResult(calendar.target, otherMappings + next.values, changes)
    }

    /**
     * 只由独立清理操作调用。全量清理可恢复删除映射丢失但仍带应用标记的旧事件；
     * 精确清理只读取用户已选 UID＋原始实例键对应的标记事件。两者均不按标题、时间或地点猜测。
     */
    fun rollback(
        calendar: OwnedCalendarRef,
        storedMappings: List<StoredEventMapping>,
        gateway: OwnedCalendarGateway,
        scope: ManagedEventCleanupScope = ManagedEventCleanupScope.AllManagedEvents,
    ): TargetSyncResult {
        if (!gateway.verifyOwnership(calendar)) return TargetSyncResult(calendar.target, storedMappings, emptyList(), "目标日历不属于本应用，已拒绝撤销。")
        val targetMappings = storedMappings.filter { it.target == calendar.target }
        val otherMappings = storedMappings.filterNot { it.target == calendar.target }
        val selectedMappings = when (scope) {
            ManagedEventCleanupScope.AllManagedEvents -> targetMappings
            is ManagedEventCleanupScope.SelectedIdentities -> targetMappings.filter { it.identity in scope.identities }
        }
        val recoveredIds = when (scope) {
            ManagedEventCleanupScope.AllManagedEvents -> gateway.findAllManagedEventIds(calendar)
            is ManagedEventCleanupScope.SelectedIdentities -> scope.identities.flatMap { gateway.findEventIdsByIdentity(calendar, it) }
        }
        val mappedIds = selectedMappings.map(StoredEventMapping::targetEventId)
        val idsToRemove = (mappedIds + recoveredIds).distinct()
        var removedNowCount = 0
        var alreadyAbsentCount = 0
        val failedIds = mutableSetOf<String>()
        idsToRemove.forEach { id ->
            when (runCatching { gateway.remove(calendar, id) }.getOrElse { failedIds += id; null }) {
                ManagedEventRemovalOutcome.REMOVED_NOW -> removedNowCount += 1
                ManagedEventRemovalOutcome.ALREADY_ABSENT -> alreadyAbsentCount += 1
                null -> Unit
            }
        }
        val remainingIds = runCatching {
            when (scope) {
                ManagedEventCleanupScope.AllManagedEvents -> gateway.findAllManagedEventIds(calendar)
                is ManagedEventCleanupScope.SelectedIdentities -> scope.identities.flatMap { gateway.findEventIdsByIdentity(calendar, it) }
            }.toSet()
        }.getOrNull()
        val completedMappedIds = mappedIds.filter { id ->
            id !in failedIds && (remainingIds == null || id !in remainingIds)
        }.toSet()
        val changes = selectedMappings
            .filter { it.targetEventId in completedMappedIds }
            .map { SyncChange(SyncChangeType.REMOVE, it.identity, it.targetEventId) }
        val retainedMappings = targetMappings.filterNot { mapping ->
            mapping in selectedMappings && mapping.targetEventId in completedMappedIds
        }
        val summary = ManagedCleanupSummary(
            mappedCandidateCount = mappedIds.size,
            recoveredCandidateCount = recoveredIds.toSet().count { it !in mappedIds },
            requestedCount = idsToRemove.size,
            removedNowCount = removedNowCount,
            alreadyAbsentCount = alreadyAbsentCount,
            failedCount = failedIds.size,
            remainingManagedCount = remainingIds?.size,
        )
        val verificationFailed = remainingIds == null
        val failureSummary = when {
            failedIds.isNotEmpty() -> "${calendar.target.safeLabel()} 清理未完成：${failedIds.size} 条删除失败。"
            verificationFailed -> "${calendar.target.safeLabel()} 删除后核对未完成；请稍后只读检查。"
            remainingIds.isNotEmpty() -> "${calendar.target.safeLabel()} 清理后仍有 ${remainingIds.size} 条应用日程。"
            else -> null
        }
        return TargetSyncResult(
            target = calendar.target,
            mappings = otherMappings + retainedMappings,
            changes = changes,
            failureSummary = failureSummary,
            recoveredRemovalCount = recoveredIds.toSet().count { it !in mappedIds && it !in failedIds && (remainingIds == null || it !in remainingIds) },
            cleanupSummary = summary,
        )
    }
}

private fun OutputTarget.safeLabel(): String = when (this) {
    OutputTarget.LOCAL_CALENDAR -> "本机日历"
    OutputTarget.GOOGLE_CALENDAR -> "Google 日历"
}

/** 仅供单元测试的内存网关；可记录写入，不代表任何真实 Calendar Provider。 */
class FakeOwnedCalendarGateway(
    private val ownedCalendarIds: Set<String>,
    private val recoveredIds: Map<ManagedEventIdentity, List<String>> = emptyMap(),
    private val allManagedIds: List<String> = emptyList(),
    private val removalOutcomes: Map<String, ManagedEventRemovalOutcome> = emptyMap(),
    private val failedRemovalIds: Set<String> = emptySet(),
    private val retainedAfterRemovalIds: Set<String> = emptySet(),
) : OwnedCalendarGateway {
    val operations = mutableListOf<String>()
    private var nextId = 1
    private val currentManagedIds = allManagedIds.toMutableSet()
    private val currentRecoveredIds = recoveredIds.mapValues { (_, ids) -> ids.toMutableSet() }.toMutableMap()
    override fun verifyOwnership(calendar: OwnedCalendarRef): Boolean = calendar.calendarId in ownedCalendarIds && calendar.ownershipMarker == "tum-calendar-control"
    override fun findEventIdsByIdentity(calendar: OwnedCalendarRef, identity: ManagedEventIdentity): List<String> = currentRecoveredIds[identity].orEmpty().toList()
    override fun findAllManagedEventIds(calendar: OwnedCalendarRef): List<String> = currentManagedIds.toList()
    override fun create(calendar: OwnedCalendarRef, event: DesiredCalendarEvent): String = "${calendar.target.name.lowercase()}-${nextId++}".also { operations += "create:$it" }
    override fun update(calendar: OwnedCalendarRef, eventId: String, event: DesiredCalendarEvent) { operations += "update:$eventId" }
    override fun remove(calendar: OwnedCalendarRef, eventId: String): ManagedEventRemovalOutcome {
        operations += "remove:$eventId"
        check(eventId !in failedRemovalIds) { "合成删除失败" }
        val outcome = removalOutcomes[eventId] ?: ManagedEventRemovalOutcome.REMOVED_NOW
        if (outcome == ManagedEventRemovalOutcome.REMOVED_NOW && eventId !in retainedAfterRemovalIds) {
            currentManagedIds.remove(eventId)
            currentRecoveredIds.values.forEach { ids -> ids.remove(eventId) }
        }
        return outcome
    }
}
