/*
 * Aufgabe: Profileinstieg im Kopf (Druckrückmeldung) und Avatar aus lokaler Farbe und Anfangszeichen.
 * Ein-/Ausgabe: Lokales Profil und Rückruf -> runder Profilknopf bzw. Avatar.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Navigationsentscheidungen, Themenpersistenz, Material-UI-Komponenten oder echte Kalenderaktionen.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.CompositionLocalProvider

/** Lokaler Profileinstieg mit wiederherstellbarem Anfangszeichen/Farbe; kein angemeldetes Konto. */
@Composable
fun TactileProfileButton(profile: UserProfile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalTumPalette.current
    TactileSurface(
        modifier = modifier.size(48.dp),
        onClick = onClick,
        shape = CircleShape,
        restingColor = Color.Transparent,
        pressedColor = palette.primary.copy(alpha = 0.18f),
    ) {
        ProfileAvatar(profile = profile, modifier = Modifier.size(34.dp))
    }
}

/** Avatar ohne Bild; lokale Farbe und erstes sichtbares Zeichen des Namens. */
@Composable
fun ProfileAvatar(profile: UserProfile, modifier: Modifier = Modifier) {
    val palette = LocalTumPalette.current
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 34.dp, minHeight = 34.dp)
            .clip(CircleShape)
            .background(profile.avatarTone.color(palette)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tr(profile.displayName.trim().ifBlank { UserProfile().displayName }).take(1),
            color = Color.White,
            style = MiuixTheme.textStyles.body1,
        )
    }
}

/** Druckzustand in einem Container bündeln; Klick, Form und Animation nicht auf jeder Seite duplizieren. */
@Composable
private fun TactileSurface(
    modifier: Modifier,
    onClick: () -> Unit,
    shape: Shape,
    restingColor: Color,
    pressedColor: Color,
    content: @Composable () -> Unit,
) {
    val haptics = com.tequila.tumcalendarcontrol.ui.design.LocalDesignHaptics.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = message(667))
    val backgroundColor by animateColorAsState(
        if (pressed) pressedColor else restingColor,
        label = message(668),
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { haptics.perform(com.tequila.tumcalendarcontrol.ui.design.HapticEvent.ACTION); onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun AvatarTone.color(palette: TumPalette): Color = when (this) {
    AvatarTone.TUM_BLUE -> palette.primary
    AvatarTone.SEA_GREEN -> Color(0xFF178B75)
    AvatarTone.SUNSET -> Color(0xFFC9682B)
    AvatarTone.LAVENDER -> Color(0xFF7861AD)
}
