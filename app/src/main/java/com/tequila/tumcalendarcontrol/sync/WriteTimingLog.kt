/*
 * Aufgabe: Inhaltsfreie Protokollzeile einer abgeschlossenen Ausgabe/Rücknahme bilden (Dauer und Zähler je Art),
 *   damit die Schreibgeschwindigkeit auf dem Gerät messbar ist.
 * Ein-/Ausgabe: ManagedSyncResult, Dauer, Art -> eine Zeile nur aus Zahlen und Zielarten.
 * Nebenwirkungen: Keine; das Schreiben ins Systemprotokoll übernimmt der Aufrufer.
 * Nicht zuständig: Titel, Zeiten, Orte, Kalender-IDs, Konten oder Fehlertexte (nie enthalten).
 */
package com.tequila.tumcalendarcontrol.sync

object WriteTimingLog {
    const val TAG = "TumWrite"

    fun line(result: ManagedSyncResult, elapsedMillis: Long, rollback: Boolean): String {
        val kept = result.targetResults.sumOf { target -> target.changes.count { it.type == SyncChangeType.KEEP } }
        return listOf(
            "stage=finished",
            "kind=${if (rollback) "rollback" else "write"}",
            "elapsedMs=$elapsedMillis",
            "created=${result.createdCount}",
            "updated=${result.updatedCount}",
            "kept=$kept",
            "removed=${result.removedCount}",
            "failed=${result.targetResults.sumOf { it.failedCount }}",
            "skipped=${result.targetResults.sumOf { it.skippedCount }}",
            "targets=${result.targetResults.map { it.target.name }.sorted().joinToString(",")}",
        ).joinToString(" ")
    }
}
