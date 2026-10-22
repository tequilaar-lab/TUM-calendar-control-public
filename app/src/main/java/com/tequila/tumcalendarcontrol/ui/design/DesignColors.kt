/*
 * Aufgabe: Farbrollen des neuen Erscheinungsbilds (Papier, Tinte, Akzent, Flächen, Status) je Farbschema bereitstellen.
 * Ein-/Ausgabe: Hell/Dunkel und gewähltes Farbschema -> unveränderliche Farbrollen über LocalDesignColors.
 * Nebenwirkungen: Keine; reine Farbdefinitionen.
 * Nicht zuständig: Teilnahmefarben (Grün/Blau/Grau bleiben in TumPalette), Miuix-Farbschema oder Speicherung.
 */
package com.tequila.tumcalendarcontrol.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.tequila.tumcalendarcontrol.ui.DarkPaletteStyle
import com.tequila.tumcalendarcontrol.ui.LightPaletteStyle

/**
 * Semantische Rollen statt Einzelfarben: Seiten verwenden nur diese Rollen.
 * Akzent ist für Hauptaktion, Marke und aktive Navigation reserviert; Auswahlsteuerelemente nutzen Tinte,
 * damit Blau nicht mit „Teilnahme auswählen“ verwechselt wird.
 */
@Immutable
data class DesignColors(
    val paper: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val wash: Color,
    val floating: Color,
    val success: Color,
    val danger: Color,
    val isDark: Boolean,
)

/** Vier Farbschemata; Warmweiß und Tintenschwarz entsprechen den bestätigten Entwürfen. */
object DesignPalettes {
    val warmWhite = DesignColors(
        paper = Color(0xFFF6F5F1), ink = Color(0xFF1D2B34), muted = Color(0xFF657078), accent = Color(0xFF0065A8),
        wash = Color(0xFFEAEFED), floating = Color(0xFFFFFEFA), success = Color(0xFF237A42), danger = Color(0xFF9E3E35), isDark = false,
    )
    val coolWhite = DesignColors(
        paper = Color(0xFFF2F5F7), ink = Color(0xFF1B2A33), muted = Color(0xFF64717A), accent = Color(0xFF0065A8),
        wash = Color(0xFFE6ECF0), floating = Color(0xFFFCFDFE), success = Color(0xFF237A42), danger = Color(0xFF9E3E35), isDark = false,
    )
    val inkBlack = DesignColors(
        paper = Color(0xFF1B1D1E), ink = Color(0xFFE8E9E6), muted = Color(0xFFA0A8AB), accent = Color(0xFF88B9D9),
        wash = Color(0xFF252B2F), floating = Color(0xFF2A2E30), success = Color(0xFF6DD875), danger = Color(0xFFE9A096), isDark = true,
    )
    val deepBlue = DesignColors(
        paper = Color(0xFF101824), ink = Color(0xFFE3E9F0), muted = Color(0xFF9AA8B8), accent = Color(0xFF8DB8E0),
        wash = Color(0xFF1A2433), floating = Color(0xFF212C3C), success = Color(0xFF6DD875), danger = Color(0xFFE9A096), isDark = true,
    )
}

/** Gleiche Auswahllogik wie appearancePalette: Hell- und Dunkelschema werden getrennt gemerkt. */
fun designColorsFor(isDark: Boolean, light: LightPaletteStyle, dark: DarkPaletteStyle): DesignColors =
    if (isDark) {
        if (dark == DarkPaletteStyle.DEEP_BLUE) DesignPalettes.deepBlue else DesignPalettes.inkBlack
    } else {
        if (light == LightPaletteStyle.WARM_WHITE) DesignPalettes.warmWhite else DesignPalettes.coolWhite
    }

val LocalDesignColors = staticCompositionLocalOf { DesignPalettes.coolWhite }
