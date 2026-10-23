/*
 * Aufgabe: Entwürfe und Formulare für getrennte Kurseinstellungen darstellen (Milchglaskarte je Kurs).
 * Ein-/Ausgabe: Ausgewählte Termine und Entwürfe -> getrennte Teilnahme-/Favoriteneinstellungen je Gruppe.
 * Nebenwirkungen: Keine externen Aktionen; nur Entwürfe im Zustand, Speichern über die Hauptaktion der Seite.
 * Nicht zuständig: Kalenderausgabe, Persistenz, Quellenlesen oder Kursnamen als Ersatz für Synchronisierungsidentität.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.sync.SourceOccurrenceIdentity
import com.tequila.tumcalendarcontrol.ui.design.DesignChoice
import com.tequila.tumcalendarcontrol.ui.design.DesignFrostCard
import com.tequila.tumcalendarcontrol.ui.design.DesignRow
import com.tequila.tumcalendarcontrol.ui.design.DesignSection
import com.tequila.tumcalendarcontrol.ui.design.DesignText

data class IndividualAttendanceDraft(val attendance: AttendanceState? = null, val favorite: SourceFavoriteAction = SourceFavoriteAction.KEEP)
data class IndividualAttendanceGroup(val key: String, val title: String, val selection: Set<SourceOccurrenceIdentity>)

/** Je Kurs eine Karte mit aktuellem Stand und Entwurf; „Einzeln einstellen“ klappt Teilnahme- und Favoritenauswahl auf. */
@Composable
internal fun IndividualSourceAttendance(state: CalendarControlState) {
    DesignText(tr(message(398)), 12, Modifier.padding(horizontal = 4.dp), muted = true)
    state.individualAttendanceGroups.forEach { group ->
        val draft = state.individualAttendanceDrafts[group.key] ?: IndividualAttendanceDraft()
        var expanded by remember(group.key) { mutableStateOf(false) }
        fun update(next: IndividualAttendanceDraft) { state.individualAttendanceDrafts = state.individualAttendanceDrafts + (group.key to next) }
        DesignFrostCard(Modifier.testTag("individual-${group.key}")) {
            DesignText(tr(group.title), 17, serif = true)
            DesignText(tr(state.currentSelectionSummary(group.selection)), 12, muted = true)
            DesignText(tr(message(399, group.selection.size, draft.attendance?.label ?: message(400), draft.favorite.label)), 12, muted = true)
            DesignRow(tr(if (expanded) message(401) else message(402))) { expanded = !expanded }
            if (expanded) {
                // Leere Wahl = Teilnahme dieses Kurses unverändert lassen.
                (listOf<AttendanceState?>(null) + AttendanceState.entries).forEach { option ->
                    DesignChoice(tr(option?.label ?: message(400)), option == draft.attendance) { update(draft.copy(attendance = option)) }
                }
                DesignSection(tr(message(143)))
                SourceFavoriteAction.entries.forEach { action ->
                    DesignChoice(tr(action.label), action == draft.favorite) { update(draft.copy(favorite = action)) }
                }
            }
        }
    }
}
