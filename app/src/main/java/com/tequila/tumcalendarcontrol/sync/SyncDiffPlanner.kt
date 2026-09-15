/*
 * 职责：以 UID 与原始实例键计算目标日历的新增、更新、删除和保持不变差异。
 * 输入／输出：输入期望事件和应用拥有的既有映射；输出可预览、可执行的差异计划。
 * 外部副作用：无。
 * 不负责：按标题等不稳定字段识别事件、实际删除／写入或访问外部账户。
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.Instant
import java.security.MessageDigest

/** 变更可见输出字段或事件样式时递增，促使既有映射安全走更新而非删除重建。 */
private const val EVENT_OUTPUT_FORMAT_REVISION = "2026-09-status-colors-v1"

data class ManagedEventIdentity(val sourceUid: String, val instanceKey: String)

/**
 * 生成目标日历的不可见稳定课次键；不会把私有来源身份暴露到标题、地点或说明。
 * 它只用于映射丢失后的恢复，主身份仍是 UID 与实例键的组合。
 */
fun ManagedEventIdentity.opaqueKey(): String = MessageDigest.getInstance("SHA-256")
    .digest("$sourceUid\u0000$instanceKey".toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }

data class DesiredCalendarEvent(
    val identity: ManagedEventIdentity,
    val title: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val attendanceState: AttendanceState,
    val location: String = "",
)

data class ManagedEventMapping(
    val identity: ManagedEventIdentity,
    val targetEventId: String,
    val fingerprint: String,
)

enum class SyncChangeType(val label: String) {
    CREATE("新增"), UPDATE("更新"), REMOVE("移除"), KEEP("保持不变"),
}

data class SyncChange(
    val type: SyncChangeType,
    val identity: ManagedEventIdentity,
    val targetEventId: String? = null,
)

class SyncDiffPlanner {
    /**
     * 正常写入只新增或更新当前选择，不会把本轮未选择的旧课程解释为“删除”。
     * 所有删除都必须经 `OwnedCalendarSyncExecutor.rollback` 的单独、显式清理路径。
     */
    fun plan(desired: List<DesiredCalendarEvent>, existing: List<ManagedEventMapping>): List<SyncChange> {
        val existingByIdentity = existing.associateBy(ManagedEventMapping::identity)
        val desiredByIdentity = desired.associateBy(DesiredCalendarEvent::identity)
        // 第二道防线：上游即使漏过去重，同一身份也只能产生一个动作。
        val upserts = desiredByIdentity.values.sortedBy { it.startsAt }.map { event ->
            val existingMapping = existingByIdentity[event.identity]
            when {
                existingMapping == null -> SyncChange(SyncChangeType.CREATE, event.identity)
                existingMapping.fingerprint == event.fingerprint() ->
                    SyncChange(SyncChangeType.KEEP, event.identity, existingMapping.targetEventId)
                else -> SyncChange(SyncChangeType.UPDATE, event.identity, existingMapping.targetEventId)
            }
        }
        return upserts
    }

    /** 指纹可包含可见输出字段，但绝不作为跨同步身份；身份始终来自 UID 与实例键。 */
    fun DesiredCalendarEvent.fingerprint(): String = listOf(
        EVENT_OUTPUT_FORMAT_REVISION,
        title,
        startsAt.toString(),
        endsAt.toString(),
        attendanceState.name,
        location,
    ).joinToString("|")
}
