/*
 * Aufgabe: Schwebende Glaselemente: Hauptschaltfläche (Miuix Button) und untere Navigation (Miuix FloatingNavigationBar).
 * Ein-/Ausgabe: Beschriftungen, Symbole, Auswahl und Rückrufe -> Glasflächen über der Inhaltsebene der Seite.
 * Nebenwirkungen: Keine; Rückrufe lösen Aktionen aus.
 * Nicht zuständig: Seitenlayout, Abtastebenen (GlassSurfaces/Seitenrahmen) oder Fachaktionen.
 */
package com.tequila.tumcalendarcontrol.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
fun DesignFloatingButton(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalDesignColors.current
    val spec = glassSpec(LocalGlassLevel.current, LocalGlassSupport.current)
    val solid = !spec.enabled || LocalGlassBackdrop.current == null
    val surface = when {
        solid && enabled -> Modifier.designGlass(colors, DesignRadii.Pill, null, spec, tint = colors.accent)
        solid -> Modifier.designGlass(colors, DesignRadii.Pill, null, spec)
        else -> Modifier.designGlass(colors, DesignRadii.Pill, LocalGlassBackdrop.current, spec, tintScale = 1.15f,
            accent = if (enabled) colors.accent.copy(alpha = if (colors.isDark) .16f else .10f) else null)
    }
    val ink = when { !enabled -> colors.muted; solid -> colors.paper; else -> colors.accent }
    Button(
        onClick = onClick, enabled = enabled, cornerRadius = DesignRadii.Pill, minHeight = 56.dp,
        modifier = modifier.heightIn(min = 56.dp).then(surface),
        colors = ButtonDefaults.buttonColors(color = Color.Transparent, disabledColor = Color.Transparent, contentColor = ink, disabledContentColor = colors.muted),
    ) {
        DesignText(label, 15, color = ink, weight = if (solid) FontWeight.Medium else FontWeight.SemiBold)
    }
}

/** Ein Ziel der unteren Navigation; Beschriftung bereits lokalisiert. */
data class DesignNavigationItem(val icon: ImageVector, val label: String)

/**
 * Untere Navigation: Miuix FloatingNavigationBar als Behälter mit transparenter Eigenfläche,
 * Glasfläche (Unschärfe der Inhaltsebene, Tönung, Lichtkante) über designGlass.
 * Ziele mit Symbol und Beschriftung, aktives Ziel im Akzent wie im bestätigten Entwurf;
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
        items.forEachIndexed { index, item ->
            val active = index == selected
            val tint = if (active) colors.accent else colors.muted
            Column(
                Modifier.width(64.dp).heightIn(min = 52.dp)
                    .selectable(active, role = Role.Tab, onClick = { onSelect(index) }),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                DesignText(item.label, 10, color = tint, maxLines = 1)
            }
        }
    }
}
