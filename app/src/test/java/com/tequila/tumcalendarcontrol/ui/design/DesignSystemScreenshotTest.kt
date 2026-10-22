/*
 * Aufgabe: Bildbasis der neuen Designbausteine je Farbschema und für die deckende Stufe „Aus“ erzeugen.
 * Ein-/Ausgabe: DesignGallery in TumCalendarTheme -> PNG-Dateien unter src/test/screenshots/design_*.png.
 * Nebenwirkungen: Nur lokale Testbilder.
 * Nicht zuständig: Bestehende Seiten (deren Bilder bleiben unverändert), echte Geräte oder Bewegungsverhalten.
 */
package com.tequila.tumcalendarcontrol.ui.design

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import com.github.takahirom.roborazzi.captureRoboImage
import com.tequila.tumcalendarcontrol.ui.DarkPaletteStyle
import com.tequila.tumcalendarcontrol.ui.LightPaletteStyle
import com.tequila.tumcalendarcontrol.ui.ThemeMode
import com.tequila.tumcalendarcontrol.ui.TumCalendarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
class DesignSystemScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun render(name: String, mode: ThemeMode, light: LightPaletteStyle, dark: DarkPaletteStyle, level: GlassLevel = GlassLevel.STANDARD) {
        compose.setContent {
            TumCalendarTheme(mode, light, dark) {
                CompositionLocalProvider(LocalGlassLevel provides level) { DesignGallery() }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("design-floating").assertIsDisplayed()
        compose.onNodeWithTag("design-navigation").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun warmWhite() = render("design_gallery_warm_white_412x892", ThemeMode.LIGHT, LightPaletteStyle.WARM_WHITE, DarkPaletteStyle.NEUTRAL_BLACK)

    @Test fun coolWhite() = render("design_gallery_cool_white_412x892", ThemeMode.LIGHT, LightPaletteStyle.COOL_WHITE, DarkPaletteStyle.NEUTRAL_BLACK)

    @Test fun inkBlack() = render("design_gallery_ink_black_412x892", ThemeMode.DARK, LightPaletteStyle.WARM_WHITE, DarkPaletteStyle.NEUTRAL_BLACK)

    @Test fun deepBlue() = render("design_gallery_deep_blue_412x892", ThemeMode.DARK, LightPaletteStyle.WARM_WHITE, DarkPaletteStyle.DEEP_BLUE)

    /** „Aus“: deckende Flächen und vollfarbige Hauptschaltfläche (entspricht auch Geräten unter Android 12). */
    @Test fun glassOff() = render("design_gallery_glass_off_412x892", ThemeMode.LIGHT, LightPaletteStyle.WARM_WHITE, DarkPaletteStyle.NEUTRAL_BLACK, GlassLevel.OFF)
}
