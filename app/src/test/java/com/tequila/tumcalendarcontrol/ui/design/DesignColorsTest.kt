/*
 * Aufgabe: Farbschema-Zuordnung und Mindestkontraste der Farbrollen des neuen Erscheinungsbilds prüfen.
 * Ein-/Ausgabe: Hell/Dunkel und Schema -> Rollen; Rollen -> WCAG-Kontrastverhältnisse.
 * Nebenwirkungen: Keine; reiner JVM-Test.
 * Nicht zuständig: Bildvergleich, Teilnahmefarben oder Miuix-Farbschema.
 */
package com.tequila.tumcalendarcontrol.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.tequila.tumcalendarcontrol.ui.DarkPaletteStyle
import com.tequila.tumcalendarcontrol.ui.LightPaletteStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignColorsTest {
    private val all = listOf(DesignPalettes.warmWhite, DesignPalettes.coolWhite, DesignPalettes.inkBlack, DesignPalettes.deepBlue)

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance().toDouble(), b.luminance().toDouble()).sortedDescending()
        return (light + .05) / (dark + .05)
    }

    @Test fun styleSelectionMatchesSavedAppearance() {
        assertEquals(DesignPalettes.warmWhite, designColorsFor(false, LightPaletteStyle.WARM_WHITE, DarkPaletteStyle.DEEP_BLUE))
        assertEquals(DesignPalettes.coolWhite, designColorsFor(false, LightPaletteStyle.COOL_WHITE, DarkPaletteStyle.DEEP_BLUE))
        assertEquals(DesignPalettes.deepBlue, designColorsFor(true, LightPaletteStyle.WARM_WHITE, DarkPaletteStyle.DEEP_BLUE))
        assertEquals(DesignPalettes.inkBlack, designColorsFor(true, LightPaletteStyle.WARM_WHITE, DarkPaletteStyle.NEUTRAL_BLACK))
    }

    /** Jede Rolle, die Worte trägt, erreicht auf Papier und auf schwebenden Flächen mindestens 4,5:1; Haupttext 7:1. */
    @Test fun textRolesKeepReadableContrast() {
        all.forEach { c ->
            assertTrue("Tinte ${c}", contrast(c.ink, c.paper) >= 7.0)
            listOf(c.muted, c.accent, c.success, c.danger).forEach { role ->
                assertTrue("Rolle $role auf Papier", contrast(role, c.paper) >= 4.5)
            }
            // Glasschaltfläche: Akzentschrift auf schwebender Fläche.
            assertTrue("Akzent auf Fläche ${c.accent}", contrast(c.accent, c.floating) >= 4.5)
        }
    }

    @Test fun darkFlagMatchesPaperBrightness() {
        all.forEach { assertEquals(it.paper.luminance() < .5f, it.isDark) }
    }
}
