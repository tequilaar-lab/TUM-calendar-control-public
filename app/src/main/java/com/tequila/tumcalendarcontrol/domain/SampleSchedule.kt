/*
 * Aufgabe: Synthetische Kurse und Regelauflösungen für die Vorführung bereitstellen.
 * Ein-/Ausgabe: Keine externe Eingabe -> künstliche Titel, UIDs, Daten und Anzeigemodelle.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Speicherung von Beispielen, Benutzerbearbeitung oder Synchronisierung.
 */
package com.tequila.tumcalendarcontrol.domain

import com.tequila.tumcalendarcontrol.i18n.message

import java.time.LocalDate
import java.time.LocalDateTime

data class PreviewEvent(
    val title: String,
    val dateTime: LocalDateTime,
    val state: AttendanceState,
)

object SampleSchedule {
    fun events(): List<PreviewEvent> {
        val rule = CourseRule(
            sourceUid = "sample-fixed-course",
            hasFixedPlus = true,
            defaultState = AttendanceState.CHOOSE_TO_ATTEND,
            stopAttendingFrom = LocalDate.of(2026, 10, 19),
            instanceOverrides = mapOf("2026-10-12T10:00" to AttendanceState.MUST_ATTEND),
        )
        val instances = listOf(
            occurrence("2026-10-05T10:00"),
            occurrence("2026-10-12T10:00"),
            occurrence("2026-10-19T10:00"),
        )
        return instances.map { occurrence ->
            PreviewEvent(
                title = message(32),
                dateTime = occurrence.start,
                state = checkNotNull(AttendanceResolver.resolve(rule, occurrence)),
            )
        }
    }

    private fun occurrence(instanceKey: String): CourseOccurrence = CourseOccurrence(
        sourceUid = "sample-fixed-course",
        instanceKey = instanceKey,
        title = message(32),
        start = LocalDateTime.parse(instanceKey),
    )
}
