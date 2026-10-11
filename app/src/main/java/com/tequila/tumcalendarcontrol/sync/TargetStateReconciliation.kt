/*
 * Aufgabe: Zuordnungen, Sollzustand und gelesene eigene Zieltermine zur echten Zustandsdiagnose vergleichen.
 * Ein-/Ausgabe: Zuordnung, Sollzustand und echte eigene Zielwerte -> bereinigte Zustandsdiagnose.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Kalenderlesen, Regeländerungen oder Schreiben/Löschen von Terminen.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.i18n.message

data class ActualManagedEvent(val eventId: String, val fingerprint: String)

enum class TargetStateCode(val explanation: String) {
    IN_SYNC(message(135)),
    MISSING_IN_TARGET(message(136)),
    CHANGED_IN_TARGET(message(137)),
}

data class TargetStateFinding(
    val identity: ManagedEventIdentity,
    val code: TargetStateCode,
    val eventId: String,
)

/** Nur gleiche Zuordnungs-ID vergleichen; verschiedene Identitäten beeinflussen sich auch bei Zeitkonflikt nicht. */
class TargetStateReconciler(private val planner: SyncDiffPlanner = SyncDiffPlanner()) {
    fun reconcile(
        desired: List<DesiredCalendarEvent>,
        mappings: List<StoredEventMapping>,
        actualByEventId: Map<String, ActualManagedEvent>,
    ): List<TargetStateFinding> {
        val desiredByIdentity = desired.associateBy(DesiredCalendarEvent::identity)
        return mappings.mapNotNull { mapping ->
            val event = desiredByIdentity[mapping.identity] ?: return@mapNotNull null
            val actual = actualByEventId[mapping.targetEventId]
            val code = when {
                actual == null -> TargetStateCode.MISSING_IN_TARGET
                actual.fingerprint == planner.run { event.fingerprint() } -> TargetStateCode.IN_SYNC
                else -> TargetStateCode.CHANGED_IN_TARGET
            }
            TargetStateFinding(mapping.identity, code, mapping.targetEventId)
        }
    }
}
