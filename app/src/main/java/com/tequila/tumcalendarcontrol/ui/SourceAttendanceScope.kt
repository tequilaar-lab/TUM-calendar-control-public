/*
 * Aufgabe: Gewählten Anker und Bereich in konkrete gespeicherte Termine auflösen.
 * Ein-/Ausgabe: Quelltermine, Auswahlidentitäten und Bereich -> konkrete unveränderte Instanzen.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Direkte Netzwerke, eigene Quellenanalyse oder eigenmächtige Kalenderoperationen.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceOccurrenceIdentity
import java.time.ZoneId

internal object SourceAttendanceScope {
    /** Originaltitel behält Typ/Gruppe; nur Mitgliedsauswahl, niemals Terminidentität. */
    fun resolve(
        occurrences: List<SourceCalendarOccurrence>,
        selection: Set<SourceOccurrenceIdentity>,
        scope: AttendanceEditScope,
        dateText: String,
    ): List<SourceCalendarOccurrence>? {
        val selected = occurrences.filter { it.identity in selection && !it.isCancelled }
        if (scope == AttendanceEditScope.ONLY_THIS_OCCURRENCE) return selected
        val date = if (scope == AttendanceEditScope.SPECIFIED_AND_FUTURE) {
            AttendanceEffectiveDateParser.parseOrNull(dateText) ?: return null
        } else null
        val anchors = selected.groupBy { it.title.trim() }.mapValues { (_, items) -> items.minOf { it.startsAt } }
        val explicitAnchors = selected.groupBy { it.sourceUid }.mapValues { (_, items) -> items.minOf { it.startsAt } }
        return occurrences.filter { item ->
            val anchor = anchors[item.title.trim()]
            !item.isCancelled && anchor != null && when (scope) {
                AttendanceEditScope.ALL_OCCURRENCES -> true
                AttendanceEditScope.CURRENT_AND_FUTURE -> item.startsAt >= (explicitAnchors[item.sourceUid] ?: anchor)
                AttendanceEditScope.SPECIFIED_AND_FUTURE -> !item.startsAt.atZone(ZoneId.of("Europe/Berlin")).toLocalDate().isBefore(date)
                AttendanceEditScope.ONLY_THIS_OCCURRENCE -> false
            }
        }
    }
}
