/*
 * Aufgabe: Einstellungen je Widget und Blockgröße prüfen: erste Werte aus dem Standard (klein/Countdown quadratisch),
 *   eigene Werte unabhängig vom Standard, Löschen beim Entfernen, Bereichsgrenzen, Blockgeometrie und Rand im Widget.
 * Ein-/Ausgabe: Robolectric-Präferenzen und synthetische Größen -> gespeicherte Werte, Geometrie, angewandte Ränder.
 * Nebenwirkungen: Nur private Test-Präferenzen.
 * Nicht zuständig: Einstellungsoberfläche (WidgetSettingsPagesTest).
 */
package com.tequila.tumcalendarcontrol.widget

import android.content.Context
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.tequila.tumcalendarcontrol.R
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetBlockSettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `erste Werte aus dem Standard, danach unabhängig`() {
        val preferences = WidgetPreferences(context)
        preferences.save(WidgetSettings(corner = WidgetCorner.ROUND, translucent = true))
        val small = preferences.load(11, WidgetSize.SMALL)
        assertTrue("klein startet quadratisch", small.square)
        assertEquals(WidgetCorner.ROUND, small.corner)
        assertFalse("mittel nicht quadratisch", preferences.load(12, WidgetSize.MEDIUM).square)
        preferences.save(11, small.copy(blockWidthPercent = 80))
        preferences.save(WidgetSettings(corner = WidgetCorner.SQUARE))
        assertEquals("Standardänderung ändert vorhandene nicht", WidgetCorner.ROUND, preferences.load(11, WidgetSize.SMALL).corner)
        assertEquals(80, preferences.load(11, WidgetSize.SMALL).blockWidthPercent)
        preferences.remove(11)
        assertEquals("nach Entfernen wieder vom Standard", WidgetCorner.SQUARE, preferences.load(11, WidgetSize.SMALL).corner)
        assertEquals(WidgetCorner.ROUND, preferences.load(12, WidgetSize.MEDIUM).corner)
    }

    @Test fun `Prozent werden auf den Bereich begrenzt`() {
        val preferences = WidgetPreferences(context)
        preferences.save(21, WidgetSettings(blockWidthPercent = 10, blockHeightPercent = 300))
        val loaded = preferences.load(21, WidgetSize.MEDIUM)
        assertEquals(60, loaded.blockWidthPercent)
        assertEquals(100, loaded.blockHeightPercent)
    }

    /** HONOR-2×2 (155×211 dp): Quadrat 155×155, oben und unten je 28 dp Rand; Prozent wirken je Achse. */
    @Test fun `Blockgeometrie`() {
        assertEquals(ScheduleWidgetRenderer.BlockGeometry(155, 155, 0, 28), ScheduleWidgetRenderer.block(WidgetSettings(square = true), 155, 211))
        assertEquals(ScheduleWidgetRenderer.BlockGeometry(256, 126, 32, 42), ScheduleWidgetRenderer.block(WidgetSettings(blockWidthPercent = 80, blockHeightPercent = 60), 320, 211))
        assertEquals(ScheduleWidgetRenderer.BlockGeometry(320, 211, 0, 0), ScheduleWidgetRenderer.block(WidgetSettings(), 320, 211))
    }

    @Test fun `Rand wird am äußeren Rahmen gesetzt`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val frame = WidgetFrame(emptyList(), false, WidgetSettings(square = true), Instant.parse("2026-10-21T08:30:00Z"), ZoneId.of("Europe/Berlin"), 155, 211)
        val view = ScheduleWidgetRenderer.render(activity, WidgetSize.SMALL, frame).apply(activity, FrameLayout(activity))
        val outer = view.findViewById<android.view.View>(R.id.widget_frame)
        val density = activity.resources.displayMetrics.density
        assertEquals((28 * density).toInt(), outer.paddingTop)
        assertEquals(0, outer.paddingLeft)
    }
}
