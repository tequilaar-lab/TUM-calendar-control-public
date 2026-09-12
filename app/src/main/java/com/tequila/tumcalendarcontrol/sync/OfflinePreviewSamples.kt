/*
 * 职责：提供仅供界面演示的合成差异预览，不依赖真实来源、账户或日历。
 * 输入／输出：无输入；输出固定的 SyncPreview。
 * 外部副作用：无。
 * 不负责：模拟真实同步结果或作为写入计划执行。
 */
package com.tequila.tumcalendarcontrol.sync

object OfflinePreviewSamples {
    fun preview(): SyncPreview = SyncPreviewComposer.compose(
        changes = listOf(
            SyncChange(SyncChangeType.CREATE, ManagedEventIdentity("synthetic-course-a", "2026-10-06T08:00:00Z")),
            SyncChange(SyncChangeType.UPDATE, ManagedEventIdentity("synthetic-course-b", "2026-10-07T12:00:00Z")),
            SyncChange(SyncChangeType.REMOVE, ManagedEventIdentity("synthetic-course-c", "2026-10-08T06:00:00Z")),
            SyncChange(SyncChangeType.KEEP, ManagedEventIdentity("synthetic-course-d", "2026-10-09T10:00:00Z")),
        ),
        skipped = listOf(IcalSkippedEvent("synthetic-pending", IcalSkipReason.NOT_CONFIRMED_FIXPLATZ)),
    )
}
