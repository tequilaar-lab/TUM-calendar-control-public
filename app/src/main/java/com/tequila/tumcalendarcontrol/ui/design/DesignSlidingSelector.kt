/*
 * Aufgabe: Gemeinsame Auswahl mit gleitendem, abgerundetem Schwebeblock (untere Navigation, Segmentsteuerungen).
 * Ein-/Ausgabe: Anzahl, gewählter Index und Inhalte -> Block gleitet federnd zum Ziel; Tippen oder seitliches Ziehen wählt.
 * Nebenwirkungen: Kurze haptische Auswahlrückmeldung; Auswahl selbst über Rückruf.
 * Nicht zuständig: Inhalte der Einträge, Navigation oder Speicherung.
 *
 * Miuix bietet keinen ziehbaren Gleitblock; Beschriftungen bleiben Miuix-Text (DesignText), der Block nutzt das
 * Schwebematerial des Designsystems (designSelectionBlock).
 */
package com.tequila.tumcalendarcontrol.ui.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Schwebematerial für ausgewählte Elemente: hellste Fläche mit weichem Schatten und Glanzkante (dunkel: zarte Aufhellung).
 * progress 0..1 blendet das Material sanft ein (für Auswahlwechsel ohne Sprung).
 */
fun Modifier.designSelectionBlock(colors: DesignColors, corner: Dp, progress: Float = 1f): Modifier {
    if (progress <= 0f) return this
    val shape = RoundedCornerShape(corner)
    val fill = if (colors.isDark) Color.White.copy(alpha = .12f * progress) else colors.floating.copy(alpha = progress)
    return designGlow(corner, colors, strength = .55f * progress).clip(shape).background(fill).designSheen(shape, colors, progress > .5f)
}

/**
 * Zeile gleich breiter Einträge mit gleitendem Block hinter dem gewählten Eintrag. Ziehen bewegt den Block mit dem
 * Finger; beim Loslassen rastet er am nächsten Eintrag ein und wählt ihn. Tippen ohne Kachel-Druckeffekt.
 */
@Composable
fun DesignSlidingSelector(
    count: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    corner: Dp = DesignRadii.Field,
    inset: Dp = 4.dp,
    block: @Composable BoxScope.() -> Unit = { DefaultSelectorBlock(corner) },
    item: @Composable BoxScope.(index: Int, active: Boolean) -> Unit,
) {
    val haptics = LocalDesignHaptics.current
    val density = LocalDensity.current
    // Gestenerkennung läuft dauerhaft; aktuelle Auswahl und Rückruf daher immer frisch lesen (sonst veralteter Startpunkt).
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    BoxWithConstraints(modifier) {
        val itemWidth = maxWidth / count
        var dragX by remember { mutableStateOf<Float?>(null) }
        val itemPx = with(density) { itemWidth.toPx() }
        // Während des Ziehens folgt der Block direkt; sonst Feder ohne sichtbares Nachschwingen.
        val target = dragX?.let { with(density) { it.toDp() } } ?: (itemWidth * selected)
        val animated by animateDpAsState(target, spring(dampingRatio = .82f, stiffness = Spring.StiffnessMediumLow), label = "selector-block")
        val offset = if (dragX != null) target else animated
        // Block der Breite eines Eintrags; Ränder (inset) lassen die Rinne sichtbar.
        Box(Modifier.offset(x = offset).width(itemWidth).fillMaxHeight().padding(inset), content = block)
        Row(
            Modifier.fillMaxSize().pointerInput(count, itemPx) {
                detectHorizontalDragGestures(
                    onDragStart = { dragX = currentSelected * itemPx },
                    onDragEnd = {
                        val index = ((dragX ?: 0f) / itemPx).roundToInt().coerceIn(0, count - 1)
                        dragX = null
                        if (index != currentSelected) { haptics.perform(HapticEvent.SELECTION); currentOnSelect(index) }
                    },
                    onDragCancel = { dragX = null },
                ) { change, amount ->
                    change.consume()
                    dragX = ((dragX ?: 0f) + amount).coerceIn(0f, (count - 1) * itemPx)
                }
            },
        ) {
            repeat(count) { index ->
                val active = index == selected
                Box(
                    Modifier.width(itemWidth).fillMaxHeight()
                        .semantics { this.selected = active }
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                            if (!active) haptics.perform(HapticEvent.SELECTION)
                            onSelect(index)
                        },
                    contentAlignment = Alignment.Center,
                ) { item(index, active) }
            }
        }
    }
}

@Composable
private fun BoxScope.DefaultSelectorBlock(corner: Dp) {
    Box(Modifier.matchParentSize().designSelectionBlock(LocalDesignColors.current, corner))
}

/** Leichter Wechsel der Blockdeckkraft, z. B. wenn ein Eintrag gewählt bzw. abgewählt wird. */
@Composable
fun animatedSelectionProgress(selected: Boolean, label: String): Float =
    animateFloatAsState(if (selected) 1f else 0f, DesignMotion.select(), label = label).value
