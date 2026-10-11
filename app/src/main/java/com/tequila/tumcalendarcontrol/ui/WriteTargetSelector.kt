/*
 * Aufgabe: Unabhängige Mehrfachziele für aktuelle Ausgabe darstellen.
 * Ein-/Ausgabe: Aktuelle Bestätigungsziele -> Zielauswahlabsicht.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Direkte Netzwerke, eigene Quellenanalyse oder eigenmächtige Kalenderoperationen.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.sync.OutputTarget
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun WriteTargetSelector(state: CalendarControlState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr(message(674)))
        OutputTarget.entries.forEach { target ->
            val checked = target in state.currentWriteTargets
            Row(Modifier.fillMaxWidth().toggleable(
                value = checked, enabled = !state.managedSyncInProgress, role = Role.Checkbox,
                onValueChange = { state.toggleCurrentWriteTarget(target) },
            ).padding(12.dp)) {
                Text(tr(message(675, (if (checked) "☑" else "☐"), (if (target == OutputTarget.LOCAL_CALENDAR) message(89) else message(90)))))
            }
        }
        Text(tr(message(676)))
    }
}
