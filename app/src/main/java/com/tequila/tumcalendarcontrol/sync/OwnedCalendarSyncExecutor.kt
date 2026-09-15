/*
 * 职责：在应用拥有的目标日历上执行已预览的差异，并维护稳定映射。
 * 输入／输出：输入目标、期望事件、资料库映射和拥有权网关；输出更新后的映射与脱敏结果。
 * 外部副作用：仅通过传入网关写入；本文件本身不访问 Android、Google、网络或账户。
 * 不负责：获取私有 iCal、OAuth、目标日历创建、用户确认 UI 或按标题识别事件。
 */
package com.tequila.tumcalendarcontrol.sync

data class OwnedCalendarRef(val target: OutputTarget, val calendarId: String, val ownershipMarker: String)

interface OwnedCalendarGateway {
    fun verifyOwnership(calendar: OwnedCalendarRef): Boolean
    fun findEventIdsByIdentity(calendar: OwnedCalendarRef, identity: ManagedEventIdentity): List<String>
    fun create(calendar: OwnedCalendarRef, event: DesiredCalendarEvent): String
    fun update(calendar: OwnedCalendarRef, eventId: String, event: DesiredCalendarEvent)
    fun remove(calendar: OwnedCalendarRef, eventId: String)
}

data class TargetSyncResult(
    val target: OutputTarget,
    val mappings: List<StoredEventMapping>,
    val changes: List<SyncChange>,
    val failureSummary: String? = null,
)

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
                    gateway.update(calendar, change.targetEventId!!, event)
                    next[change.identity] = StoredEventMapping(calendar.target, change.identity, change.targetEventId, planner.run { event.fingerprint() })
                }
                SyncChangeType.REMOVE -> { gateway.remove(calendar, change.targetEventId!!); next.remove(change.identity) }
                SyncChangeType.KEEP -> Unit
            }
        }
        return TargetSyncResult(calendar.target, otherMappings + next.values, changes)
    }

    /** 只删除资料库已经映射的本应用事件；不删除日历本身或未映射的用户事件。 */
    fun rollback(
        calendar: OwnedCalendarRef,
        storedMappings: List<StoredEventMapping>,
        gateway: OwnedCalendarGateway,
    ): TargetSyncResult {
        if (!gateway.verifyOwnership(calendar)) return TargetSyncResult(calendar.target, storedMappings, emptyList(), "目标日历不属于本应用，已拒绝撤销。")
        val targetMappings = storedMappings.filter { it.target == calendar.target }
        val otherMappings = storedMappings.filterNot { it.target == calendar.target }
        targetMappings.forEach { gateway.remove(calendar, it.targetEventId) }
        val changes = targetMappings.map { SyncChange(SyncChangeType.REMOVE, it.identity, it.targetEventId) }
        return TargetSyncResult(calendar.target, otherMappings, changes)
    }
}

/** 仅供单元测试的内存网关；可记录写入，不代表任何真实 Calendar Provider。 */
class FakeOwnedCalendarGateway(
    private val ownedCalendarIds: Set<String>,
    private val recoveredIds: Map<ManagedEventIdentity, List<String>> = emptyMap(),
) : OwnedCalendarGateway {
    val operations = mutableListOf<String>()
    private var nextId = 1
    override fun verifyOwnership(calendar: OwnedCalendarRef): Boolean = calendar.calendarId in ownedCalendarIds && calendar.ownershipMarker == "tum-calendar-control"
    override fun findEventIdsByIdentity(calendar: OwnedCalendarRef, identity: ManagedEventIdentity): List<String> = recoveredIds[identity].orEmpty()
    override fun create(calendar: OwnedCalendarRef, event: DesiredCalendarEvent): String = "${calendar.target.name.lowercase()}-${nextId++}".also { operations += "create:$it" }
    override fun update(calendar: OwnedCalendarRef, eventId: String, event: DesiredCalendarEvent) { operations += "update:$eventId" }
    override fun remove(calendar: OwnedCalendarRef, eventId: String) { operations += "remove:$eventId" }
}
