/*
 * Aufgabe: Regeln und eigene Termine über ausdrücklich gewählte SAF-Datei sichern/wiederherstellen; Wiederherstellen mit Glasbestätigung.
 * Ein-/Ausgabe: Regelbestand und Sicherungsentwurf -> ausdrücklich bestätigte Datei-/Wiederherstellungsabsicht.
 * Nebenwirkungen: Keine externen Aktionen; Dateiauswahl und Übernahme über bestehende Zustandsaufrufe.
 * Nicht zuständig: Private Verbindungen, Ersetzen von Terminzuordnungen, automatische Synchronisierung oder Kalenderänderungen.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.ui.design.DesignConfirmDialog
import com.tequila.tumcalendarcontrol.ui.design.DesignConfirmRequest
import com.tequila.tumcalendarcontrol.ui.design.DesignFrostCard
import com.tequila.tumcalendarcontrol.ui.design.DesignMetrics
import com.tequila.tumcalendarcontrol.ui.design.DesignNote
import com.tequila.tumcalendarcontrol.ui.design.DesignPage
import com.tequila.tumcalendarcontrol.ui.design.DesignPillButton
import com.tequila.tumcalendarcontrol.ui.design.DesignSection
import com.tequila.tumcalendarcontrol.ui.design.DesignText

/** Inhalt der Sicherung, Export und Import; eine gelesene Datei wird erst nach Glasbestätigung übernommen. */
@Composable
fun RuleBackupScreen(state: CalendarControlState) {
    val pending = state.pendingRuleBackup
    DesignPage(contentTag = "backup-content") {
        DesignNote(tr(message(487)), tr(message(488)))
        DesignFrostCard(Modifier.testTag("backup-current")) {
            DesignSection(tr(message(489)))
            DesignMetrics(
                tr(message(514)) to state.ruleLedger.rules.size.toString(),
                tr(message(165)) to state.ruleLedger.manualEvents.size.toString(),
            )
            DesignText(tr(message(490, state.ruleLedger.rules.size, state.ruleLedger.manualEvents.size)), 12, muted = true)
        }
        DesignPillButton(tr(message(491)), Modifier.testTag("backup-export"), primary = false, onClick = state::requestRuleBackupExport)
        DesignPillButton(tr(message(492)), Modifier.testTag("backup-import"), primary = false, onClick = state::requestRuleBackupImport)
        state.feedback?.let { DesignText(tr(it), 12, Modifier.padding(horizontal = 4.dp), muted = true) }
    }
    DesignConfirmDialog(pending?.let {
        DesignConfirmRequest(
            kicker = tr(message(719)), title = tr(message(493)),
            body = tr(message(494, it.summary.ruleCount, it.summary.groupRuleCount, it.summary.manualEventCount)), note = "",
            cancelLabel = tr(message(197)), confirmLabel = tr(message(495)), danger = false,
            onCancel = state::cancelRuleBackupImport, onConfirm = state::confirmRuleBackupImport,
        )
    })
}
