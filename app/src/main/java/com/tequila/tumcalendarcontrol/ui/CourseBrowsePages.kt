/*
 * Aufgabe: Übersicht (mit Karte „Demnächst“ für Prüfungen/Fristen), Suche und Favoriten als Varianten des gemeinsamen
 *   Kursverzeichnisses mit eigenem Kopfinhalt.
 * Ein-/Ausgabe: CalendarControlState (angezeigte, gesuchte, favorisierte Kurse; Suchbegriff) -> CourseIndexPage.
 * Nebenwirkungen: Keine externen Aktionen; Suchbegriff bleibt Seitenzustand im bestehenden Zustand.
 * Nicht zuständig: Filterlogik (CalendarControlState), Verzeichnisdarstellung (CourseIndexPage) oder Kursidentität.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import com.tequila.tumcalendarcontrol.ui.design.hapticClickable
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.ui.design.DesignField
import com.tequila.tumcalendarcontrol.ui.design.DesignNote
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors

/** Übersicht: Zähler und Einstieg in die Kursauswahl; Verzeichnis aller angezeigten Kurse mit Einstieg zum erneuten Lesen. */
@Composable
internal fun OverviewHome(state: CalendarControlState) {
    CourseIndexPage(state, state.displayedCourses, message(360), showRefresh = true) { groups ->
        val colors = LocalDesignColors.current
        // Prüfungen/Fristen der nächsten 30 Tage (nur wenn vorhanden); Antippen öffnet die Campustermine.
        UpcomingDeadlinesCard(state, Modifier.padding(bottom = 12.dp))
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            DesignText(tr(message(693, groups.size, groups.sumOf { it.courses.size })), 12, muted = true, modifier = Modifier.weight(1f))
            DesignText(
                tr(message(694)) + "  ↗", 14, color = colors.accent, weight = FontWeight.Medium,
                // Tippfläche 48 dp hoch; Zeilenabstand darunter entsprechend verringert, Lage bleibt gleich.
                modifier = Modifier.hapticClickable { if (state.sourceCatalogLoaded) state.openSourceCourseSelector() else state.openSourceRange() }
                    .heightIn(min = 48.dp).wrapContentHeight().padding(horizontal = 4.dp).testTag("overview-select"),
            )
        }
    }
}

/** Suche: Suchfeld mit Lupe; ein Suchbegriff filtert nur die Anzeige und ändert keine Auswahl oder Kursdaten. */
@Composable
internal fun SearchPage(state: CalendarControlState) {
    CourseIndexPage(state, state.searchedCourses, if (state.searchQuery.isBlank()) message(355) else message(356), showRefresh = false) {
        DesignField(
            label = "", value = state.searchQuery, onValueChange = { state.searchQuery = it },
            modifier = Modifier.padding(bottom = 14.dp).testTag("search-field"), placeholder = tr(message(155)), search = true,
        )
    }
}

/** Favoriten: Hinweis, dass Favoriten nur die Anzeige betreffen; Verzeichnis der favorisierten Kurse. */
@Composable
internal fun FavoritesPage(state: CalendarControlState) {
    CourseIndexPage(state, state.favoriteCourses, message(357), showRefresh = false) {
        DesignNote(tr(message(358)), tr(message(359)), Modifier.padding(bottom = 14.dp))
    }
}
