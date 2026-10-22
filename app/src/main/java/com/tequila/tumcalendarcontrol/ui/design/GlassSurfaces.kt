/*
 * Aufgabe: Schwebende Oberflächen zeichnen: weiche Schatten, Glasflächen (backdrop), Seiten-Abtastebenen und Glanzkanten.
 * Ein-/Ausgabe: Farbrollen, Radius, Abtastebene und Glasstufe -> Modifier für Navigation, Schaltflächen, Dialoge und Karten.
 * Nebenwirkungen: Keine; nur Zeichnen.
 * Nicht zuständig: Komponentenaufbau (DesignFloating/DesignBlocks), Stufenwahl oder Speicherung.
 */
package com.tequila.tumcalendarcontrol.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight

/** Inhaltsebene einer Seite, aus der schwebende Glasflächen Unschärfe und Brechung abtasten. */
val LocalGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Ganze Seite inklusive Kopf und schwebender Elemente; nur Bestätigungsdialoge tasten diese Ebene ab. */
val LocalGlassHostBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * Weicher, rundum gleichmäßiger Schatten in Tintenrichtung statt reinem Schwarz:
 * großer Umgebungsschatten für das Schweben, kleiner Kontaktschatten für eine klare Kante.
 * strength < 1 für Inhaltskarten, damit sie nicht wie ein Tablett wirken.
 */
fun Modifier.designGlow(corner: Dp, colors: DesignColors, strength: Float = 1f): Modifier {
    val shape = RoundedCornerShape(corner)
    val tone = if (colors.isDark) Color.Black else colors.ink
    return dropShadow(shape, Shadow(radius = 26.dp, color = tone, offset = DpOffset(0.dp, 8.dp), alpha = (if (colors.isDark) .55f else .13f) * strength))
        .dropShadow(shape, Shadow(radius = 3.dp, color = tone, offset = DpOffset(0.dp, 1.dp), alpha = (if (colors.isDark) .45f else .07f) * strength))
}

/**
 * Glasfläche: Hintergrund abtasten -> Lebendigkeit -> Unschärfe -> Linsenbrechung (je nach Gerät) -> Tönung.
 * tintScale/minAlpha erhöhen die Deckkraft für lesbare Flächen (Dialoge); accent legt eine zarte Farbe darüber.
 * rim=false verzichtet auf die helle Lichtkante der Bibliothek (Rückmeldung: wirkt an Schaltflächen störend).
 * Ohne Abtastebene oder bei „Aus“/fehlender Unterstützung entsteht eine deckende Fläche mit weichem Schatten.
 */
fun Modifier.designGlass(
    colors: DesignColors,
    corner: Dp,
    backdrop: LayerBackdrop?,
    spec: GlassSpec,
    tint: Color = colors.floating,
    tintScale: Float = 1f,
    accent: Color? = null,
    minAlpha: Float = 0f,
    rim: Boolean = false,
): Modifier {
    val shape = RoundedCornerShape(corner)
    if (backdrop == null || !spec.enabled) {
        val solid = designGlow(corner, colors).clip(shape).background(tint)
        return if (rim) solid.border(1.dp, Color.White.copy(alpha = if (colors.isDark) .10f else .75f), shape) else solid
    }
    // Auf dunklem Papier wirkt dieselbe Tönung durchsichtiger; leicht anheben, damit Symbole lesbar bleiben.
    val alpha = ((spec.tint + if (colors.isDark) .07f else 0f) * tintScale).coerceIn(minAlpha, 1f)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        highlight = if (rim) { { Highlight.Default } } else null,
        effects = {
            vibrancy()
            blur(spec.blurDp.dp.toPx())
            if (spec.lensHeightDp > 0f) lens(spec.lensHeightDp.dp.toPx(), spec.lensAmountDp.dp.toPx())
        },
        onDrawSurface = {
            drawRect(tint.copy(alpha = alpha))
            if (accent != null) drawRect(accent)
        },
    )
}

/**
 * Für Seiten mit Bestätigungsdialog anstelle einer äußeren Box: Die Seite wird als Ganzes abgetastet,
 * der Dialog liegt außerhalb dieser Ebene und tastet sich daher nicht selbst ab.
 */
@Composable
fun GlassHost(content: @Composable BoxScope.() -> Unit) {
    val host = rememberLayerBackdrop()
    CompositionLocalProvider(LocalGlassHostBackdrop provides host) { Box(Modifier.fillMaxSize(), content = content) }
}

/**
 * Statisches Glasmaterial für Steuerelemente innerhalb der Inhaltsebene: von oben nach unten auslaufende Lichtkante.
 * Diese Elemente liegen selbst in der abgetasteten Ebene und dürfen sie nicht erneut abtasten.
 */
fun Modifier.designSheen(shape: Shape, colors: DesignColors, enabled: Boolean): Modifier =
    if (!enabled) this
    else border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (colors.isDark) .22f else .95f), Color.White.copy(alpha = 0f))), shape)
