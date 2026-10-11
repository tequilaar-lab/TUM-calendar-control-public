/*
 * Aufgabe: Dreizustandsauswahl mit vier gemeinsamen Geltungsbereichen darstellen.
 * Ein-/Ausgabe: Teilnahmeentwurf und Bereich -> ausdrückliche Bestätigungsabsicht.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Echte Quelleninstanzauswahl; der synthetische Prototyp speichert nur die Auswahl und schreibt keine Kalender.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AttendanceScreen(state: CalendarControlState) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(tr(message(193, (state.selectedCourse.title))), style = MiuixTheme.textStyles.title3)
        Text(tr(message(194)))
        AttendanceOption(AttendanceState.MUST_ATTEND, state)
        AttendanceOption(AttendanceState.CHOOSE_TO_ATTEND, state)
        AttendanceOption(AttendanceState.STOP_ATTENDING, state)
        Text(tr(message(195)), style = MiuixTheme.textStyles.title3)
        AttendanceEditScope.entries.forEach { scope -> AttendanceScopeOption(scope, state) }
        if (state.pendingAttendanceScope == AttendanceEditScope.SPECIFIED_AND_FUTURE) TextField(
            value = state.pendingEffectiveDate,
            onValueChange = { state.pendingEffectiveDate = it },
            modifier = Modifier.fillMaxWidth(),
            label = tr(message(196)),
        )
        state.feedback?.let { message ->
            Text(tr(message), color = LocalTumPalette.current.onSurfaceVariant)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = tr(message(197)),
                onClick = { state.open(AppScreen.DETAIL) },
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = state::confirmAttendance,
                modifier = Modifier.weight(2f),
            ) { Text(tr(message(198))) }
        }
    }
}

@Composable
private fun AttendanceScopeOption(scope: AttendanceEditScope, state: CalendarControlState) {
    val selected = scope == state.pendingAttendanceScope
    Card(
        modifier = Modifier.fillMaxWidth().clickable { state.pendingAttendanceScope = scope },
        insideMargin = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = { state.pendingAttendanceScope = scope })
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(tr(scope.label), style = MiuixTheme.textStyles.body1)
                Text(tr(scope.description), color = LocalTumPalette.current.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AttendanceOption(option: AttendanceState, state: CalendarControlState) {
    val palette = LocalTumPalette.current
    val selected = option == state.pendingAttendance
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { state.pendingAttendance = option },
        insideMargin = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = { state.pendingAttendance = option })
            Spacer(Modifier.padding(4.dp))
            Column(modifier = Modifier.padding(start = 4.dp, end = 8.dp)) {
                Text(tr(option.label), style = MiuixTheme.textStyles.title3)
                Text(tr(message(199, (option.colorLabel), (option.description))), color = palette.onSurfaceVariant)
            }
            StatusDot(option)
        }
    }
}
