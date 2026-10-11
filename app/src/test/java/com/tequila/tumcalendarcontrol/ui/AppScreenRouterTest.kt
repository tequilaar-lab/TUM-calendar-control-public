/*
 * Aufgabe: synthetische Regressionstests für AppScreenRouter.
 * Ein-/Ausgabe: künstliche Zustände und Fixtures -> JUnit-Prüfergebnisse.
 * Nebenwirkungen: nur lokaler Testprozess und gegebenenfalls Robolectric-Präferenzen.
 * Nicht zuständig: echte Konten, private Quellen, Geräte oder reale Kalenderausgaben.
 */
package com.tequila.tumcalendarcontrol.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppScreenRouterTest {
    @Test
    fun `every stable screen has an explicit content route`() {
        assertEquals(AppScreen.entries.toSet(), routedAppScreens)
    }
}
