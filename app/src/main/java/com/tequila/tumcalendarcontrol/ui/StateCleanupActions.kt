/*
 * Aufgabe: Aktionen des CalendarControlState zur Bereinigung geschriebener Termine: Seite vorbereiten, Ziele lesen lassen,
 *   Bereich bestimmen und Rücknahme anfragen (aus CalendarControlState ausgelagert, Verhalten unverändert).
 * Ein-/Ausgabe: Nutzerauswahl (Kurse, Zeitraum, Ziele) und gelesene Zielkataloge -> Bereinigungsbereich und Operationsanfrage.
 * Nebenwirkungen: Keine externen Aktionen; Lesen und Löschen in Kalendern führt CalendarControlApp nach Bestätigung aus.
 * Nicht zuständig: Kalenderzugriff, Ausgabe neuer Termine oder Darstellung.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.sync.ManagedEventCleanupScope
import com.tequila.tumcalendarcontrol.sync.ManagedEventIdentity
import com.tequila.tumcalendarcontrol.sync.OutputTarget
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import java.time.LocalDate
import java.time.ZoneId

fun CalendarControlState.requestCleanupCatalogRead(targets: Set<OutputTarget>) {
    if (managedSyncInProgress || cleanupCatalogReadTargets.isNotEmpty()) return
    val requested = targets.intersect(availableManagedOutputTargets())
    if (requested.isEmpty()) { feedback = message(249); return }
    if (OutputTarget.GOOGLE_CALENDAR in requested && !hasGoogleAuthorization) { feedback = message(250); return }
    if (OutputTarget.LOCAL_CALENDAR in requested && !localCalendarPermissionGranted) { feedback = message(251); return }
    cleanupCatalogReadTargets = requested
    cleanupCatalogReadToken += 1
    feedback = message(252)
}

fun CalendarControlState.resolveCleanupCatalogRead(token: Long, result: com.tequila.tumcalendarcontrol.sync.CleanupCatalogReadResult?) {
    if (token != cleanupCatalogReadToken) return
    cleanupCatalogReadTargets = emptySet()
    if (result == null) { feedback = message(253); return }
    ruleLedger = ruleLedger.copy(mappings = result.mappings)
    feedback = message(254, (result.checkedCount), (result.failedCount))
}

/** Bereinigungsseite bereitet Bereich und zweite Bestätigung vor; Öffnen greift nicht auf Kalender zu. */
fun CalendarControlState.openOutputCleanup() {
    if (availableManagedOutputTargets().isEmpty()) {
        feedback = message(296)
        return
    }
    managedSyncPreview = null
    navigation.openOutputCleanup()
}

/** Erst nach zweiter Bestätigung; Löschobjekte über UID/Originalinstanz, niemals Anzeigetitel bestimmen. */
fun CalendarControlState.requestManagedRollback(scope: ManagedEventCleanupScope = ManagedEventCleanupScope.AllManagedEvents, selectedTargets: Set<OutputTarget> = availableManagedOutputTargets()) {
    if (managedSyncInProgress) return
    if (cleanupCatalogReadTargets.isNotEmpty()) { feedback = message(307); return }
    if (scope is ManagedEventCleanupScope.SelectedIdentities && scope.identities.isEmpty()) {
        feedback = message(308)
        return
    }
    val targets = selectedTargets.intersect(availableManagedOutputTargets())
    if (targets.isEmpty()) { feedback = message(309); return }
    if (OutputTarget.LOCAL_CALENDAR in targets && !localCalendarPermissionGranted) { feedback = message(310); return }
    if (OutputTarget.GOOGLE_CALENDAR in targets && !hasGoogleAuthorization) { feedback = message(311); return }
    managedRollbackScope = scope
    managedRollbackTargets = targets
    if (outputWorkflow.beginRollback() != null) feedback = message(312)
}

/** Leere Kursmenge bedeutet keine Auswahl; null nur im Datumsmodus als unbegrenzte Kurswahl. */
fun CalendarControlState.cleanupIdentities(
    sourceUids: Set<String>?,
    startsOn: LocalDate?,
    endsOn: LocalDate?,
    targets: Set<OutputTarget> = availableManagedOutputTargets(),
): Set<ManagedEventIdentity> = cleanupOccurrencesForTargets(targets).asSequence()
    .filter { sourceUids == null || it.sourceUid in sourceUids }
    .filter { occurrence ->
        val localDate = occurrence.startsAt.atZone(ZoneId.of("Europe/Berlin")).toLocalDate()
        (startsOn == null || !localDate.isBefore(startsOn)) && (endsOn == null || !localDate.isAfter(endsOn))
    }
    .map { ManagedEventIdentity(it.sourceUid, it.instanceKey) }
    .toSet()

fun CalendarControlState.cleanupOccurrencesForTargets(targets: Set<OutputTarget>): List<SourceCalendarOccurrence> {
    val written = ruleLedger.mappings.filter { it.target in targets }.mapNotNull { it.writtenEvent }.map {
        SourceCalendarOccurrence(it.identity.sourceUid, it.identity.instanceKey, it.title, it.startsAt, it.endsAt, false, location = it.location)
    }
    val known = written.map { it.identity }.toSet()
    val fallback = currentSourceOccurrences + savedSourceSnapshots.previous?.occurrences.orEmpty() + ruleLedger.manualEvents.map {
        SourceCalendarOccurrence("manual:${(it.id)}", it.id, it.title, it.startsAt, it.endsAt, false)
    }
    return (written + fallback.filter { it.identity !in known }).distinct()
}

/** Ziele können verschiedene Zeitversionen enthalten; Datumsfilter je Ziel, keine zielübergreifende Löschwahl. */
fun CalendarControlState.selectedCleanupScope(sourceUids: Set<String>?, startsOn: LocalDate?, endsOn: LocalDate?, targets: Set<OutputTarget>): ManagedEventCleanupScope.SelectedIdentities {
    val byTarget = targets.associateWith { cleanupIdentities(sourceUids, startsOn, endsOn, setOf(it)) }
    return ManagedEventCleanupScope.SelectedIdentities(byTarget.values.flatten().toSet(), byTarget)
}
