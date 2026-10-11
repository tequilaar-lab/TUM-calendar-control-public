/*
 * Aufgabe: Instanzen gleicher Quell-UID zum Laufzeitkatalog aggregieren.
 * Ein-/Ausgabe: Quellinstanzen -> nur nach UID aggregierter Laufzeitkatalog.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Fixplatz-Entscheidungen, Regelspeicherung, Netzwerkabruf, Titeldeduplizierung oder Kalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.sync

import java.time.Instant

data class SourceCourse(
    val sourceUid: String,
    val title: String,
    val location: String,
    val firstStart: Instant,
    val lastEnd: Instant,
    val representativeInstanceKey: String,
    val occurrenceCount: Int,
    val cancelledOccurrenceCount: Int,
)

object SourceCourseCatalog {
    /** UID ist einziger Zugehörigkeitsschlüssel; Titel nur zur Anzeige, nie zur Zusammenführung oder Regelidentität. */
    fun from(occurrences: List<SourceCalendarOccurrence>): List<SourceCourse> = occurrences
        .groupBy(SourceCalendarOccurrence::sourceUid)
        .map { (sourceUid, items) ->
            val ordered = items.sortedBy(SourceCalendarOccurrence::startsAt)
            val representative = ordered.firstOrNull { !it.isCancelled } ?: ordered.first()
            SourceCourse(
                sourceUid = sourceUid,
                title = representative.title,
                location = representative.location,
                firstStart = ordered.first().startsAt,
                lastEnd = ordered.maxOf(SourceCalendarOccurrence::endsAt),
                representativeInstanceKey = representative.instanceKey,
                occurrenceCount = ordered.size,
                cancelledOccurrenceCount = ordered.count(SourceCalendarOccurrence::isCancelled),
            )
        }
        .sortedWith(compareBy<SourceCourse> { it.firstStart }.thenBy { it.sourceUid })
}
