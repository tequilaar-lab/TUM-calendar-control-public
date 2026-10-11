/*
 * Aufgabe: Kalenderauswirkungen vor Ausgabe anzeigen und Bestätigung einholen.
 * Ein-/Ausgabe: Interne Vorschauzusammenfassung -> ausdrückliche Ausgabebestätigung.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: iCal-Lesen, OAuth-Details, Calendar-Provider-Aufrufe oder echte Termininhalte.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.i18n.restoreOwnedMessage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.sync.OutputTarget
import com.tequila.tumcalendarcontrol.sync.SyncChangeType
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun ManagedSyncPreviewScreen(state: CalendarControlState) {
    val preview = state.managedSyncPreview
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InformationCard(message(166), message(410))
        if (preview == null) {
            InformationCard(message(411), message(412))
            state.feedback?.let { Text(tr(it), color = LocalTumPalette.current.onSurfaceVariant) }
            return
        }
        InformationCard(message(413), message(414, (preview.desiredEventCount), (state.managedSourceCandidateCount), (state.managedManualCandidateCount)))
        if (state.managedCancelledMappedCount > 0) {
            InformationCard(
                message(415),
                message(416, (state.managedCancelledMappedCount)),
            )
            Button(onClick = state::openOutputCleanup, modifier = Modifier.fillMaxWidth()) { Text(tr(message(417))) }
        }
        WriteTargetSelector(state)
        if (state.sourceCatalogLoaded) InformationCard(message(418), message(419, (state.sourceSnapshotLabel), (state.sourceSnapshotSavedAt)))
        preview.targetChanges.toSortedMap(compareBy(OutputTarget::name)).forEach { (target, changes) ->
            InformationCard(
                target.label(),
                message(420, (changes.count { it.type == SyncChangeType.CREATE }), (changes.count { it.type != SyncChangeType.CREATE }), (if (target in state.readyWriteTargets) message(421) else message(422))),
            )
        }
        if (OutputTarget.GOOGLE_CALENDAR in state.currentWriteTargets && !state.hasGoogleAuthorization) {
            Button(onClick = state::requestGoogleAuthorization, modifier = Modifier.fillMaxWidth()) { Text(tr(message(423))) }
        }
        if (OutputTarget.LOCAL_CALENDAR in state.currentWriteTargets && !state.localCalendarPermissionGranted) {
            Button(onClick = state::requestCurrentWriteLocalPermission, modifier = Modifier.fillMaxWidth()) { Text(tr(message(424))) }
        }
        if (state.readyWriteTargets.isNotEmpty() && state.readyWriteTargets != preview.targetChanges.keys) {
            Button(onClick = state::requestReadyManagedSyncExecution, enabled = !state.managedSyncInProgress, modifier = Modifier.fillMaxWidth()) {
                Text(tr(message(425, (com.tequila.tumcalendarcontrol.i18n.joinMessages(state.readyWriteTargets.map { it.label() }, "、")))))
            }
        }
        Button(
            onClick = state::requestManagedSyncExecution,
            enabled = !state.managedSyncInProgress,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(tr(if (state.managedSyncInProgress) message(304) else message(426))) }
        state.ruleLedger.lastSummary?.let { summary ->
            InformationCard(
                message(427),
                com.tequila.tumcalendarcontrol.i18n.concatenateMessages((summary.detailSummary?.let(::restoreOwnedMessage) ?: message(428, (summary.createdCount), (summary.updatedCount), (summary.removedCount))),
                    (summary.failureSummary?.let { message(429, (restoreOwnedMessage(it))) } ?: "")),
            )
        }
        state.feedback?.let { Text(tr(it), color = LocalTumPalette.current.onSurfaceVariant) }
    }
}

private fun OutputTarget.label(): String = when (this) {
    OutputTarget.LOCAL_CALENDAR -> message(89)
    OutputTarget.GOOGLE_CALENDAR -> message(90)
}
