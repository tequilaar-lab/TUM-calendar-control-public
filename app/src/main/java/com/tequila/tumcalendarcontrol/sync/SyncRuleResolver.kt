/*
 * Aufgabe: Dreizustandsregeln, künftige Stichtage und Einzelterminausnahmen auf Quelltermine anwenden.
 * Ein-/Ausgabe: Quellinstanz und Dreizustandsregel -> aufgelöster Status oder Ausschluss.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: iCalendar-Parsing, Fixplatz-Ableitung, Regelspeicherung, Differenzberechnung oder Kalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.i18n.message

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.LocalDate
import java.time.ZoneId

data class SyncRule(
    val sourceUid: String,
    /** Historisches Feld nur zur Kompatibilität privater Regeln; keine Import-/Ausgabesperre mehr. */
    val hasConfirmedFixplatz: Boolean,
    val defaultState: AttendanceState,
    val stopAttendingFrom: LocalDate? = null,
    /** Datumsregeln für Grün/Blau/Grau; Vorrang vor Standardregel, nach Einzelterminausnahme. */
    val futureStateOverrides: Map<LocalDate, AttendanceState> = emptyMap(),
    val instanceOverrides: Map<String, AttendanceState> = emptyMap(),
    val futureInstantOverrides: Map<java.time.Instant, AttendanceState> = emptyMap(),
)

object SyncRuleResolver {
    /** Einzelterminausnahme zuerst; grauer Stichtag betrifft nur diesen und folgende Tage. Abgesagte Termine bleiben ausgeschlossen. */
    fun resolve(rule: SyncRule, occurrence: SourceCalendarOccurrence, zoneId: ZoneId): AttendanceState? {
        require(rule.sourceUid == occurrence.sourceUid) { message(134) }
        if (occurrence.isCancelled) return null
        rule.instanceOverrides[occurrence.instanceKey]?.let { return it }
        val date = occurrence.startsAt.atZone(zoneId).toLocalDate()
        (rule.futureStateOverrides.mapKeys { it.key.atStartOfDay(zoneId).toInstant() } + rule.futureInstantOverrides)
            .filterKeys { !it.isAfter(occurrence.startsAt) }
            .maxByOrNull { it.key }
            ?.value
            ?.let { return it }
        return if (rule.stopAttendingFrom != null && !date.isBefore(rule.stopAttendingFrom)) {
            AttendanceState.STOP_ATTENDING
        } else {
            rule.defaultState
        }
    }
}
