/*
 * Aufgabe: Schwebende Glaselemente: Hauptschaltfläche (Miuix Button), Seite mit schwebender Hauptaktion
 *   und untere Navigation (Miuix FloatingNavigationBar).
 * Ein-/Ausgabe: Beschriftungen, Symbole, Auswahl und Rückrufe -> Glasflächen über der Inhaltsebene der Seite.
 * Nebenwirkungen: Keine; Rückrufe lösen Aktionen aus.
 * Nicht zuständig: Seitenlayout, Abtastebenen (GlassSurfaces/Seitenrahmen) oder Fachaktionen.
 */
package com.tequila.tumcalendarcontrol.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.Icon

/**
 * Hauptaktion als schwebende Glaspille mit hellblauer Tönung und blauer, halbfetter Schrift (wie iOS-26-Werkzeugleisten);
 * weiße Schrift auf halbtransparentem Blau erreicht 4,5:1 nicht. Bei „Aus“ deckend blau mit heller Schrift.
 * Die Lichtkante entfällt auf Wunsch des Nutzers; Unschärfe, Brechung, Tönung und Schatten bleiben.
 */
@Composable
fun DesignFloatingButton(label: String, enabled: Boolean, modifier: Modifier = Modifier, secondary: Boolean = false, onClick: () -> Unit) {
    val colors = LocalDesignColors.current
    val haptics = LocalDesignHaptics.current
    val spec = glassSpec(LocalGlassLevel.current, LocalGlassSupport.current)
    val solid = !spec.enabled || LocalGlassBackdrop.current == null
    val surface = when {
        solid && enabled && !secondary -> Modifier.designGlass(colors, DesignRadii.Pill, null, spec, tint = colors.accent)
        solid -> Modifier.designGlass(colors, DesignRadii.Pill, null, spec)
        // Glas mit eigenem weichen Schatten, damit die Schaltfläche wie die übrigen Schwebeblöcke abgehoben wirkt.
        else -> Modifier.designGlow(DesignRadii.Pill, colors, strength = .8f).designGlass(colors, DesignRadii.Pill, LocalGlassBackdrop.current, spec, tintScale = 1.15f,
            accent = if (enabled && !secondary) colors.accent.copy(alpha = if (colors.isDark) .16f else .10f) else null)
    }
    val ink = when { !enabled -> colors.muted; secondary -> colors.ink; solid -> colors.paper; else -> colors.accent }
    Button(
        onClick = { haptics.perform(HapticEvent.ACTION); onClick() }, enabled = enabled, cornerRadius = DesignRadii.Pill, minHeight = 56.dp,
        modifier = modifier.heightIn(min = 56.dp).then(surface),
        colors = ButtonDefaults.buttonColors(color = Color.Transparent, disabledColor = Color.Transparent, contentColor = ink, disabledContentColor = colors.muted),
    ) {
        DesignText(label, 15, color = ink, weight = if (solid || secondary) FontWeight.Medium else FontWeight.SemiBold, maxLines = 1)
    }
}

/** Ein Ziel der unteren Navigation; Beschriftung bereits lokalisiert. */
data class DesignNavigationItem(val icon: ImageVector, val label: String)

/**
 * Untere Navigation: Miuix FloatingNavigationBar als Behälter mit transparenter Eigenfläche,
 * Glasfläche (Unschärfe der Inhaltsebene, Tönung, Lichtkante) über designGlass.
 * Ziele mit Symbol und Beschriftung, aktives Ziel im Akzent; dahinter ein halbtransparenter Gleitblock (ziehbar).
 * Miuix' eigene Ziele zeigen nur Symbole und unterscheiden die Auswahl lediglich über Deckkraft.
 */
@Composable
fun DesignGlassNavigation(items: List<DesignNavigationItem>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val colors = LocalDesignColors.current
    val spec = glassSpec(LocalGlassLevel.current, LocalGlassSupport.current)
    FloatingNavigationBar(
        modifier = modifier.designGlass(colors, DesignRadii.Navigation, LocalGlassBackdrop.current, spec, rim = true),
        color = Color.Transparent,
        cornerRadius = DesignRadii.Navigation,
        shadowElevation = 0.dp,
    ) {
        // Gewähltes Ziel als halbtransparenter, abgerundeter Block, der zum Ziel gleitet und sich ziehen lässt.
        DesignSlidingSelector(
            count = items.size, selected = selected, onSelect = onSelect,
            modifier = Modifier.width(64.dp * items.size).height(56.dp),
            corner = 18.dp, inset = 3.dp,
            block = { Box(Modifier.matchParentSize().clip(RoundedCornerShape(18.dp)).background(if (colors.isDark) Color.White.copy(alpha = .12f) else colors.ink.copy(alpha = .07f))) },
        ) { index, active ->
            val item = items[index]
            val tint by animatedSelectionColor(active, colors.accent, colors.muted, "nav-tint-$index")
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                DesignText(item.label, 10, color = tint, maxLines = 1)
            }
        }
    }
}

/** Platz am Ende scrollbarer Seiten, damit der letzte Inhalt über die schwebende Hauptaktion scrollen kann. */
val FloatingActionContentClearance = 120.dp

/**
 * Seite mit schwebender Glas-Hauptaktion: Inhalt scrollt unter der Aktion hindurch, am Ende bleibt Platz,
 * damit nichts verdeckt bleibt. Aktion schwebt über der Systemnavigation.
 * Ausrichtung wie auf allen Seiten: Blockkanten (Karten, Felder) bei 24 dp, freier Text 4 dp weiter innen auf der Titellinie.
 * Eigene Abtastebene nur für den Seiteninhalt: Läge die Aktion in der Ebene, die sie abtastet (Inhaltsebene
 * des Rahmens), zeichnete sie sich rekursiv selbst (nativer Absturz beim Rendern).
 */
@Composable
fun DesignActionPage(
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionEnabled: Boolean = true,
    actionTag: String = "floating-action",
    contentTag: String = "action-page-content",
    content: @Composable ColumnScope.() -> Unit,
) {
    DesignActionFrame(actionLabel, onAction, modifier, actionEnabled, actionTag) {
        Column(
            Modifier.fillMaxSize().testTag(contentTag).verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp).padding(bottom = FloatingActionContentClearance),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/**
 * Rahmen mit schwebender Glas-Hauptaktion für beliebigen Inhalt (z. B. LazyColumn): Inhalt liegt in einer eigenen
 * Abtastebene auf Papiergrund und muss selbst FloatingActionContentClearance als unteren Abstand vorsehen.
 */
@Composable
fun DesignActionFrame(
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionEnabled: Boolean = true,
    actionTag: String = "floating-action",
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
    secondaryTag: String = "floating-secondary",
    content: @Composable BoxScope.() -> Unit,
) {
    val pageLayer = rememberLayerBackdrop()
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(pageLayer).background(LocalDesignColors.current.paper), content = content)
        CompositionLocalProvider(LocalGlassBackdrop provides pageLayer) {
            // Optionale Nebenaktion links (ca. 40 %) gleitet heraus; die Hauptaktion rückt dafür nach rechts zusammen.
            BoxWithConstraints(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 16.dp).widthIn(max = 420.dp).fillMaxWidth(),
            ) {
                val secondaryWidth = maxWidth * .4f
                var lastSecondary by remember { mutableStateOf(secondaryLabel.orEmpty()) }
                if (secondaryLabel != null) lastSecondary = secondaryLabel
                Row(Modifier.fillMaxWidth()) {
                    AnimatedVisibility(
                        visible = secondaryLabel != null,
                        enter = expandHorizontally(DesignMotion.enter()) + fadeIn(DesignMotion.enter()),
                        exit = shrinkHorizontally(DesignMotion.exit()) + fadeOut(DesignMotion.exit()),
                    ) {
                        DesignFloatingButton(lastSecondary, true, Modifier.width(secondaryWidth).padding(end = 10.dp).testTag(secondaryTag), secondary = true, onClick = onSecondary)
                    }
                    DesignFloatingButton(actionLabel, actionEnabled, Modifier.weight(1f).testTag(actionTag), onClick = onAction)
                }
            }
        }
    }
}
