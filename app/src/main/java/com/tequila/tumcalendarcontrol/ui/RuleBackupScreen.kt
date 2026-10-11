/*
 * Aufgabe: Regeln und eigene Termine über ausdrücklich gewählte SAF-Datei sichern/wiederherstellen und bestätigen.
 * Ein-/Ausgabe: Regelbestand und Sicherungsentwurf -> ausdrücklich bestätigte Datei-/Wiederherstellungsabsicht.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Private Verbindungen, Ersetzen von Terminzuordnungen, automatische Synchronisierung oder Kalenderänderungen.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun RuleBackupScreen(state: CalendarControlState) {
    val pending = state.pendingRuleBackup
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InformationCard(message(487), message(488))
        InformationCard(message(489), message(490, (state.ruleLedger.rules.size), (state.ruleLedger.manualEvents.size)))
        Button(onClick = state::requestRuleBackupExport, modifier = Modifier.fillMaxWidth()) { Text(tr(message(491))) }
        Button(onClick = state::requestRuleBackupImport, modifier = Modifier.fillMaxWidth()) { Text(tr(message(492))) }
        if (pending != null) {
            InformationCard(message(493), message(494, (pending.summary.ruleCount), (pending.summary.groupRuleCount), (pending.summary.manualEventCount)))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = state::cancelRuleBackupImport, modifier = Modifier.weight(1f)) { Text(tr(message(197))) }
                    Button(onClick = state::confirmRuleBackupImport, modifier = Modifier.weight(1f)) { Text(tr(message(495))) }
            }
        }
        state.feedback?.let { Text(tr(it), color = LocalTumPalette.current.onSurfaceVariant) }
    }
}
