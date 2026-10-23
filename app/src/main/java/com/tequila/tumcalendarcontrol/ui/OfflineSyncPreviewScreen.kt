/*
 * Aufgabe: Feste synthetische Schreibdifferenz (Offline-Beispiel) im neuen Erscheinungsbild zeigen.
 * Ein-/Ausgabe: Synthetische Vorschau des Zustands -> Hinweis, Zusammenfassung und Beispielzeilen.
 * Nebenwirkungen: Keine; kein Lesen, keine Berechtigung, kein Schreiben.
 * Nicht zuständig: echte Vorschau (ManagedSyncPreviewScreen), Synchronisierung oder Kalenderzugriff.
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
import com.tequila.tumcalendarcontrol.ui.design.DesignFrostCard
import com.tequila.tumcalendarcontrol.ui.design.DesignNote
import com.tequila.tumcalendarcontrol.ui.design.DesignPage
import com.tequila.tumcalendarcontrol.ui.design.DesignLine
import com.tequila.tumcalendarcontrol.ui.design.DesignSection
import com.tequila.tumcalendarcontrol.ui.design.DesignText

/** Nur feste synthetische Differenz; kein Schreibbestätigungsknopf und keine echte Synchronisierung. */
@Composable
internal fun SyncPreviewScreen(state: CalendarControlState) {
    val preview = state.offlinePreview
    DesignPage(contentTag = "offline-content") {
        DesignNote(tr(message(543)), tr(message(544)))
        DesignFrostCard(Modifier.testTag("offline-summary")) {
            DesignSection(tr(message(545)))
            DesignText(tr(message(546, preview.createCount, preview.updateCount, preview.removeCount, preview.keepCount, preview.skipCount)), 14)
        }
        // Beispielzeilen sind reine Information (nicht antippbar, daher ohne Pfeil).
        Column {
            preview.changes.forEach { change -> OfflineLine(tr(change.type.label), tr(message(547))) }
            preview.skipped.forEach { OfflineLine(tr(message(548)), tr(message(549))) }
        }
    }
}

@Composable
private fun OfflineLine(title: String, detail: String) {
    Column(Modifier.padding(start = 4.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DesignText(title, 15)
        DesignText(detail, 12, muted = true)
    }
    DesignLine()
}
