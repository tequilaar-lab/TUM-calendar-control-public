/*
 * Aufgabe: Ableitung der englischen Semesterkennzeichnung der Übersicht prüfen.
 * Ein-/Ausgabe: Synthetische Kurse mit Beginnzeiten -> erwartete Kennzeichnung.
 * Nebenwirkungen: Keine; reiner JVM-Test.
 * Nicht zuständig: Darstellung des Kopfes (Bildtests).
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.domain.SampleCourses
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSemesterTest {
    private fun courseAt(iso: String) = SampleCourses.all().first().copy(startsAt = ZonedDateTime.parse(iso).toInstant())

    @Test fun withoutDatesStaysNeutral() = assertEquals(NeutralOverviewKicker, homeSemesterKicker(SampleCourses.all()))

    @Test fun octoberStartsWinterSemester() =
        assertEquals("WINTER SEMESTER   26 / 27", homeSemesterKicker(listOf(courseAt("2026-10-26T09:00+01:00[Europe/Berlin]"))))

    @Test fun earlyYearBelongsToPreviousWinter() =
        assertEquals("WINTER SEMESTER   26 / 27", homeSemesterKicker(listOf(courseAt("2027-01-11T09:00+01:00[Europe/Berlin]"))))

    @Test fun aprilStartsSummerSemester() =
        assertEquals("SUMMER SEMESTER   27", homeSemesterKicker(listOf(courseAt("2027-04-19T09:00+02:00[Europe/Berlin]"))))

    /** Der früheste Termin entscheidet, auch wenn spätere Termine im Sommer liegen. */
    @Test fun earliestSessionDecides() = assertEquals(
        "WINTER SEMESTER   26 / 27",
        homeSemesterKicker(listOf(courseAt("2027-05-03T09:00+02:00[Europe/Berlin]"), courseAt("2026-11-02T09:00+01:00[Europe/Berlin]"))),
    )
}
