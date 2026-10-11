/*
 * Aufgabe: synthetische Regressionstests für SyncPreview.
 * Ein-/Ausgabe: künstliche Zustände und Fixtures -> JUnit-Prüfergebnisse.
 * Nebenwirkungen: nur lokaler Testprozess und gegebenenfalls Robolectric-Präferenzen.
 * Nicht zuständig: echte Konten, private Quellen, Geräte oder reale Kalenderausgaben.
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
