/*
 * Aufgabe: Zweispaltige Darstellung auf breiten Fenstern (aufgeklapptes Faltgerät, Tablet): links Suche bzw. Favoriten,
 *   rechts die Details des gewählten Kurses (die Übersicht hat ihre eigene breite Darstellung); schmale Fenster unverändert.
 * Ein-/Ausgabe: aktuelle Seite, Fensterbreite, Herkunft der Detailseite -> Listenseite für die linke Spalte (oder null);
 *   Zustand -> zweispaltiger Inhalt.
 * Nebenwirkungen: Keine; nur Darstellung über bestehende Seiten.
 * Nicht zuständig: Navigation (NavigationState), Seiteninhalte oder Kopf/Navigation des Rahmens.
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.ui.design.DesignText

/** Ab dieser Inhaltsbreite zwei Spalten – gleiche Grenze wie die breite Übersicht (CourseIndexPage). */
internal const val TWO_PANE_MIN_WIDTH_DP = 700

/**
 * Suche und Favoriten; die Übersicht hat bereits ihre eigene breite Darstellung (Liste und Gruppendetails, Gerätefund:
 * doppelte Zweispaltigkeit vermeiden) und bleibt daher außen vor.
 */
private val ListScreens = setOf(AppScreen.SEARCH, AppScreen.FAVORITES)

/**
 * Listenseite der linken Spalte: Liste selbst, oder bei Kursdetails die Liste, aus der sie geöffnet wurden; null = eine
 * Spalte (zu schmal oder andere Seite).
 */
internal fun twoPaneListScreen(screen: AppScreen, widthDp: Int, detailOrigin: AppScreen): AppScreen? {
    if (widthDp < TWO_PANE_MIN_WIDTH_DP) return null
    return when (screen) {
        in ListScreens -> screen
        AppScreen.DETAIL -> detailOrigin.takeIf { it in ListScreens }
        else -> null
    }
}

/** Liste links (gut 40 %), Details rechts; ohne gültige Kurswahl ein Hinweis statt Details. */
@Composable
internal fun TwoPaneContent(state: CalendarControlState, listScreen: AppScreen, savedPages: SaveableStateHolder) {
    Row(Modifier.testTag("two-pane")) {
        Box(Modifier.weight(.42f).fillMaxHeight()) {
            savedPages.SaveableStateProvider(listScreen.name) { AppScreenContent(state, listScreen) }
        }
        Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(.58f).fillMaxHeight().testTag("two-pane-detail")) {
            if (state.courses.any { it.id == state.selectedCourseId }) {
                savedPages.SaveableStateProvider("${AppScreen.DETAIL.name}-pane") { DetailScreen(state) }
            } else {
                DesignText(tr(message(950)), 14, Modifier.align(Alignment.Center).padding(16.dp), muted = true)
            }
        }
    }
}
