/*
 * Aufgabe: Kalenderauswirkungen vor Ausgabe anzeigen, Bestätigung einholen und letztes Ergebnis zeigen.
 * Ein-/Ausgabe: Interne Vorschauzusammenfassung -> ausdrückliche Ausgabebestätigung (schwebende Hauptaktion).
 * Nebenwirkungen: Keine externen Aktionen; Ausgabe, Autorisierung und Berechtigung nur über bestehende Zustandsaufrufe.
 * Nicht zuständig: iCal-Lesen, OAuth-Details, Calendar-Provider-Aufrufe oder echte Termininhalte.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.i18n.restoreOwnedMessage

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.sync.OutputTarget
import com.tequila.tumcalendarcontrol.sync.SyncChangeType
import com.tequila.tumcalendarcontrol.ui.design.DesignActionPage
import com.tequila.tumcalendarcontrol.ui.design.DesignFrostCard
import com.tequila.tumcalendarcontrol.ui.design.DesignLine
import com.tequila.tumcalendarcontrol.ui.design.DesignMetrics
import com.tequila.tumcalendarcontrol.ui.design.DesignNote
import com.tequila.tumcalendarcontrol.ui.design.DesignPage
import com.tequila.tumcalendarcontrol.ui.design.DesignPillButton
import com.tequila.tumcalendarcontrol.ui.design.DesignSection
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors

/** Freier Text auf der Titellinie (4 dp innerhalb der Blockkanten). */
private val TextInset = Modifier.padding(horizontal = 4.dp)

/**
 * Ohne Vorschau: Hinweis ohne Hauptaktion. Mit Vorschau: Umfang, Ziele, Datenstand, je Kalender erwartete Änderungen,
 * nötige Verbindungen und letztes Ergebnis; schwebende Hauptaktion „Bestätigen und schreiben“.
 */
@Composable
fun ManagedSyncPreviewScreen(state: CalendarControlState) {
    val preview = state.managedSyncPreview
    if (preview == null) {
        DesignPage(contentTag = "write-content") {
            DesignNote(tr(message(411)), tr(message(412)))
            LastResultCard(state)
            state.feedback?.let { DesignText(tr(it), 12, TextInset, muted = true) }
        }
        return
    }
    val writing = state.writeProgress
    val running = state.managedSyncInProgress && writing.active
    val reported = writing.progress
    val label = when {
        !state.managedSyncInProgress -> message(426)
        writing.stopping -> message(783)
        reported != null -> message(781, reported.done, reported.total, reported.target.outputLabel())
        else -> message(304)
    }
    // Während des Schreibens: Pille füllt sich je Ziel, links „Anhalten“ (wirkt zwischen zwei Terminen).
    DesignActionPage(
        tr(label), onAction = state::requestManagedSyncExecution,
        actionEnabled = !state.managedSyncInProgress, actionTag = "write-confirm", contentTag = "write-content",
        actionProgress = if (running) writing.fraction ?: 0f else null,
        secondaryLabel = if (running) tr(message(782)) else null, onSecondary = writing::requestStop, secondaryTag = "write-stop",
    ) {
        if (running) DesignText(tr(message(784)), 12, TextInset.testTag("write-progress-note"), color = LocalDesignColors.current.accent)
        // Umfang: große Zahl, darunter Aufteilung nach Kursen und Campus-Terminen.
        DesignSection(tr(message(413)), TextInset)
        DesignText(preview.desiredEventCount.toString(), 56, TextInset.testTag("write-count"), weight = FontWeight.Light)
        DesignText(tr(message(414, preview.desiredEventCount, state.managedSourceCandidateCount, state.managedManualCandidateCount)), 14, TextInset)
        DesignText(tr(message(410)), 12, TextInset, muted = true)
        DesignLine()
        WriteTargetSelector(state)
        if (state.sourceCatalogLoaded) {
            DesignSection(tr(message(418)), TextInset)
            DesignText(tr(message(419, state.sourceSnapshotLabel, state.sourceSnapshotSavedAt)), 13, TextInset, muted = true)
        }
        // Je Zielkalender getrennt: neu, prüfen/aktualisieren, Bereitschaft.
        DesignSection(tr(message(720)), TextInset)
        preview.targetChanges.toSortedMap(compareBy(OutputTarget::name)).forEach { (target, changes) ->
            DesignFrostCard(Modifier.testTag("write-target-card-${target.name}")) {
                DesignText(tr(target.outputLabel()), 17, serif = true)
                DesignMetrics(
                    tr(message(721)) to changes.count { it.type == SyncChangeType.CREATE }.toString(),
                    tr(message(722)) to changes.count { it.type != SyncChangeType.CREATE }.toString(),
                )
                DesignText(tr(message(420, changes.count { it.type == SyncChangeType.CREATE }, changes.count { it.type != SyncChangeType.CREATE },
                    if (target in state.readyWriteTargets) message(421) else message(422))), 12, muted = true)
            }
        }
        // Verbindungen nachholen; „nur bereite Kalender“ schreibt die übrigen später.
        if (OutputTarget.GOOGLE_CALENDAR in state.currentWriteTargets && !state.hasGoogleAuthorization) {
            DesignPillButton(tr(message(423)), Modifier.testTag("write-connect-google"), primary = false, onClick = state::requestGoogleAuthorization)
        }
        if (OutputTarget.LOCAL_CALENDAR in state.currentWriteTargets && !state.localCalendarPermissionGranted) {
            DesignPillButton(tr(message(424)), Modifier.testTag("write-allow-local"), primary = false, onClick = state::requestCurrentWriteLocalPermission)
        }
        if (state.readyWriteTargets.isNotEmpty() && state.readyWriteTargets != preview.targetChanges.keys) {
            DesignPillButton(
                tr(message(425, com.tequila.tumcalendarcontrol.i18n.joinMessages(state.readyWriteTargets.map { it.outputLabel() }, "、"))),
                Modifier.testTag("write-ready-only"), primary = false, enabled = !state.managedSyncInProgress, onClick = state::requestReadyManagedSyncExecution,
            )
        }
        // Vom Schreiben unabhängig: abgesagte Quelltermine werden nie automatisch gelöscht.
        if (state.managedCancelledMappedCount > 0) {
            DesignNote(tr(message(415)), tr(message(416, state.managedCancelledMappedCount)), error = true)
            DesignPillButton(tr(message(417)), Modifier.testTag("write-open-cleanup"), primary = false, onClick = state::openOutputCleanup)
        }
        LastResultCard(state)
        state.feedback?.let { DesignText(tr(it), 12, TextInset.testTag("write-feedback"), muted = true) }
    }
}

/** Letztes Schreib-/Bereinigungsergebnis je Ziel; Fehlerteil in gedämpftem Rot. */
@Composable
private fun LastResultCard(state: CalendarControlState) {
    val summary = state.ruleLedger.lastSummary ?: return
    DesignFrostCard(Modifier.testTag("write-last-result")) {
        DesignSection(tr(message(427)))
        DesignText(tr(summary.detailSummary?.let(::restoreOwnedMessage) ?: message(428, summary.createdCount, summary.updatedCount, summary.removedCount)), 13)
        summary.failureSummary?.let { DesignText(tr(restoreOwnedMessage(it)), 12, color = LocalDesignColors.current.danger) }
    }
}
