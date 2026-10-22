/*
 * Aufgabe: Glasstufen, Geräteabschwächung und Speicherung der Glaswahl prüfen.
 * Ein-/Ausgabe: API-Stufen, Stufen und Robolectric-Präferenzen -> erwartete Glasparameter und gespeicherte Werte.
 * Nebenwirkungen: Nur lokaler Testprozess und Robolectric-Präferenzen.
 * Nicht zuständig: Zeichnen der Glasflächen (Bildtests) oder echte Geräte.
 */
package com.tequila.tumcalendarcontrol.ui.design

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tequila.tumcalendarcontrol.ui.AppearancePreferences
import com.tequila.tumcalendarcontrol.ui.AppearanceSettings
import com.tequila.tumcalendarcontrol.ui.DarkPaletteStyle
import com.tequila.tumcalendarcontrol.ui.LightPaletteStyle
import com.tequila.tumcalendarcontrol.ui.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GlassLevelTest {
    // —— Geräteunterstützung ——

    @Test fun supportFollowsAndroidVersions() {
        assertEquals(GlassSupport.NONE, GlassSupport.forSdk(26))
        assertEquals(GlassSupport.NONE, GlassSupport.forSdk(30))
        assertEquals(GlassSupport.BLUR_ONLY, GlassSupport.forSdk(31))
        assertEquals(GlassSupport.BLUR_ONLY, GlassSupport.forSdk(32))
        assertEquals(GlassSupport.FULL, GlassSupport.forSdk(33))
        assertEquals(GlassSupport.FULL, GlassSupport.forSdk(36))
    }

    @Test fun offAndUnsupportedDevicesUseSolidSurfaces() {
        GlassLevel.entries.forEach { assertFalse(glassSpec(it, GlassSupport.NONE).enabled) }
        GlassSupport.entries.forEach { assertFalse(glassSpec(GlassLevel.OFF, it).enabled) }
    }

    @Test fun blurOnlyDevicesDropLensButKeepTint() {
        val spec = glassSpec(GlassLevel.CLEAR, GlassSupport.BLUR_ONLY)
        assertTrue(spec.enabled)
        assertEquals(0f, spec.lensHeightDp)
        assertEquals(GlassLevel.CLEAR.tint, spec.tint)
    }

    @Test fun levelsGetMoreTransparentInOrder() {
        val tints = listOf(GlassLevel.SOFT, GlassLevel.STANDARD, GlassLevel.CLEAR).map { glassSpec(it, GlassSupport.FULL).tint }
        assertTrue(tints[0] > tints[1] && tints[1] > tints[2])
        assertEquals(0f, glassSpec(GlassLevel.SOFT, GlassSupport.FULL).lensHeightDp)
        assertTrue(glassSpec(GlassLevel.STANDARD, GlassSupport.FULL).lensHeightDp > 0f)
    }

    // —— Speicherung ——

    @Test fun glassChoiceSurvivesNewStoreAndDefaultsToStandard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(GlassLevel.STANDARD, GlassPreferences(context).load())
        GlassPreferences(context).save(GlassLevel.CLEAR)
        assertEquals(GlassLevel.CLEAR, GlassPreferences(context).load())
    }

    @Test fun unknownStoredValueFallsBackToStandard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString("glass", "UNBEKANNT").commit()
        assertEquals(GlassLevel.STANDARD, GlassPreferences(context).load())
    }

    /** Regression: Das Speichern der übrigen Darstellung darf die Glaswahl nicht zurücksetzen. */
    @Test fun savingAppearanceKeepsGlassChoice() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        GlassPreferences(context).save(GlassLevel.SOFT)
        AppearancePreferences(context).save(AppearanceSettings(ThemeMode.DARK, LightPaletteStyle.WARM_WHITE, DarkPaletteStyle.DEEP_BLUE))
        assertEquals(GlassLevel.SOFT, GlassPreferences(context).load())
        assertEquals(DarkPaletteStyle.DEEP_BLUE, AppearancePreferences(context).load().dark)
    }
}
