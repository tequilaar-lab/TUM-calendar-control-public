/*
 * Aufgabe: Synthetische Fixplatz-Kurse für die Offline-Oberfläche bereitstellen.
 * Ein-/Ausgabe: Keine Eingabe -> fünf künstliche Kurse ohne echte Quellen oder Konten.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Simulation echter TUM-Daten, Fixplatz-Ableitung oder Synchronisierungseingaben.
 */
package com.tequila.tumcalendarcontrol.domain

import com.tequila.tumcalendarcontrol.i18n.message

object SampleCourses {
    fun all(): List<Course> = listOf(
        Course("course-a", message(21), message(22), message(23), AttendanceState.MUST_ATTEND, false),
        Course("course-b", message(24), message(25), message(23), AttendanceState.CHOOSE_TO_ATTEND, true, sourceStatus = TumEventStatus.PLANNED),
        Course("course-c", message(26), message(27), message(23), AttendanceState.STOP_ATTENDING, false, sourceStatus = TumEventStatus.MOVED),
        Course("course-d", message(28), message(29), message(23), AttendanceState.CHOOSE_TO_ATTEND, false, sourceStatus = TumEventStatus.CANCELLED),
        Course("course-e", message(30), message(31), message(23), AttendanceState.MUST_ATTEND, false),
    )
}
