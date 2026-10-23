/*
 * Aufgabe: Helle bzw. dunkle Farbpaletten als Farbfelder mit Häkchen zur Auswahl darstellen.
 * Ein-/Ausgabe: Gewählte Paletten und Auswahlrückruf -> zwei Farbfelder je Modus.
 * Nebenwirkungen: Keine; Auswahl über Rückruf (Zustand speichert sofort).
 * Nicht zuständig: Systemthema, Teilnahmefarben, Glasstufe oder Kalenderinhalte.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.ui.design.hapticSelectable
import com.tequila.tumcalendarcontrol.ui.design.animatedSelectionColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.ui.design.DesignColors
import com.tequila.tumcalendarcontrol.ui.design.DesignRadii
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors
import com.tequila.tumcalendarcontrol.ui.design.designColorsFor

/** Zwei Paletten eines Modus nebeneinander; Feld zeigt das Papier der Palette, gewähltes mit Tintenrahmen und Häkchen. */
@Composable
internal fun PaletteTiles(dark: Boolean, state: CalendarControlState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (dark) {
            DarkPaletteStyle.entries.forEach { style ->
                PaletteTile(tr(style.label), designColorsFor(true, state.lightPaletteStyle, style), state.darkPaletteStyle == style, Modifier.weight(1f).testTag("palette-${style.name}")) {
                    state.darkPaletteStyle = style
                }
            }
        } else {
            LightPaletteStyle.entries.forEach { style ->
                PaletteTile(tr(style.label), designColorsFor(false, style, state.darkPaletteStyle), state.lightPaletteStyle == style, Modifier.weight(1f).testTag("palette-${style.name}")) {
                    state.lightPaletteStyle = style
                }
            }
        }
    }
}

@Composable
private fun PaletteTile(label: String, palette: DesignColors, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val current = LocalDesignColors.current
    val shape = RoundedCornerShape(DesignRadii.Field)
    val borderColor by animatedSelectionColor(selected, current.ink, current.ink.copy(alpha = .14f), "palette-$label")
    Box(
        modifier.height(72.dp).clip(shape).background(palette.paper)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .hapticSelectable(selected, Role.RadioButton, onClick).padding(14.dp),
    ) {
        DesignText(label, 14, Modifier.align(Alignment.BottomStart), color = palette.ink, weight = if (selected) FontWeight.Medium else FontWeight.Normal)
        if (selected) DesignText("✓", 16, Modifier.align(Alignment.TopEnd), color = palette.ink)
    }
}
