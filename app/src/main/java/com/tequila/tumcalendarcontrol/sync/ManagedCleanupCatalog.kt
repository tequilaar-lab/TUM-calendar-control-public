/*
 * Aufgabe: Eigene protokollierte Termine lesend um Titel/Zeiten für Kurs-/Datumsbereinigung ergänzen.
 * Ein-/Ausgabe: Eigene Zielreferenzen und Zuordnungen -> verfügbare Titel-/Zeitangaben zur Bereinigung.
 * Nebenwirkungen: Nur lesender Zugriff auf verifizierte eigene Zieltermine.
 * Nicht zuständig: Fremde Kalender, Übernahme anhand von Titeln, Bereinigungsausführung oder automatischer Start.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.i18n.message

data class CleanupCatalogReadResult(val mappings: List<StoredEventMapping>, val checkedCount: Int, val failedCount: Int)

object ManagedCleanupCatalog {
    fun read(mappings: List<StoredEventMapping>, bindings: List<TargetBinding>): CleanupCatalogReadResult {
        var checked = 0
        var failed = 0
        val next = mappings.map { mapping ->
            val binding = bindings.firstOrNull { it.calendar.target == mapping.target } ?: return@map mapping
            runCatching {
                check(binding.gateway.verifyOwnership(binding.calendar))
                val ids = binding.gateway.findEventIdsByIdentity(binding.calendar, mapping.identity)
                if (ids.isEmpty()) { checked += 1; return@runCatching mapping }
                check(ids.size == 1)
                val actual = binding.gateway.readEvent(binding.calendar, ids.single(), mapping.identity)
                    ?: error(message(88))
                checked += 1
                mapping.copy(calendarId = binding.calendar.calendarId, targetEventId = ids.single(), writtenEvent = actual)
            }.getOrElse { failed += 1; mapping }
        }
        return CleanupCatalogReadResult(next, checked, failed)
    }
}
