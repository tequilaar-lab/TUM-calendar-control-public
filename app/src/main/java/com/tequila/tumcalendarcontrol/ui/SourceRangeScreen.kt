/*
 * Aufgabe: Lesezeitraum bearbeiten, getrennt vom Zeitraum bereits gelesener Daten.
 * Ein-/Ausgabe: Private Konfiguration und gelesene Zusammenfassung -> validierter Leseentwurf oder Leseabsicht.
 * Nebenwirkungen: Nur ausdrücklich geöffneter Datumsdialog; Speichern/Lesen über den App-Einstieg.
 * Nicht zuständig: iCal-Netzwerk, Quellen-Parsing, Google-Autorisierung oder Kalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import android.app.DatePickerDialog
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.R
import com.tequila.tumcalendarcontrol.sync.SourceOutputDateWindow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val DateText = DateTimeFormatter.ISO_LOCAL_DATE
private val BerlinZone = ZoneId.of("Europe/Berlin")

@Composable
fun SourceRangeScreen(state: CalendarControlState) {
    val configuration = state.privateSyncConfiguration
    val usesDarkTheme = state.themeMode.usesDarkTheme(isSystemInDarkTheme())
    var startDate by androidx.compose.runtime.saveable.rememberSaveable(configuration.sourceRangeStartDate) { mutableStateOf(configuration.sourceRangeStartDate) }
    var endDate by androidx.compose.runtime.saveable.rememberSaveable(configuration.sourceRangeEndDate) { mutableStateOf(configuration.sourceRangeEndDate) }
    var validation by remember { mutableStateOf<List<String>>(emptyList()) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InformationCard(message(162), message(631))
        val context = localizedContext()
        DateRangeField(message(465), startDate, message(474), onClick = { showDatePicker(context, startDate, usesDarkTheme) { startDate = it } })
        DateRangeField(message(466), endDate, message(474), onClick = { showDatePicker(context, endDate, usesDarkTheme) { endDate = it } })
        QuickRangeButtons(
            onFromToday = { startDate = LocalDate.now(BerlinZone).format(DateText); endDate = "" },
            onNextNinetyDays = {
                val today = LocalDate.now(BerlinZone)
                startDate = today.format(DateText)
                endDate = today.plusDays(89).format(DateText)
            },
            onClear = { startDate = ""; endDate = "" },
        )
        Button(
            onClick = {
                validation = state.saveSourceOutputRange(startDate, endDate)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(tr(message(632))) }
        validation.forEach { Text(tr(it), color = LocalTumPalette.current.onSurfaceVariant) }
        ReadCoverageCard(state)
        if (state.sourcePreview != null) {
            Button(
                onClick = { validation = state.continueWithSavedCourses() },
                enabled = !state.sourceReadInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(tr(message(633))) }
            Text(tr(message(634, (state.sourceSnapshotLabel))), color = LocalTumPalette.current.onSurfaceVariant)
        }
        if (state.sourceReloadConfirmationPending) {
            SourceReloadConfirmationCard(state)
        } else {
            Button(
                onClick = { validation = state.requestSourceReadForRange(startDate, endDate) },
                enabled = !state.sourceReadInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(tr(if (state.sourceReadInProgress) message(635) else message(506))) }
        }
        InformationCard(message(636), message(637))
    }
}

/** Neulesebestätigung speichert nur neue Datenversion; eine frühere bleibt wählbar. Fortfahren ist keine Synchronisierung. */
@Composable
private fun SourceReloadConfirmationCard(state: CalendarControlState) {
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr(message(638)), style = MiuixTheme.textStyles.title3)
            Text(tr(message(639)), color = LocalTumPalette.current.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { state.resolveSourceReloadConfirmation(false) }, modifier = Modifier.weight(1f)) { Text(tr(message(197))) }
                Button(onClick = { state.resolveSourceReloadConfirmation(true) }, modifier = Modifier.weight(1f)) { Text(tr(message(640))) }
            }
        }
    }
}

@Composable
private fun DateRangeField(label: String, value: String, emptyLabel: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        insideMargin = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr(label), style = MiuixTheme.textStyles.body1)
                Text(tr(if (value.isBlank()) emptyLabel else value), color = LocalTumPalette.current.onSurfaceVariant)
            }
            Text(tr(message(475)))
        }
    }
}

@Composable
private fun QuickRangeButtons(onFromToday: () -> Unit, onNextNinetyDays: () -> Unit, onClear: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr(message(641)), style = MiuixTheme.textStyles.title3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onFromToday, modifier = Modifier.weight(1f)) { Text(tr(message(642))) }
            Button(onClick = onNextNinetyDays, modifier = Modifier.weight(1f)) { Text(tr(message(643))) }
        }
        Button(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text(tr(message(644))) }
    }
}

@Composable
private fun ReadCoverageCard(state: CalendarControlState) {
    val preview = state.sourcePreview
    val coverage = if (preview == null) message(645) else {
        message(646, (preview.firstManageableStart.toLocalDateText()), (preview.lastManageableEnd.toLocalDateText()), (preview.manageableOccurrenceCount))
    }
    InformationCard(message(647), coverage)
}

private fun Instant?.toLocalDateText(): String = this?.atZone(BerlinZone)?.toLocalDate()?.format(DateText) ?: message(648)

/** Plattform-Datumsdialog verwendet aufgelösten App-Hell/Dunkelmodus. */
private fun showDatePicker(context: android.content.Context, it: String, usesDarkTheme: Boolean, onPicked: (String) -> Unit) {
    val initial = runCatching { LocalDate.parse(it) }.getOrElse { LocalDate.now(BerlinZone) }
    DatePickerDialog(
        context,
        if (usesDarkTheme) R.style.Theme_TumCalendarControl_DatePicker_Dark else R.style.Theme_TumCalendarControl_DatePicker_Light,
        { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day).format(DateText)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}
