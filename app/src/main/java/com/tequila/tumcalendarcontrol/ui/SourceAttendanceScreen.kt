/*
 * Aufgabe: Teilnahme, Favoriten und Bereich für gewählte Einzeltermine bearbeiten.
 * Ein-/Ausgabe: Ausgewählte Termine und Entwürfe -> ausdrücklich gespeicherte Teilnahme-/Favoritenwerte.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: iCal-Lesen, Anzeige interner Identitäten, Berechtigungen oder Kalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SourceAttendanceScreen(state: CalendarControlState) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(tr(message(583, (state.selectedSourceOccurrences.size))), style = MiuixTheme.textStyles.title3)
        Text(tr(state.currentSelectionSummary(state.selectedSourceOccurrences)))
        Text(tr(message(584)), color = LocalTumPalette.current.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { state.individualAttendanceMode = false }, modifier = Modifier.weight(1f)) { Text(tr(if (!state.individualAttendanceMode) message(585) else message(586))) }
            Button(onClick = { state.individualAttendanceMode = true }, modifier = Modifier.weight(1f)) { Text(tr(if (state.individualAttendanceMode) message(587) else message(588))) }
        }
        if (state.individualAttendanceMode) IndividualSourceAttendance(state) else {
        Text(tr(message(589)), style = MiuixTheme.textStyles.title3)
        AttendanceState.entries.forEach { option -> SourceAttendanceOption(option, state) }
        Text(tr(message(143)), style = MiuixTheme.textStyles.title3)
        SourceFavoriteAction.entries.forEach { action -> SourceFavoriteActionOption(action, state) }
        }
        Text(tr(message(195)), style = MiuixTheme.textStyles.title3)
        AttendanceEditScope.entries.forEach { scope -> SourceAttendanceScopeOption(scope, state) }
        if (state.pendingSourceAttendanceScope == AttendanceEditScope.SPECIFIED_AND_FUTURE) {
            TextField(
                value = state.pendingSourceEffectiveDate,
                onValueChange = { state.pendingSourceEffectiveDate = it },
                modifier = Modifier.fillMaxWidth(),
                label = tr(message(196)),
            )
        }
        state.feedback?.let { Text(tr(it), color = LocalTumPalette.current.onSurfaceVariant) }
        Text(tr(message(590, (state.sourceAttendanceAffectedCount?.toString() ?: message(591)))), color = LocalTumPalette.current.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = tr(message(197)),
                onClick = state::handleBack,
                modifier = Modifier.width(112.dp),
            )
            Button(onClick = state::confirmSourceAttendance, modifier = Modifier.weight(1f)) { Text(tr(message(592))) }
        }
    }
}

/** Favoriten als optionale Sammelaktion; Standard behält bisherigen Zustand. */
@Composable
private fun SourceFavoriteActionOption(action: SourceFavoriteAction, state: CalendarControlState) {
    val selected = action == state.pendingSourceFavoriteAction
    Card(
        modifier = Modifier.fillMaxWidth().clickable { state.pendingSourceFavoriteAction = action },
        insideMargin = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = { state.pendingSourceFavoriteAction = action })
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(tr(action.label), style = MiuixTheme.textStyles.body1)
                Text(tr(action.description), color = LocalTumPalette.current.onSurfaceVariant)
            }
        }
    }
}

/** Dreizustandskarte ändert nur Entwurf; vor Bestätigung keine Regeln speichern. */
@Composable
private fun SourceAttendanceOption(option: AttendanceState, state: CalendarControlState) {
    val selected = option == state.pendingSourceAttendance
    Card(
        modifier = Modifier.fillMaxWidth().clickable { state.pendingSourceAttendance = option },
        insideMargin = androidx.compose.foundation.layout.PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = { state.pendingSourceAttendance = option })
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(tr(option.label), style = MiuixTheme.textStyles.title3)
                Text(tr(message(199, (option.colorLabel), (option.description))), color = LocalTumPalette.current.onSurfaceVariant)
            }
        }
    }
}

/** Bereichskarte ändert nur Entwurf; Resolver verhindert rückwirkende graue Regeln. */
@Composable
private fun SourceAttendanceScopeOption(scope: AttendanceEditScope, state: CalendarControlState) {
    val selected = scope == state.pendingSourceAttendanceScope
    Card(
        modifier = Modifier.fillMaxWidth().clickable { state.pendingSourceAttendanceScope = scope },
        insideMargin = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = { state.pendingSourceAttendanceScope = scope })
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(tr(scope.label), style = MiuixTheme.textStyles.body1)
                Text(tr(scope.description), color = LocalTumPalette.current.onSurfaceVariant)
            }
        }
    }
}
