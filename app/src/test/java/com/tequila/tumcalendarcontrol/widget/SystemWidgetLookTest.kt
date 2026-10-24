/*
 * Aufgabe: „Systemaussehen folgen“ prüfen: Blockgeometrie passend zu Systemkarten (2×2, 4×2, 1×1-Untergrenze),
 *   Rückfall des Systemradius, Wahl des Hintergrunds je Radius sowie Speichern des Schalters mit erhaltenen manuellen Werten.
 * Ein-/Ausgabe: synthetische Größen, Radien und Robolectric-Präferenzen -> Geometrie, Ressourcen-IDs, gespeicherte Werte.
 * Nebenwirkungen: Nur private Test-Präferenzen.
 * Nicht zuständig: Einstellungsoberfläche (WidgetSettingsPagesTest) oder prozentuale Blöcke (WidgetBlockSettingsTest).
 */
package com.tequila.tumcalendarcontrol.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tequila.tumcalendarcontrol.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SystemWidgetLookTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** HONOR 2×2-Feld 155×211 dp -> 149×170 dp wie die Systemkarte (150×170 dp); 4×2 320×211 -> 314×170. */
    @Test fun `Block passend zu Systemkarten`() {
        val square = SystemWidgetLook.preset(155, 211)
        assertEquals(149, square.widthDp)
        assertEquals(170, square.heightDp)
        assertEquals(3f, square.leftDp)
        assertEquals(19.5f, square.topDp)
        assertEquals(21.5f, square.bottomDp)
        val wide = SystemWidgetLook.preset(320, 211)
        assertEquals(314, wide.widthDp)
        assertEquals(170, wide.heightDp)
    }

    /** 1×1 (73×101 dp): Höhe nicht unter 60 %, Rest oben:unten im Verhältnis 19,5:21,5. */
    @Test fun `kleines Feld behält mindestens 60 Prozent`() {
        val small = SystemWidgetLook.preset(73, 101)
        assertEquals(67, small.widthDp)
        assertEquals(61, small.heightDp)
        assertEquals(101 * .4f, small.topDp + small.bottomDp, .01f)
        assertEquals(19.5f / 21.5f, small.topDp / small.bottomDp, .001f)
    }

    @Test fun `Schalter nutzt Systemgeometrie statt Prozent und Quadrat`() {
        val manual = WidgetSettings(blockWidthPercent = 60, blockHeightPercent = 60, square = true)
        assertEquals(SystemWidgetLook.preset(155, 211), ScheduleWidgetRenderer.block(manual.copy(followSystem = true), 155, 211))
        assertEquals(93, ScheduleWidgetRenderer.block(manual, 155, 211).widthDp)
    }

    @Test fun `Systemradius mit Rückfall`() {
        assertNull("vor Android 12", SystemWidgetLook.radiusDp(30, 2.55f) { 58.65f })
        assertNull("Wert 0", SystemWidgetLook.radiusDp(36, 2.55f) { 0f })
        assertNull("Lesefehler", SystemWidgetLook.radiusDp(36, 2.55f) { error("keine Ressource") })
        assertEquals(23, SystemWidgetLook.radiusDp(36, 2.55f) { 58.65f })
        assertEquals("auf 32 dp begrenzt", 32, SystemWidgetLook.radiusDp(36, 2f) { 100f })
    }

    @Test fun `Hintergrund je Radius`() {
        val follow = WidgetSettings(followSystem = true, corner = WidgetCorner.SMALL)
        assertEquals(R.drawable.widget_bg_solid_r23, ScheduleWidgetRenderer.background(follow, 23))
        assertEquals(R.drawable.widget_bg_glass_r23, ScheduleWidgetRenderer.background(follow.copy(translucent = true), 23))
        assertEquals("System ohne Wert: manuelle Ecke", R.drawable.widget_bg_solid_r8, ScheduleWidgetRenderer.background(follow, null))
        assertEquals("Schalter aus: Systemwert ignoriert", R.drawable.widget_bg_solid_r8, ScheduleWidgetRenderer.background(follow.copy(followSystem = false), 23))
        assertEquals(WidgetBackgroundTable.MAX_RADIUS_DP + 1, WidgetBackgroundTable.solid.size)
        assertEquals(WidgetBackgroundTable.MAX_RADIUS_DP + 1, WidgetBackgroundTable.glass.toSet().size)
    }

    @Test fun `Schalter wird gespeichert, manuelle Werte bleiben`() {
        val preferences = WidgetPreferences(context)
        assertFalse("ältere Daten: aus", preferences.load(31, WidgetSize.MEDIUM).followSystem)
        val manual = WidgetSettings(corner = WidgetCorner.ROUND, blockWidthPercent = 80, blockHeightPercent = 70, square = true)
        preferences.save(31, manual.copy(followSystem = true))
        val loaded = preferences.load(31, WidgetSize.MEDIUM)
        assertTrue(loaded.followSystem)
        assertEquals(manual, loaded.copy(followSystem = false))
        preferences.save(WidgetSettings(followSystem = true))
        assertTrue("neue Widgets erben den Standard", preferences.load(32, WidgetSize.SMALL).followSystem)
    }
}
