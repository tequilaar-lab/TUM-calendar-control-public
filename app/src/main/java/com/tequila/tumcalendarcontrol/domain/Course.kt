/*
 * Aufgabe: Gemeinsames Kursmodell für Übersicht und Details definieren.
 * Ein-/Ausgabe: Kursfelder -> Kursanzeige, Teilnahme und Favoritenstatus.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Bestätigung echter Fixplätze, Speicherung von Benutzeränderungen oder Kalendersynchronisierung.
 */
package com.tequila.tumcalendarcontrol.domain

import com.tequila.tumcalendarcontrol.i18n.message

data class Course(
    val id: String,
    val title: String,
    val schedule: String,
    val location: String,
    val attendance: AttendanceState,
    val isFavorite: Boolean,
    val hasFixedPlus: Boolean = true,
    val sourceStatus: TumEventStatus = TumEventStatus.FIX,
    val localOverride: CourseLocalOverride = CourseLocalOverride(),
    /** Anzeigezeile unabhängig von Kurs-UID; Einzelaktionen benötigen den ursprünglichen Instanzschlüssel. */
    val instanceKey: String? = null,
    val startsAt: java.time.Instant? = null,
    val endsAt: java.time.Instant? = null,
)

val Course.browseKey: String get() = "$id\u0000${instanceKey.orEmpty()}"

/** Quellstatus dient nur dem Anzeigefilter; ersetzt keine bestätigte Fixplatz-Ausgabeberechtigung. */
enum class TumEventStatus(val label: String) {
    UNKNOWN(message(10)), FIX("fix"), PLANNED("geplant"), MOVED("verschoben"), CANCELLED("abgesagt"), REJECTED("abgelehnt"), DELETED("gelöscht"),
}

/** Lokale Änderungen überschreiben keine Quellfelder; Aktualisierungen behalten Originaldaten. */
data class CourseLocalOverride(
    val title: String? = null,
    val schedule: String? = null,
    val location: String? = null,
    val meetingTime: CourseMeetingTime? = null,
)

/** Strukturierte Zeiten vermeiden manuelle Doppelpunkte; Kalenderausgabe nutzt Werte statt Anzeigetext. */
data class CourseMeetingTime(
    val weekday: CourseWeekday,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
) {
    init {
        require(startHour in 0..23 && endHour in 0..23) { message(11) }
        require(startMinute in 0..59 && endMinute in 0..59) { message(12) }
        require(endHour * 60 + endMinute > startHour * 60 + startMinute) { message(13) }
    }
    fun display(): String = message(679, weekday.label, startHour.twoDigits(), startMinute.twoDigits(), endHour.twoDigits(), endMinute.twoDigits())
}

enum class CourseWeekday(val label: String) { MONDAY(message(14)), TUESDAY(message(15)), WEDNESDAY(message(16)), THURSDAY(message(17)), FRIDAY(message(18)), SATURDAY(message(19)), SUNDAY(message(20)) }
private fun Int.twoDigits(): String = toString().padStart(2, '0')

val Course.displayTitle: String get() = localOverride.title ?: title
val Course.displaySchedule: String get() {
    val meeting = localOverride.meetingTime ?: return localOverride.schedule ?: schedule
    val originalDate = startsAt?.atZone(java.time.ZoneId.of("Europe/Berlin"))?.toLocalDate() ?: return meeting.display()
    val editedDate = originalDate.plusDays((meeting.weekday.ordinal + 1 - originalDate.dayOfWeek.value).toLong())
    return message(675, (editedDate), (meeting.display()))
}
val Course.displayLocation: String get() = localOverride.location ?: location
