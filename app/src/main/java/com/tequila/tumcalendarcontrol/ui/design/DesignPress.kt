/*
 * Aufgabe: Druckrückmeldung und Aufklappen im Designsystem: abgerundeter Schwebeblock beim Drücken statt Rechteckblitz,
 *   Aufklappzeile mit drehendem Pfeil und schubladenartig aufgehender Inhalt.
 * Ein-/Ausgabe: Klick-/Aufklapprückrufe und Inhalte -> Modifier bzw. Zeile und animierter Bereich.
 * Nebenwirkungen: Kurze haptische Rückmeldung über LocalDesignHaptics.
 * Nicht zuständig: Fachaktionen, Navigation oder Speicherung des Aufklappzustands.
 */
package com.tequila.tumcalendarcontrol.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon

/**
 * Klickbare Fläche, die beim Drücken als abgerundeter Schwebeblock hervortritt (wie die Auswahl in Navigation und
 * Segmenten) statt eines rechteckigen Aufblitzens; dazu Haptik.
 */
fun Modifier.designPressable(
    event: HapticEvent? = HapticEvent.SELECTION,
    corner: Dp = DesignRadii.Tile,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val haptics = LocalDesignHaptics.current
    val colors = LocalDesignColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val progress by animateFloatAsState(if (pressed) 1f else 0f, DesignMotion.select(), label = "press-block")
    designSelectionBlock(colors, corner, progress * .9f)
        .clickable(interaction, indication = null, role = role) { event?.let(haptics::perform); onClick() }
}

/**
 * Aufklappzeile: Pfeil zeigt geschlossen nach links und dreht beim Öffnen 90° gegen den Uhrzeigersinn nach unten;
 * Druck als Schwebeblock.
 */
@Composable
fun DesignDisclosureRow(title: String, expanded: Boolean, modifier: Modifier = Modifier, detail: String = "", onToggle: () -> Unit) {
    val colors = LocalDesignColors.current
    val rotation by animateFloatAsState(if (expanded) -90f else 0f, DesignMotion.enter(), label = "disclosure-arrow")
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).designPressable(role = Role.Button, onClick = onToggle).padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            DesignText(title, 15)
            if (detail.isNotEmpty()) DesignText(detail, 11, muted = true)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp).rotate(rotation).testTag("disclosure-arrow"))
    }
}

/** Inhalt, der sich wie eine Schublade von oben aufzieht (Höhe wächst, Inhalt blendet ein) und schneller schließt. */
@Composable
fun DesignExpandable(visible: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible = visible, modifier = modifier,
        enter = expandVertically(DesignMotion.enter(), expandFrom = Alignment.Top) + fadeIn(DesignMotion.enter()),
        exit = shrinkVertically(DesignMotion.exit(), shrinkTowards = Alignment.Top) + fadeOut(DesignMotion.exit()),
    ) { Column(content = content) }
}
