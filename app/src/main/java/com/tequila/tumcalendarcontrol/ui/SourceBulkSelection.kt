/*
 * Aufgabe: Schnellauswahl der Kursauswahl berechnen: alle Termine des Datenstands und „ganzer aktueller Kurs“.
 * Ein-/Ausgabe: Quelltermine, Auswahl und Schlüssel des aktuellen Kurses -> Anzeigegruppen bzw. hinzuzufügende Identitäten.
 * Nebenwirkungen: Keine; reine Berechnung. Übernahme in den Zustand über CalendarControlState.
 * Nicht zuständig: Anzeigefilter, Regelbearbeitung, Kandidatenberechnung oder Synchronisierungsidentität.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceOccurrenceIdentity
import com.tequila.tumcalendarcontrol.sync.SourceOutputDateWindow
import java.time.ZoneId

internal object SourceBulkSelection {
    private val Zone = ZoneId.of("Europe/Berlin")

    /** Alle wählbaren Termine (ohne abgesagte) – unabhängig von Suche und Anzeigefilter. */
    fun allSelectable(occurrences: List<SourceCalendarOccurrence>): Set<SourceOccurrenceIdentity> =
        occurrences.filterNot(SourceCalendarOccurrence::isCancelled).map(SourceCalendarOccurrence::identity).toSet()

    /** Anzeigegruppen ohne Suche/Filter: Grundlage für Zählung und Inhalt des „aktuellen Kurses“. */
    fun unfilteredGroups(occurrences: List<SourceCalendarOccurrence>, selected: Set<SourceOccurrenceIdentity>): List<SourceCourseDisplayGroup> =
        SourceCourseBrowserModel.groups(occurrences, selected, query = "", filter = SourceCourseFilter.ALL, outputWindow = SourceOutputDateWindow(), zoneId = Zone)

    fun keyOf(group: SourceCourseDisplayGroup): String = SourceCourseBrowserModel.normalizedDisplayKey(group.displayTitle)

    fun keyOf(occurrence: SourceCalendarOccurrence): String = SourceCourseBrowserModel.normalizedDisplayKey(occurrence.title)

    /** Zuletzt aufgeklappter oder angetippter Kurs; ohne gültigen Schlüssel der erste Kurs. */
    fun currentGroup(groups: List<SourceCourseDisplayGroup>, currentKey: String?): SourceCourseDisplayGroup? =
        groups.firstOrNull { keyOf(it) == currentKey } ?: groups.firstOrNull()

    /** Ganzen Kurs nur hinzufügen: andere Auswahl bleibt, erneutes Ausführen ändert nichts. */
    fun courseAdditions(group: SourceCourseDisplayGroup): Set<SourceOccurrenceIdentity> =
        group.occurrences.map(SourceCalendarOccurrence::identity).toSet()
}
