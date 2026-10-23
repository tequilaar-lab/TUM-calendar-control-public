/*
 * Aufgabe: Teilnahme, Favoriten und Bereich für gewählte Einzeltermine bearbeiten (neues Erscheinungsbild).
 * Ein-/Ausgabe: Ausgewählte Termine und Entwürfe -> ausdrücklich gespeicherte Teilnahme-/Favoritenwerte.
 * Nebenwirkungen: Keine externen Aktionen; Speichern erst über die schwebende Hauptaktion.
 * Nicht zuständig: iCal-Lesen, Anzeige interner Identitäten, Berechtigungen oder Kalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.ui.design.DesignActionPage
import com.tequila.tumcalendarcontrol.ui.design.DesignChoice
import com.tequila.tumcalendarcontrol.ui.design.DesignField
import com.tequila.tumcalendarcontrol.ui.design.DesignLine
import com.tequila.tumcalendarcontrol.ui.design.DesignMetrics
import com.tequila.tumcalendarcontrol.ui.design.DesignPillButton
import com.tequila.tumcalendarcontrol.ui.design.DesignSection
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors

/** Freier Text auf der Titellinie (4 dp innerhalb der Blockkanten). */
private val TextInset = Modifier.padding(horizontal = 4.dp)

/**
 * Bearbeitungsart (gemeinsam oder je Kurs), Umfang der Auswahl, Teilnahme mit Statusfarbe, Favoriten, Geltungsbereich.
 * Schwebende Hauptaktion speichert; „Abbrechen“ kehrt ohne Speichern zurück.
 */
@Composable
fun SourceAttendanceScreen(state: CalendarControlState) {
    val colors = LocalDesignColors.current
    val selection = state.selectedSourceOccurrences
    DesignActionPage(tr(message(592)), onAction = state::confirmSourceAttendance, actionTag = "source-attendance-save", contentTag = "source-attendance-content") {
        DesignText(tr(message(583, selection.size)), 20, TextInset)
        DesignText(tr(message(584)), 12, TextInset, muted = true)
        DesignSection(tr(message(713)), TextInset)
        DesignChoice(tr(message(586)), !state.individualAttendanceMode, Modifier.testTag("source-mode-unified"), detail = tr(message(715))) { state.individualAttendanceMode = false }
        DesignChoice(tr(message(588)), state.individualAttendanceMode, Modifier.testTag("source-mode-individual"), detail = tr(message(716))) { state.individualAttendanceMode = true }
        DesignLine()
        DesignSection(tr(message(714)), TextInset)
        val chosen = state.currentSourceOccurrences.filter { it.identity in selection }
        DesignMetrics(
            tr(message(146)) to chosen.map(SourceBulkSelection::keyOf).distinct().size.toString(),
            tr(message(712)) to selection.size.toString(),
            modifier = TextInset,
        )
        DesignText(tr(state.currentSelectionSummary(selection)), 12, TextInset, muted = true)
        if (state.individualAttendanceMode) {
            IndividualSourceAttendance(state)
        } else {
            // Sichtbare Regeln: Grün verbindlich, Blau auswählen, Grau ab Termin nicht teilnehmen; Punkt in der Statusfarbe.
            DesignSection(tr(message(589)), TextInset)
            AttendanceState.entries.forEach { option ->
                DesignChoice(
                    label = tr(option.label), selected = option == state.pendingSourceAttendance,
                    modifier = Modifier.testTag("source-attendance-${option.name}"),
                    detail = tr(message(199, option.colorLabel, option.description)),
                    trailing = { Box(Modifier.size(10.dp).clip(CircleShape).background(attendanceInk(option, colors))) },
                ) { state.pendingSourceAttendance = option }
            }
            DesignSection(tr(message(143)), TextInset)
            SourceFavoriteAction.entries.forEach { action ->
                DesignChoice(tr(action.label), action == state.pendingSourceFavoriteAction, Modifier.testTag("source-favorite-${action.name}"), detail = tr(action.description)) {
                    state.pendingSourceFavoriteAction = action
                }
            }
        }
        // Bereich gilt für beide Bearbeitungsarten; Resolver verhindert rückwirkende graue Regeln.
        DesignSection(tr(message(195)), TextInset)
        AttendanceEditScope.entries.forEach { scope ->
            DesignChoice(tr(scope.label), scope == state.pendingSourceAttendanceScope, Modifier.testTag("source-scope-${scope.name}"), detail = tr(scope.description)) {
                state.pendingSourceAttendanceScope = scope
            }
        }
        if (state.pendingSourceAttendanceScope == AttendanceEditScope.SPECIFIED_AND_FUTURE) {
            DesignField(tr(message(196)), state.pendingSourceEffectiveDate, { state.pendingSourceEffectiveDate = it }, Modifier.testTag("source-attendance-date"))
        }
        state.feedback?.let { DesignText(tr(it), 12, TextInset, muted = true) }
        DesignText(tr(message(590, state.sourceAttendanceAffectedCount?.toString() ?: message(591))), 12, TextInset.testTag("source-attendance-count"), muted = true)
        DesignPillButton(tr(message(197)), Modifier.testTag("source-attendance-cancel"), primary = false, onClick = state::handleBack)
    }
}
