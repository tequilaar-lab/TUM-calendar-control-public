/*
 * Aufgabe: Startsymbol auf der Darstellungsseite wählen: fünf Vorschauen, Wechsel erst nach Glasbestätigung.
 * Ein-/Ausgabe: Aktives Symbol aus dem PackageManager -> Auswahl und bestätigter Wechsel über AppIconSwitcher.
 * Nebenwirkungen: Nur Aktivierung der app-eigenen Startaliasse nach ausdrücklicher Bestätigung.
 * Nicht zuständig: Alias-Logik (AppIconChoice), übrige Darstellungsoptionen.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import com.tequila.tumcalendarcontrol.ui.design.hapticSelectable
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.ui.design.DesignConfirmDialog
import com.tequila.tumcalendarcontrol.ui.design.DesignConfirmRequest
import com.tequila.tumcalendarcontrol.ui.design.DesignSection
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors

/** Fünf Symbole nebeneinander; das aktive mit Tintenrahmen. Antippen fragt nach, erst „Wechseln“ ändert den Startbildschirm. */
@Composable
internal fun AppIconSelector() {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppIconSwitcher.current(context)) }
    var pending by remember { mutableStateOf<AppIconStyle?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DesignSection(tr(message(730)), Modifier.padding(start = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppIconStyle.entries.forEach { style ->
                AppIconTile(style, style == current, Modifier.testTag("app-icon-${style.name}")) { if (style != current) pending = style }
            }
        }
        DesignText(tr(message(731)), 12, Modifier.padding(horizontal = 4.dp), muted = true)
    }
    DesignConfirmDialog(pending?.let { style ->
        DesignConfirmRequest(
            kicker = tr(message(719)), title = tr(message(732)), body = tr(message(739, style.label)), note = tr(message(731)),
            cancelLabel = tr(message(197)), confirmLabel = tr(message(733)), danger = false,
            onCancel = { pending = null },
            onConfirm = {
                AppIconSwitcher.select(context, style)
                current = style
                pending = null
            },
        )
    })
}

@Composable
private fun AppIconTile(style: AppIconStyle, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalDesignColors.current
    Column(
        modifier.hapticSelectable(selected, Role.RadioButton, onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(60.dp).border(if (selected) 2.dp else 0.dp, if (selected) colors.ink else colors.paper, RoundedCornerShape(18.dp)).padding(4.dp),
            contentAlignment = Alignment.Center,
        ) { AppIconPreview(style, 52.dp) }
        DesignText(tr(style.label), 11, color = if (selected) colors.ink else colors.muted, weight = if (selected) FontWeight.Medium else FontWeight.Normal, align = TextAlign.Center)
    }
}

/** Adaptives Symbol nachgebildet: Ebenen 1,5-fach, sichtbar die Mitte unter einer abgerundeten Maske (wie Startbildschirme). */
@Composable
internal fun AppIconPreview(style: AppIconStyle, size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(30)), contentAlignment = Alignment.Center) {
        Image(painterResource(style.background), null, Modifier.requiredSize(size * 1.5f))
        Image(painterResource(style.foreground), null, Modifier.requiredSize(size * 1.5f))
        // Feine Kontur, damit helle Symbole (Papier, Umlauf) sich vom hellen Seitengrund abheben.
        Box(Modifier.matchParentSize().border(1.dp, LocalDesignColors.current.ink.copy(alpha = .10f), RoundedCornerShape(30)))
    }
}
