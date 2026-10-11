/*
 * Aufgabe: Ausgabedifferenzen je Ziel berechnen und bestätigte Aktionen/Rücknahmen nacheinander ausführen.
 * Ein-/Ausgabe: Kandidaten, aktivierte Ziele und Zuordnungen -> Vorschau und isolierte Zielergebnisse.
 * Nebenwirkungen: Nur über den bestätigten kontrollierten Gateway-Aufruf.
 * Nicht zuständig: Quellenlesen, Berechtigungsanfragen, OAuth, Zielbindungserstellung, Bestandsspeicherung oder UI-Darstellung.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.i18n.joinMessages
import com.tequila.tumcalendarcontrol.i18n.concatenateMessages

data class ManagedSyncPreview(
    val desiredEventCount: Int,
    val targetChanges: Map<OutputTarget, List<SyncChange>>,
)

data class TargetBinding(val calendar: OwnedCalendarRef, val gateway: OwnedCalendarGateway)

data class ManagedSyncResult(
    val mappings: List<StoredEventMapping>,
    val targetResults: List<TargetSyncResult>,
) {
    val createdCount: Int get() = targetResults.sumOf { result -> result.changes.count { it.type == SyncChangeType.CREATE } }
    val updatedCount: Int get() = targetResults.sumOf { result -> result.changes.count { it.type == SyncChangeType.UPDATE } }
    /** Nur vom Ziel bestätigte erfolgreiche Löschungen zählen; 404/410 zählen nicht als neu gelöscht. */
    val removedCount: Int get() = targetResults.sumOf { result ->
        result.cleanupSummary?.removedNowCount ?: result.changes.count { it.type == SyncChangeType.REMOVE }
    }
    val failureSummary: String? get() = targetResults.mapNotNull(TargetSyncResult::failureSummary).takeIf(List<String>::isNotEmpty)?.let { joinMessages(it) }
    val writeDetailSummary: String get() = joinMessages(targetResults.map { result ->
        val label = if (result.target == OutputTarget.LOCAL_CALENDAR) message(89) else message(90)
        concatenateMessages(message(91, (label), (result.changes.count { it.type == SyncChangeType.CREATE }), (result.changes.count { it.type == SyncChangeType.UPDATE }), (result.changes.count { it.type == SyncChangeType.KEEP }), (result.failedCount)),
            if (result.restoredMissingCount > 0) message(92, (result.restoredMissingCount)) else "")
    })
    val cleanupDetailSummary: String? get() = targetResults
        .mapNotNull { result -> result.cleanupSummary?.toUserSummary(result.target) }
        .takeIf(List<String>::isNotEmpty)
        ?.let { joinMessages(it) }
}

/** Nur Ergebnisse je Ziel anzeigen; keine Zuordnungen, Marker, Termin-IDs oder Inhalte. */
private fun ManagedCleanupSummary.toUserSummary(target: OutputTarget): String {
    val label = when (target) {
        OutputTarget.LOCAL_CALENDAR -> message(89)
        OutputTarget.GOOGLE_CALENDAR -> message(90)
    }
    val remaining = remainingManagedCount?.let { message(93, (it)) } ?: message(94)
    return message(95, (label), (requestedCount), (removedNowCount), (alreadyAbsentCount), (remaining))
}

class ManagedSyncEngine(
    private val executor: OwnedCalendarSyncExecutor = OwnedCalendarSyncExecutor(),
    private val planner: SyncDiffPlanner = SyncDiffPlanner(),
) {
    /** Vorschau greift nicht auf Zielkalender zu; berechnet Änderungen anhand gespeicherter App-Zuordnungen. */
    fun preview(desired: List<DesiredCalendarEvent>, mappings: List<StoredEventMapping>, targets: Set<OutputTarget>): ManagedSyncPreview =
        ManagedSyncPreview(
            desiredEventCount = desired.size,
            targetChanges = targets.associateWith { target ->
                planner.plan(desired, mappings.filter { it.target == target }.map { ManagedEventMapping(it.identity, it.targetEventId, it.fingerprint) })
            },
        )

    /** Bei Fehler eines Ziels erfolgreiche Zuordnungen behalten und andere unabhängige Ziele weiter bearbeiten. */
    fun execute(desired: List<DesiredCalendarEvent>, mappings: List<StoredEventMapping>, bindings: List<TargetBinding>, onCheckpoint: (List<StoredEventMapping>) -> Unit = {}): ManagedSyncResult {
        var currentMappings = mappings
        val results = bindings.map { binding ->
            val result = runCatching { executor.execute(binding.calendar, desired, currentMappings, binding.gateway, onCheckpoint) }
                .getOrElse { error ->
                    TargetSyncResult(
                        target = binding.calendar.target,
                        mappings = currentMappings,
                        changes = emptyList(),
                        failureSummary = error.googleSafeSummary(message(96, (binding.calendar.target.name))),
                        failureCode = error.targetFailureCode(),
                        failedCount = desired.size,
                    )
                }
            currentMappings = result.mappings
            result
        }
        return ManagedSyncResult(currentMappings, results)
    }

    /** Rücknahme je Ziel isoliert; nur zugeordnete App-Termine löschen. */
    fun rollback(
        mappings: List<StoredEventMapping>,
        bindings: List<TargetBinding>,
        scope: ManagedEventCleanupScope = ManagedEventCleanupScope.AllManagedEvents,
    ): ManagedSyncResult {
        var currentMappings = mappings
        val results = bindings.map { binding ->
            val result = runCatching { executor.rollback(binding.calendar, currentMappings, binding.gateway, scope) }
                .getOrElse { TargetSyncResult(binding.calendar.target, currentMappings, emptyList(), message(97, (binding.calendar.target.name))) }
            // Teilweise Bereinigung speichern: bestätigte Löschungen übernehmen; fehlgeschlagene/verbliebene Zuordnungen erhalten.
            currentMappings = result.mappings
            result
        }
        return ManagedSyncResult(currentMappings, results)
    }
}
