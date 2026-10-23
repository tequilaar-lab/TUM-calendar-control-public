/*
 * Aufgabe: Darstellungsseite: Hell/Dunkel, helle und dunkle Palette, Glasstufe mit Vorschau.
 * Ein-/Ausgabe: Darstellungszustand und Glasauswahl -> sofort gespeicherte Auswahl (wie bisher ohne Speichern-Schaltfläche).
 * Nebenwirkungen: Keine externen Aktionen; Speichern übernehmen Zustand bzw. App-Einstieg (Glasstufe).
 * Nicht zuständig: Teilnahmefarben, Systemeinstellungen oder Gerätefähigkeit (nur angezeigt).
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.ui.design.DesignPage
import com.tequila.tumcalendarcontrol.ui.design.DesignRadii
import com.tequila.tumcalendarcontrol.ui.design.DesignSection
import com.tequila.tumcalendarcontrol.ui.design.DesignSegmented
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.GlassLevel
import com.tequila.tumcalendarcontrol.ui.design.GlassSupport
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors
import com.tequila.tumcalendarcontrol.ui.design.LocalGlassBackdrop
import com.tequila.tumcalendarcontrol.ui.design.LocalGlassSelection
import com.tequila.tumcalendarcontrol.ui.design.LocalGlassSupport
import com.tequila.tumcalendarcontrol.ui.design.designGlass
import com.tequila.tumcalendarcontrol.ui.design.glassSpec

/** Freier Text auf der Titellinie (4 dp innerhalb der Blockkanten). */
private val TextInset = Modifier.padding(horizontal = 4.dp)

/** Reihenfolge wie im bestätigten Entwurf: System zuerst. */
private val ThemeOrder = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)

@Composable
internal fun AppearanceScreen(state: CalendarControlState) {
    val glass = LocalGlassSelection.current
    DesignPage(contentTag = "appearance-content") {
        DesignSection(tr(message(726)), TextInset)
        DesignSegmented(ThemeOrder.map { tr(it.label) }, ThemeOrder.indexOf(state.themeMode), Modifier.testTag("appearance-mode")) { state.themeMode = ThemeOrder[it] }
        DesignSection(tr(message(185)), TextInset)
        PaletteTiles(dark = false, state = state)
        DesignSection(tr(message(187)), TextInset)
        PaletteTiles(dark = true, state = state)
        DesignText(tr(message(186)), 12, TextInset, muted = true)
        // Glasstufe: wirkt auf Navigation, schwebende Schaltflächen und Bestätigungsdialoge.
        DesignSection(tr(message(686)), TextInset)
        DesignSegmented(GlassLevel.entries.map { tr(it.label) }, glass.level.ordinal, Modifier.testTag("appearance-glass")) { glass.select(GlassLevel.entries[it]) }
        DesignText(tr(message(691)), 12, TextInset, muted = true)
        if (LocalGlassSupport.current != GlassSupport.FULL) DesignText(tr(message(692)), 12, TextInset, color = LocalDesignColors.current.danger)
        DesignSection(tr(message(727)), TextInset, index = tr(glass.level.label))
        GlassPreview()
    }
}

/**
 * Synthetische Kurskarte mit Statusfarben; darüber eine Glasleiste, die nur diese Karte abtastet
 * (eigene Ebene, damit sie nicht die Seite und sich selbst abtastet).
 */
@Composable
private fun GlassPreview() {
    val colors = LocalDesignColors.current
    val layer = rememberLayerBackdrop()
    val spec = glassSpec(LocalGlassSelection.current.level, LocalGlassSupport.current)
    Box(Modifier.fillMaxWidth().height(176.dp).clip(RoundedCornerShape(DesignRadii.Block)).testTag("appearance-preview")) {
        // Statusfarben so weit unten, dass die Glasleiste ihre untere Hälfte überdeckt: Unschärfe sichtbar, Text noch lesbar.
        Column(
            Modifier.fillMaxSize().layerBackdrop(layer).background(colors.wash).padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DesignText("Synthetik Messtechnik", 18, serif = true)
                DesignText("10:00–11:30", 12, muted = true)
            }
            Row(Modifier.padding(bottom = 30.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AttendanceState.entries.forEach { option ->
                    DesignText(tr(option.label), 11, Modifier.clip(RoundedCornerShape(DesignRadii.Pill)).background(attendanceInk(option, colors)).padding(horizontal = 10.dp, vertical = 4.dp), color = colors.paper)
                }
            }
        }
        CompositionLocalProvider(LocalGlassBackdrop provides layer) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth().height(48.dp)
                    .designGlass(colors, DesignRadii.Pill, layer, spec, tintScale = 1.15f),
                contentAlignment = Alignment.Center,
            ) { DesignText(tr(message(729)), 12, color = colors.accent, weight = FontWeight.Medium) }
        }
    }
}
