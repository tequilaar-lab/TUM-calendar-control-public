/*
 * Aufgabe: Differenzen und übersprungene Importe als rein lesende Vorschau zusammenfassen.
 * Ein-/Ausgabe: Differenz und ausgeschlossene Importe -> lesbare Vorschau ohne Ausführung.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Bestätigung, Autorisierung, Netzwerk, Calendar Provider oder Löschen von Zielterminen.
 */
package com.tequila.tumcalendarcontrol.sync

data class SyncPreview(
    val changes: List<SyncPreviewChange>,
    val skipped: List<SyncPreviewSkip>,
) {
    val createCount get() = changes.count { it.type == SyncChangeType.CREATE }
    val updateCount get() = changes.count { it.type == SyncChangeType.UPDATE }
    val removeCount get() = changes.count { it.type == SyncChangeType.REMOVE }
    val keepCount get() = changes.count { it.type == SyncChangeType.KEEP }
    val skipCount get() = skipped.size
}

data class SyncPreviewChange(val type: SyncChangeType, val identity: ManagedEventIdentity)
data class SyncPreviewSkip(val sourceUid: String?, val reason: String)

object SyncPreviewComposer {
    fun compose(changes: List<SyncChange>, skipped: List<IcalSkippedEvent>): SyncPreview = SyncPreview(
        changes = changes.map { SyncPreviewChange(it.type, it.identity) },
        skipped = skipped.map { SyncPreviewSkip(it.sourceUid, it.reason.label) },
    )
}
