/*
 * Aufgabe: iCal-Katalog auf gemeinsame Kurse für Übersicht, Suche, Favoriten und Details projizieren.
 * Ein-/Ausgabe: UID-Katalog, Instanzen, Regeln und bisherige Anzeigen -> stabil nach UID verbundene Kursliste.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Quellen-Parsing, Fixplatz-Entscheidungen, Regelbearbeitung, Kalenderdifferenzen oder Synchronisierungsausführung.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.domain.Course
import com.tequila.tumcalendarcontrol.domain.CourseWeekday
import com.tequila.tumcalendarcontrol.domain.TumEventStatus
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceCourse
import com.tequila.tumcalendarcontrol.sync.SyncRule
import com.tequila.tumcalendarcontrol.sync.SyncRuleResolver
import java.time.DayOfWeek
import java.time.ZoneId

/** Quelltitel nur Anzeige; ausschließlich sourceUid übernimmt Favoriten/Änderungen zur nächsten Lesung. */
internal object SourceCourseProjection {
    private val sourceZone: ZoneId = ZoneId.of("Europe/Berlin")

    fun courses(
        sourceCourses: List<SourceCourse>,
        occurrences: List<SourceCalendarOccurrence>,
        rules: List<SyncRule>,
        previousCourses: List<Course>,
        savedPresentations: Map<String, CoursePresentation> = emptyMap(),
    ): List<Course> {
        val previousByUid = previousCourses.associateBy(Course::id)
        val rulesByUid = rules.associateBy(SyncRule::sourceUid)
        return occurrences.sortedBy { it.startsAt }.map { occurrence ->
            val source = sourceCourses.first { it.sourceUid == occurrence.sourceUid }
            val previous = previousByUid[source.sourceUid]
            val presentation = previous?.let { CoursePresentation(it.isFavorite, it.localOverride) }
                ?: savedPresentations[source.sourceUid]
                ?: CoursePresentation()
            Course(
                id = source.sourceUid,
                title = occurrence.title,
                schedule = occurrence.toDisplaySchedule(),
                location = occurrence.location,
                attendance = occurrence.let { item ->
                    rulesByUid[source.sourceUid]?.let { SyncRuleResolver.resolve(it, item, sourceZone) }
                } ?: previous?.attendance ?: AttendanceState.CHOOSE_TO_ATTEND,
                isFavorite = presentation.isFavorite ?: false,
                // iCal hat keinen verlässlichen TUM-Anmeldestatus; alter Fixplatz-Filter darf keine Kurse ausblenden.
                hasFixedPlus = true,
                sourceStatus = if (occurrence.isCancelled) TumEventStatus.CANCELLED else TumEventStatus.UNKNOWN,
                localOverride = presentation.localOverride,
                instanceKey = occurrence.instanceKey,
                startsAt = occurrence.startsAt,
                endsAt = occurrence.endsAt,
            )
        }
    }

    private fun SourceCalendarOccurrence.toDisplaySchedule(): String {
        val start = startsAt.atZone(sourceZone)
        val end = endsAt.atZone(sourceZone)
        val timeFormat = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val endDate = if (start.toLocalDate() == end.toLocalDate()) "" else message(611, (end.toLocalDate()))
        return message(612, (start.toLocalDate()), (start.dayOfWeek.toCourseWeekday().label), (start.format(timeFormat)), (endDate), (end.format(timeFormat)))
    }

    private fun DayOfWeek.toCourseWeekday(): CourseWeekday = when (this) {
        DayOfWeek.MONDAY -> CourseWeekday.MONDAY
        DayOfWeek.TUESDAY -> CourseWeekday.TUESDAY
        DayOfWeek.WEDNESDAY -> CourseWeekday.WEDNESDAY
        DayOfWeek.THURSDAY -> CourseWeekday.THURSDAY
        DayOfWeek.FRIDAY -> CourseWeekday.FRIDAY
        DayOfWeek.SATURDAY -> CourseWeekday.SATURDAY
        DayOfWeek.SUNDAY -> CourseWeekday.SUNDAY
    }
}
