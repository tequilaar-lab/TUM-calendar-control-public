/*
 * Aufgabe: Entwürfe und Formulare für getrennte Kurseinstellungen darstellen.
 * Ein-/Ausgabe: Ausgewählte Termine und Entwürfe -> getrennte Teilnahme-/Favoriteneinstellungen je Gruppe.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Kalenderausgabe, Persistenz, Quellenlesen oder Kursnamen als Ersatz für Synchronisierungsidentität.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.sync.SourceOccurrenceIdentity
import top.yukonga.miuix.kmp.basic.*

data class IndividualAttendanceDraft(val attendance: AttendanceState? = null, val favorite: SourceFavoriteAction = SourceFavoriteAction.KEEP)
data class IndividualAttendanceGroup(val key: String, val title: String, val selection: Set<SourceOccurrenceIdentity>)

@Composable
internal fun IndividualSourceAttendance(state: CalendarControlState) {
    Text(tr(message(398)))
    state.individualAttendanceGroups.forEach { group ->
        val draft = state.individualAttendanceDrafts[group.key] ?: IndividualAttendanceDraft()
        var expanded by remember(group.key) { mutableStateOf(false) }
        Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr(group.title))
                Text(tr(state.currentSelectionSummary(group.selection)))
                Text(tr(message(399, (group.selection.size), (draft.attendance?.label ?: message(400)), (draft.favorite.label))))
                Button(onClick = { expanded = !expanded }) { Text(tr(if (expanded) message(401) else message(402))) }
                if (expanded) {
                    (listOf<AttendanceState?>(null) + AttendanceState.entries).forEach { option ->
                        Row {
                            RadioButton(selected = option == draft.attendance, onClick = { state.individualAttendanceDrafts = state.individualAttendanceDrafts + (group.key to draft.copy(attendance = option)) })
                            Text(tr(option?.label ?: message(400)), Modifier.padding(start = 8.dp))
                        }
                    }
                    Text(tr(message(143)))
                    SourceFavoriteAction.entries.forEach { action ->
                        Row {
                            RadioButton(selected = action == draft.favorite, onClick = { state.individualAttendanceDrafts = state.individualAttendanceDrafts + (group.key to draft.copy(favorite = action)) })
                            Text(tr(action.label), Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}
