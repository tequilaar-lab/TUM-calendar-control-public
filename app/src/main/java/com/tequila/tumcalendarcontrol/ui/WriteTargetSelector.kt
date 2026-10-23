/*
 * Aufgabe: Unabhängige Mehrfachziele für aktuelle Ausgabe als Häkchenzeilen darstellen.
 * Ein-/Ausgabe: Aktuelle Bestätigungsziele und Bereitschaft -> Zielauswahlabsicht.
 * Nebenwirkungen: Keine externen Aktionen; Auswahl über bestehenden Zustandsaufruf.
 * Nicht zuständig: Direkte Netzwerke, eigene Quellenanalyse oder eigenmächtige Kalenderoperationen.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.sync.OutputTarget
import com.tequila.tumcalendarcontrol.ui.design.DesignCheck
import com.tequila.tumcalendarcontrol.ui.design.DesignSection
import com.tequila.tumcalendarcontrol.ui.design.DesignText

/** Ziele dieser Ausgabe (mehrfach wählbar); je Ziel Bereitschaft (verbunden/autorisiert). Ändert keine Standardeinstellung. */
@Composable
internal fun WriteTargetSelector(state: CalendarControlState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DesignSection(tr(message(674)), Modifier.padding(start = 4.dp))
        OutputTarget.entries.forEach { target ->
            val checked = target in state.currentWriteTargets
            DesignCheck(
                tr(target.outputLabel()), checked, Modifier.padding(start = 4.dp).testTag("write-target-${target.name}"),
                detail = if (checked) tr(if (target in state.readyWriteTargets) message(421) else message(422)) else "",
            ) { if (!state.managedSyncInProgress) state.toggleCurrentWriteTarget(target) }
        }
        DesignText(tr(message(676)), 12, Modifier.padding(horizontal = 4.dp), muted = true)
    }
}

/** Anzeigename eines Ausgabeziels. */
internal fun OutputTarget.outputLabel(): String = when (this) {
    OutputTarget.LOCAL_CALENDAR -> message(89)
    OutputTarget.GOOGLE_CALENDAR -> message(90)
}
