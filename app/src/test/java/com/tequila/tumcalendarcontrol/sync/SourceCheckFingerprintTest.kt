/*
 * Aufgabe: synthetische Regressionstests für SourceCheckFingerprint.
 * Ein-/Ausgabe: künstliche Zustände und Fixtures -> JUnit-Prüfergebnisse.
 * Nebenwirkungen: nur lokaler Testprozess und gegebenenfalls Robolectric-Präferenzen.
 * Nicht zuständig: echte Konten, private Quellen, Geräte oder reale Kalenderausgaben.
 */
package com.tequila.tumcalendarcontrol.sync

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SourceCheckFingerprintTest {
    @Test
    fun `标题地点变化会改变摘要 顺序不影响结果`() {
        val first = occurrence(title = "合成标题", location = "地点 A")
        val renamed = first.copy(title = "另一标题", location = "地点 B")

        assertNotEquals(SourceCheckFingerprint.from(listOf(first)), SourceCheckFingerprint.from(listOf(renamed)))
        assertEquals(SourceCheckFingerprint.from(listOf(first, second())), SourceCheckFingerprint.from(listOf(second(), first)))
    }

    @Test
    fun `稳定键 时间或取消状态变化会改变摘要`() {
        val first = occurrence()

        assertNotEquals(SourceCheckFingerprint.from(listOf(first)), SourceCheckFingerprint.from(listOf(first.copy(instanceKey = "other"))))
        assertNotEquals(SourceCheckFingerprint.from(listOf(first)), SourceCheckFingerprint.from(listOf(first.copy(isCancelled = true))))
    }

    private fun occurrence(title: String = "合成", location: String = "地点") = SourceCalendarOccurrence(
        sourceUid = "synthetic-uid",
        instanceKey = "2026-10-01T08:00:00Z",
        title = title,
        startsAt = Instant.parse("2026-10-01T08:00:00Z"),
        endsAt = Instant.parse("2026-10-01T09:00:00Z"),
        isCancelled = false,
        location = location,
    )

    private fun second() = occurrence().copy(sourceUid = "synthetic-uid-b", instanceKey = "2026-10-02T08:00:00Z")
}
