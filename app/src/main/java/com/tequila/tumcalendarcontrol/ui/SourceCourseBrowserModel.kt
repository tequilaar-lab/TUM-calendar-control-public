/*
 * Aufgabe: Laufzeitkurse in durchsuchbare/gefilterte visuelle Gruppen umwandeln.
 * Ein-/Ausgabe: UID-Kurse, Instanzen, Auswahl und Zeitfenster -> durchsuchbare visuelle Gruppen.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Zusammenlegen von Kursidentitäten, Regelbearbeitung, iCal-Lesen oder Zielkalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceOccurrenceIdentity
import com.tequila.tumcalendarcontrol.sync.SourceOutputDateWindow
import java.time.Instant
import java.time.ZoneId

/** Filter nur für Kataloganzeige; UID-Auswahl und Kandidaten unverändert. */
enum class SourceCourseFilter(val label: String) {
    ALL(message(593)),
    SELECTED(message(188)),
    IN_OUTPUT_RANGE(message(594)),
}

/** Visueller Container gleichnamiger Kurse; Auswahl/Synchronisierung weiter pro UID und Instanz. */
data class SourceCourseDisplayGroup(
    val displayTitle: String,
    val occurrences: List<SourceCalendarOccurrence>,
    val selectedOccurrenceCount: Int,
    val inOutputRangeOccurrenceCount: Int,
    val earliestStart: Instant,
) {
    val sourceUidCount: Int get() = occurrences.map(SourceCalendarOccurrence::sourceUid).distinct().size
    val totalOccurrenceCount: Int get() = occurrences.size
}

object SourceCourseBrowserModel {
    /** Titel nur zur visuellen Gruppierung, nie für Regeln, Kandidaten oder Persistenzidentität. Leerzeichen normalisieren, damit Gruppen nicht unnötig getrennt werden. */
    fun groups(
        occurrences: List<SourceCalendarOccurrence>,
        selectedOccurrences: Set<SourceOccurrenceIdentity>,
        query: String,
        filter: SourceCourseFilter,
        outputWindow: SourceOutputDateWindow,
        zoneId: ZoneId,
    ): List<SourceCourseDisplayGroup> {
        val searchable = query.trim()
        fun isInWindow(occurrence: SourceCalendarOccurrence): Boolean = outputWindow.includes(occurrence.startsAt, occurrence.endsAt, zoneId)
        fun matches(occurrence: SourceCalendarOccurrence): Boolean {
            val queryMatches = searchable.isBlank() || occurrence.title.contains(searchable, ignoreCase = true) ||
                occurrence.location.contains(searchable, ignoreCase = true)
            val filterMatches = when (filter) {
                SourceCourseFilter.ALL -> true
                SourceCourseFilter.SELECTED -> occurrence.identity in selectedOccurrences
                SourceCourseFilter.IN_OUTPUT_RANGE -> isInWindow(occurrence)
            }
            return queryMatches && filterMatches
        }
        return occurrences.asSequence()
            .filterNot(SourceCalendarOccurrence::isCancelled)
            .filter(::matches)
            .groupBy { normalizedDisplayKey(it.title) }
            .map { (_, members) ->
                val ordered = members.sortedWith(compareBy(SourceCalendarOccurrence::startsAt).thenBy(SourceCalendarOccurrence::sourceUid).thenBy(SourceCalendarOccurrence::instanceKey))
                SourceCourseDisplayGroup(
                    displayTitle = ordered.first().title,
                    occurrences = ordered,
                    selectedOccurrenceCount = ordered.count { it.identity in selectedOccurrences },
                    inOutputRangeOccurrenceCount = ordered.count(::isInWindow),
                    earliestStart = ordered.minOf(SourceCalendarOccurrence::startsAt),
                )
            }
            .sortedWith(compareBy(SourceCourseDisplayGroup::earliestStart).thenBy(SourceCourseDisplayGroup::displayTitle))
            .toList()
    }

    /** Gruppenschlüssel der Anzeige (auch für den „aktuellen Kurs“ der Schnellauswahl). */
    internal fun normalizedDisplayKey(title: String): String = title.trim().replace(Regex("\\s+"), " ").lowercase()
}
