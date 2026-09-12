/*
 * 职责：把纯差异与导入跳过项汇总成用户可读、不会执行写入的同步预览。
 * 输入／输出：输入 SyncChange 与 IcalSkippedEvent；输出类别数量和可显示行。
 * 外部副作用：无。
 * 不负责：确认、授权、网络、Calendar Provider 或删除任何目标事件。
 */
package com.tequila.tumcalendarcontrol.sync

data class SyncPreview(
    val changes: List<SyncPreviewChange>,
    val skipped: List<SyncPreviewSkip>,
) {
    val createCount get() = changes.count { it.type == SyncChangeType.CREATE }
    val updateCount get() = changes.count { it.type == SyncChangeType.UPDATE }
    val removeCount get() = changes.count { it.type == SyncChangeType.REMOVE }
    val keepCount get() = changes.count { it.type == SyncChangeType.KEEP }
    val skipCount get() = skipped.size
}

data class SyncPreviewChange(val type: SyncChangeType, val identity: ManagedEventIdentity)
data class SyncPreviewSkip(val sourceUid: String?, val reason: String)

object SyncPreviewComposer {
    fun compose(changes: List<SyncChange>, skipped: List<IcalSkippedEvent>): SyncPreview = SyncPreview(
        changes = changes.map { SyncPreviewChange(it.type, it.identity) },
        skipped = skipped.map { SyncPreviewSkip(it.sourceUid, it.reason.label) },
    )
}
