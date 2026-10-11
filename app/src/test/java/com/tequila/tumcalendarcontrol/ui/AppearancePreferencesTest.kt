/*
 * Aufgabe: synthetische Regressionstests für AppearancePreferences.
 * Ein-/Ausgabe: künstliche Zustände und Fixtures -> JUnit-Prüfergebnisse.
 * Nebenwirkungen: nur lokaler Testprozess und gegebenenfalls Robolectric-Präferenzen.
 * Nicht zuständig: echte Konten, private Quellen, Geräte oder reale Kalenderausgaben.
 */
package com.tequila.tumcalendarcontrol.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppearancePreferencesTest {
    @Test fun preferencesSurviveNewStoreAndState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expected = AppearanceSettings(ThemeMode.DARK, LightPaletteStyle.WARM_WHITE, DarkPaletteStyle.DEEP_BLUE)
        AppearancePreferences(context).save(expected)
        assertEquals(expected, CalendarControlState(initialAppearance = AppearancePreferences(context).load()).appearanceSettings)
    }
    @Test fun unknownPreferencesUseSafeDefaults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString("mode", "future").putString("dark", "future").putString("light", "future").commit()
        assertEquals(AppearanceSettings(), AppearancePreferences(context).load())
    }
    @Test fun modesHaveIndependentSavedPalettesAndKeepAttendanceColors() {
        LightPaletteStyle.entries.forEach { light -> DarkPaletteStyle.entries.forEach { dark ->
            listOf(false, true).forEach { isDark ->
                val palette = appearancePalette(isDark, light, dark)
                assertEquals(TumPalettes.dark.green, palette.green)
                assertEquals(TumPalettes.dark.blue, palette.blue)
                assertEquals(TumPalettes.dark.gray, palette.gray)
            }
        } }
        assertEquals(Color(0xFF10151C), appearancePalette(true, LightPaletteStyle.WARM_WHITE, DarkPaletteStyle.DEEP_BLUE).surface)
        assertEquals(Color(0xFF1B1B1B), appearancePalette(true, LightPaletteStyle.WARM_WHITE, DarkPaletteStyle.NEUTRAL_BLACK).surface)
        assertEquals(WarmWhitePalette, appearancePalette(false, LightPaletteStyle.WARM_WHITE, DarkPaletteStyle.DEEP_BLUE))
    }
    @Test fun systemModeResolvesSeparateLightAndDarkChoices() {
        val settings = AppearanceSettings(light = LightPaletteStyle.WARM_WHITE, dark = DarkPaletteStyle.DEEP_BLUE)
        assertEquals(WarmWhitePalette, appearancePalette(settings.mode.usesDarkTheme(false), settings.light, settings.dark))
        assertEquals(DeepBluePalette, appearancePalette(settings.mode.usesDarkTheme(true), settings.light, settings.dark))
    }
    @Test fun directoryArrowUsesRightToDownRotation() {
        assertEquals(0f, directoryArrowRotation(false))
        assertEquals(90f, directoryArrowRotation(true))
    }
}
