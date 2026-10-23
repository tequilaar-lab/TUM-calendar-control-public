/*
 * Aufgabe: Englische Semesterkennzeichnung für den Kopf der Übersicht aus den Kursterminen ableiten.
 * Ein-/Ausgabe: Kurse mit optionalem Beginn -> „WINTER SEMESTER 26 / 27“, „SUMMER SEMESTER 27“ oder neutrale Kennzeichnung.
 * Nebenwirkungen: Keine; reine Berechnung.
 * Nicht zuständig: Übersetzung (Kleinbeschriftungen bleiben auf Nutzerwunsch englisch) oder Kursauswahl.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.domain.Course
import java.time.ZoneId

/** Ohne Termine neutral; bewusst nicht vom heutigen Datum abhängig, damit Bildtests stabil bleiben. */
internal const val NeutralOverviewKicker = "COURSES / OVERVIEW"

/**
 * Der früheste Termin bestimmt das Semester (Europe/Berlin): Oktober–März Wintersemester, April–September Sommersemester.
 * Jahreszahlen zweistellig wie im bestätigten Entwurf.
 */
internal fun homeSemesterKicker(courses: List<Course>): String {
    val first = courses.mapNotNull { it.startsAt }.minOrNull() ?: return NeutralOverviewKicker
    val date = first.atZone(ZoneId.of("Europe/Berlin")).toLocalDate()
    val shortYear = date.year % 100
    return when (date.monthValue) {
        in 10..12 -> "WINTER SEMESTER   %02d / %02d".format(shortYear, (shortYear + 1) % 100)
        in 1..3 -> "WINTER SEMESTER   %02d / %02d".format((shortYear + 99) % 100, shortYear)
        else -> "SUMMER SEMESTER   %02d".format(shortYear)
    }
}
