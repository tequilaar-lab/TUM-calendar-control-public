/*
 * Aufgabe: Lokale Kursregeln zum Teilnahmestatus eines Termins auflösen.
 * Ein-/Ausgabe: CourseRule und gleichartige CourseOccurrence -> AttendanceState oder null bei fehlender Zulassung.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: iCal-Lesen, RRULE-Auflösung, Regelspeicherung oder Google-/HONOR-Kalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.domain

import com.tequila.tumcalendarcontrol.i18n.message

object AttendanceResolver {
    /** Nur bestätigte Fixplatz-Termine mit übereinstimmender UID dürfen in Zielkalender gelangen. */
    fun resolve(rule: CourseRule, occurrence: CourseOccurrence): AttendanceState? {
        require(rule.sourceUid == occurrence.sourceUid) {
            message(0)
        }
        if (!rule.hasFixedPlus) return null

        // Manuelle Einzelterminauswahl hat Vorrang und erlaubt spätere Teilnahmeausnahmen.
        rule.instanceOverrides[occurrence.instanceKey]?.let { return it }

        // Stichtag wirkt nur künftig; frühere Termine behalten Grün/Blau.
        if (rule.stopAttendingFrom != null && !occurrence.start.toLocalDate().isBefore(rule.stopAttendingFrom)) {
            return AttendanceState.STOP_ATTENDING
        }
        return rule.defaultState
    }
}
