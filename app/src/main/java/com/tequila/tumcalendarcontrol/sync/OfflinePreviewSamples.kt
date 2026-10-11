/*
 * Aufgabe: Synthetische Ausgabevorschau ohne echte Quelle, Konten oder Kalender bereitstellen.
 * Ein-/Ausgabe: Keine externe Eingabe -> feste synthetische Vorschau.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Simulation echter Synchronisierungsergebnisse oder Ausführung als Schreibplan.
 */
package com.tequila.tumcalendarcontrol.sync

object OfflinePreviewSamples {
    fun preview(): SyncPreview = SyncPreviewComposer.compose(
        changes = listOf(
            SyncChange(SyncChangeType.CREATE, ManagedEventIdentity("synthetic-course-a", "2026-10-06T08:00:00Z")),
            SyncChange(SyncChangeType.UPDATE, ManagedEventIdentity("synthetic-course-b", "2026-10-07T12:00:00Z")),
            SyncChange(SyncChangeType.REMOVE, ManagedEventIdentity("synthetic-course-c", "2026-10-08T06:00:00Z")),
            SyncChange(SyncChangeType.KEEP, ManagedEventIdentity("synthetic-course-d", "2026-10-09T10:00:00Z")),
        ),
        skipped = listOf(IcalSkippedEvent("synthetic-pending", IcalSkipReason.NOT_CONFIRMED_FIXPLATZ)),
    )
}
