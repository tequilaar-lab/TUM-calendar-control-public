/*
 * 职责：验证离线同步预览正确区分变更与 Fixplatz 跳过原因。
 * 输入／输出：输入合成差异与跳过事件；输出预览计数断言。
 * 外部副作用：无。
 * 不负责：确认写入、权限、网络或真实日历显示。
 */
package com.tequila.tumcalendarcontrol.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPreviewTest {
    @Test fun `预览分别汇总五类结果且保留跳过原因`() {
        val identity = ManagedEventIdentity("synthetic", "2026-10-01T08:00:00Z")
        val preview = SyncPreviewComposer.compose(
            SyncChangeType.entries.map { SyncChange(it, identity) },
            listOf(IcalSkippedEvent("pending", IcalSkipReason.NOT_CONFIRMED_FIXPLATZ)),
        )
        assertEquals(1, preview.createCount)
        assertEquals(1, preview.updateCount)
        assertEquals(1, preview.removeCount)
        assertEquals(1, preview.keepCount)
        assertEquals(1, preview.skipCount)
        assertEquals("未确认 Fixplatz", preview.skipped.single().reason)
    }
}
