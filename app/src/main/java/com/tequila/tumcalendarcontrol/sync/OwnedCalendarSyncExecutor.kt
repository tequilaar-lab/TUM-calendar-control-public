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
    /** 只读取已核对身份的应用事件字段；未知格式返回 null，绝不能用本机指纹冒充实际值。 */
    fun readEvent(calendar: OwnedCalendarRef, eventId: String, identity: ManagedEventIdentity): DesiredCalendarEvent? = null
    fun readFingerprint(calendar: OwnedCalendarRef, eventId: String, identity: ManagedEventIdentity): String? =
        readEvent(calendar, eventId, identity)?.let { SyncDiffPlanner().run { it.fingerprint() } }
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
    val restoredMissingCount: Int = 0,
    val failedCount: Int = 0,
)

/** 清理必须由用户单独选择；常规写入绝不创建这个范围。 */
sealed interface ManagedEventCleanupScope {
    data object AllManagedEvents : ManagedEventCleanupScope
    data class SelectedIdentities(
        val identities: Set<ManagedEventIdentity>,
        val byTarget: Map<OutputTarget, Set<ManagedEventIdentity>> = emptyMap(),
    ) : ManagedEventCleanupScope {
        fun forTarget(target: OutputTarget): Set<ManagedEventIdentity> = if (byTarget.isEmpty()) identities else byTarget[target].orEmpty()
    }
}

/** 所有权先验失败时零写入；稳定映射严格以目标、UID 和原始实例键定位。 */
class OwnedCalendarSyncExecutor(private val planner: SyncDiffPlanner = SyncDiffPlanner()) {
    fun execute(
        calendar: OwnedCalendarRef,
        desired: List<DesiredCalendarEvent>,
        storedMappings: List<StoredEventMapping>,
        gateway: OwnedCalendarGateway,
        onCheckpoint: (List<StoredEventMapping>) -> Unit = {},
    ): TargetSyncResult {
        if (!gateway.verifyOwnership(calendar)) return TargetSyncResult(calendar.target, storedMappings, emptyList(), "目标日历不属于本应用，已拒绝写入。", failedCount = desired.size)
        val targetMappings = storedMappings.filter { it.target == calendar.target }
        val otherMappings = storedMappings.filterNot { it.target == calendar.target }
        val next = targetMappings.associateBy { it.identity }.toMutableMap()
        val completed = mutableListOf<SyncChange>()
        val failures = mutableListOf<Throwable>()
        var restored = 0
        // 每次确认都按实际目标内的应用身份定位；旧映射不是目标存在的证明。
        desired.distinctBy { it.identity }.forEach { event ->
            try {
                val ids = gateway.findEventIdsByIdentity(calendar, event.identity)
                check(ids.size <= 1) { "目标存在重复课次，未改动该课次；请先核对重复项。" }
                var type = SyncChangeType.UPDATE
                val id = ids.singleOrNull()?.let { existingId ->
                    try {
                        val actual = gateway.readFingerprint(calendar, existingId, event.identity)
                        if (actual == planner.run { event.fingerprint() }) type = SyncChangeType.KEEP
                        else gateway.update(calendar, existingId, event)
                        existingId
                    } catch (_: ManagedEventMissingException) {
                        type = SyncChangeType.CREATE
                        gateway.create(calendar, event)
                    }
                } ?: run { type = SyncChangeType.CREATE; gateway.create(calendar, event) }
                next[event.identity] = StoredEventMapping(calendar.target, event.identity, id, planner.run { event.fingerprint() }, calendar.calendarId, event)
                completed += SyncChange(type, event.identity, id)
                if (type == SyncChangeType.CREATE && targetMappings.any { it.identity == event.identity }) restored += 1
                onCheckpoint(otherMappings + next.values)
            } catch (error: Exception) {
                failures += error
            }
        }
        return TargetSyncResult(
            calendar.target, otherMappings + next.values, completed,
            failures.firstOrNull()?.googleSafeSummary("${calendar.target.safeLabel()}：已完成 ${completed.size} 条，失败 ${failures.size} 条"),
            failures.firstNotNullOfOrNull { it.targetFailureCode() },
            restoredMissingCount = restored,
            failedCount = failures.size,
        )
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
            is ManagedEventCleanupScope.SelectedIdentities -> targetMappings.filter { it.identity in scope.forTarget(calendar.target) }
        }
        val recoveredIds = when (scope) {
            ManagedEventCleanupScope.AllManagedEvents -> gateway.findAllManagedEventIds(calendar)
            is ManagedEventCleanupScope.SelectedIdentities -> scope.forTarget(calendar.target).flatMap { gateway.findEventIdsByIdentity(calendar, it) }
        }
        // 陈旧映射不赋予删除权；仅删除这次在专用目标内找到的应用标记事件。
        val mappedIds = selectedMappings.map(StoredEventMapping::targetEventId)
        val idsToRemove = recoveredIds.distinct()
        var removedNowCount = 0
        var alreadyAbsentCount = mappedIds.distinct().count { it !in recoveredIds }
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
                is ManagedEventCleanupScope.SelectedIdentities -> scope.forTarget(calendar.target).flatMap { gateway.findEventIdsByIdentity(calendar, it) }
            }.toSet()
        }.getOrNull()
        val completedMappedIds = selectedMappings.map(StoredEventMapping::targetEventId).filter { id ->
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
            requestedCount = (mappedIds + recoveredIds).distinct().size,
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
    private val generated = mutableMapOf<Pair<String, ManagedEventIdentity>, String>()
    private val eventValues = mutableMapOf<Pair<String, String>, DesiredCalendarEvent>()
    override fun verifyOwnership(calendar: OwnedCalendarRef): Boolean = calendar.calendarId in ownedCalendarIds && calendar.ownershipMarker == "tum-calendar-control"
    override fun findEventIdsByIdentity(calendar: OwnedCalendarRef, identity: ManagedEventIdentity): List<String> = (currentRecoveredIds[identity].orEmpty() + listOfNotNull(generated[calendar.calendarId to identity])).distinct()
    override fun findAllManagedEventIds(calendar: OwnedCalendarRef): List<String> = (currentManagedIds + generated.filterKeys { it.first == calendar.calendarId }.values).distinct()
    override fun create(calendar: OwnedCalendarRef, event: DesiredCalendarEvent): String = "${calendar.target.name.lowercase()}-${nextId++}".also { operations += "create:$it"; generated[calendar.calendarId to event.identity] = it; eventValues[calendar.calendarId to it] = event }
    override fun update(calendar: OwnedCalendarRef, eventId: String, event: DesiredCalendarEvent) { operations += "update:$eventId"; eventValues[calendar.calendarId to eventId] = event }
    override fun readFingerprint(calendar: OwnedCalendarRef, eventId: String, identity: ManagedEventIdentity): String? = eventValues[calendar.calendarId to eventId]?.let { SyncDiffPlanner().run { it.fingerprint() } }
    override fun remove(calendar: OwnedCalendarRef, eventId: String): ManagedEventRemovalOutcome {
        operations += "remove:$eventId"
        check(eventId !in failedRemovalIds) { "合成删除失败" }
        val outcome = removalOutcomes[eventId] ?: ManagedEventRemovalOutcome.REMOVED_NOW
        if (outcome == ManagedEventRemovalOutcome.REMOVED_NOW && eventId !in retainedAfterRemovalIds) {
            currentManagedIds.remove(eventId)
            currentRecoveredIds.values.forEach { ids -> ids.remove(eventId) }
            generated.entries.removeAll { it.key.first == calendar.calendarId && it.value == eventId }
        }
        return outcome
    }
}
