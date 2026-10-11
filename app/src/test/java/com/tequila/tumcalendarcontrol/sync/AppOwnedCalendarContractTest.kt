/*
 * Aufgabe: synthetische Regressionstests für AppOwnedCalendarContract.
 * Ein-/Ausgabe: künstliche Zustände und Fixtures -> JUnit-Prüfergebnisse.
 * Nebenwirkungen: nur lokaler Testprozess und gegebenenfalls Robolectric-Präferenzen.
 * Nicht zuständig: echte Konten, private Quellen, Geräte oder reale Kalenderausgaben.
 */
package com.tequila.tumcalendarcontrol.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOwnedCalendarContractTest {
    @Test
    fun `可见事件描述不包含来源UID或实例键`() {
        val syntheticUid = "synthetic-uid@example.test"
        val syntheticInstanceKey = "2026-10-13T14:45:00Z"

        val description = AppOwnedCalendarContract.eventDescription()

        assertTrue(description.contains("TUM 日历控制"))
        assertFalse(description.contains(syntheticUid))
        assertFalse(description.contains(syntheticInstanceKey))
    }
}
