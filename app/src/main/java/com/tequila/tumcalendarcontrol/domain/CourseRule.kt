/*
 * Aufgabe: Fixplatz-Zulassung, Standardstatus, Stichtag und Einzelterminausnahmen eines Kurses darstellen.
 * Ein-/Ausgabe: Lokale Regelwerte -> von AttendanceResolver auswertbare Kursregel.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Fixplatz-Erkennung in TUMonline oder Erzeugung von Wiederholungsinstanzen.
 */
package com.tequila.tumcalendarcontrol.domain

import java.time.LocalDate

data class CourseRule(
    val sourceUid: String,
    val hasFixedPlus: Boolean,
    val defaultState: AttendanceState,
    val stopAttendingFrom: LocalDate? = null,
    val instanceOverrides: Map<String, AttendanceState> = emptyMap(),
)
