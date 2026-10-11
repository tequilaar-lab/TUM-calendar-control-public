/*
 * Aufgabe: Standardmäßig deaktivierte lesende Hintergrundprüfung und Status bedienen.
 * Ein-/Ausgabe: Prüfeinstellungen und Status -> ausdrücklich betätigte Einstellungs-/Prüfabsichten.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Quelleninhalt, automatische Synchronisierung, Kalenderausgabe, Google-Autorisierung oder Bereichskonfiguration.
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
import com.tequila.tumcalendarcontrol.sync.SourceCheckCadence
import com.tequila.tumcalendarcontrol.sync.SourceCheckResult
import com.tequila.tumcalendarcontrol.sync.SourceCheckStatus
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BackgroundCheckScreen(state: CalendarControlState) {
    val settings = state.sourceCheckSettings
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InformationCard(message(168), message(200))
        InformationCard(message(201), settings.cadence.label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SourceCheckCadence.entries.forEach { cadence ->
                Button(
                    onClick = { state.updateSourceCheck(settings.enabled, cadence) },
                    modifier = Modifier.weight(1f),
                ) { Text(tr(if (cadence == settings.cadence) message(202, (cadence.label)) else cadence.label)) }
            }
        }
        Button(
            onClick = { state.updateSourceCheck(!settings.enabled) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(tr(if (settings.enabled) message(203) else message(204))) }
        InformationCard(message(205), state.sourceCheckStatus.toUserSummary())
        InformationCard(message(206), message(207))
        state.feedback?.let { Text(tr(it), color = LocalTumPalette.current.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp)) }
    }
}

private fun SourceCheckStatus.toUserSummary(): String {
    val time = checkedAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) ?: message(208)
    val resultText = when (result) {
        SourceCheckResult.NEVER -> message(209)
        SourceCheckResult.INITIAL -> message(210)
        SourceCheckResult.UNCHANGED -> message(211)
        SourceCheckResult.CHANGED -> message(212)
        SourceCheckResult.FAILED -> message(213)
        SourceCheckResult.NEEDS_CONFIGURATION -> message(214)
    }
    return message(147, (time), (resultText))
}
