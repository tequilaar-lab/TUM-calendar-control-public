/*
 * Aufgabe: Begrenzte helle/dunkle Farbpaletten mit Farbmuster darstellen.
 * Ein-/Ausgabe: Gespeicherte Paletten und Auswahlrückrufe -> helle/dunkle Farbmuster.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Systemthema, Teilnahmefarben, Kalenderinhalte oder Hintergrundbildfarben.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AppearancePaletteSelector(state: CalendarControlState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr(message(185)), style = MiuixTheme.textStyles.title3)
        Text(tr(message(186)), color = LocalTumPalette.current.onSurfaceVariant)
        LightPaletteStyle.entries.forEach { style ->
            PaletteChoice(style.label, appearancePalette(false, style, state.darkPaletteStyle), state.lightPaletteStyle == style) { state.lightPaletteStyle = style }
        }
        Text(tr(message(187)), style = MiuixTheme.textStyles.title3)
        DarkPaletteStyle.entries.forEach { style ->
            PaletteChoice(style.label, appearancePalette(true, state.lightPaletteStyle, style), state.darkPaletteStyle == style) { state.darkPaletteStyle = style }
        }
    }
}

@Composable
private fun PaletteChoice(label: String, palette: TumPalette, selected: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), insideMargin = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioButton(selected = selected, onClick = onClick)
            Box(Modifier.size(28.dp).background(palette.surface, CircleShape).border(1.dp, LocalTumPalette.current.outline, CircleShape))
            Text(tr(label), modifier = Modifier.weight(1f))
            if (selected) Text(tr(message(188)), color = LocalTumPalette.current.primary)
        }
    }
}
