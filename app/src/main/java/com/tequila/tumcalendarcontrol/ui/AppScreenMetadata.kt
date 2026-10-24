/*
 * Aufgabe: Statische Seitenmetadaten und Hauptnavigation definieren.
 * Ein-/Ausgabe: AppScreen -> Titel, Untertitel und Navigationsmerkmale.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Seitendarstellung, Benutzeraktionen, Zustandsspeicherung, Berechtigungen oder Kalenderzugriffe.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

internal fun AppScreen.usesBottomNavigation(): Boolean = this in setOf(
    AppScreen.OVERVIEW,
    AppScreen.SEARCH,
    AppScreen.FAVORITES,
    AppScreen.SETTINGS,
)

internal fun AppScreen.usesBackArrow(): Boolean = this in setOf(
    AppScreen.DETAIL,
    AppScreen.ATTENDANCE,
    AppScreen.COURSE_EDIT,
    AppScreen.PROFILE,
    AppScreen.SYNC_CONFIGURATION,
    AppScreen.SYNC_PREVIEW,
    AppScreen.SOURCE_RANGE,
    AppScreen.SOURCE_PREVIEW,
    AppScreen.SOURCE_ATTENDANCE,
    AppScreen.MANUAL_EVENTS,
    AppScreen.MANAGED_SYNC_PREVIEW,
    AppScreen.OUTPUT_CLEANUP,
    AppScreen.BACKGROUND_CHECK,
    AppScreen.RULE_BACKUP,
    AppScreen.APPEARANCE,
    AppScreen.SOURCE_CHANGES,
)

internal fun AppScreen.bottomNavigationIndex(): Int = when (this) {
    AppScreen.OVERVIEW -> 0
    AppScreen.SEARCH -> 1
    AppScreen.FAVORITES -> 2
    AppScreen.SETTINGS -> 3
    else -> 0
}

internal fun AppScreen.title(): String = when (this) {
    AppScreen.OVERVIEW -> message(151)
    AppScreen.DETAIL -> message(152)
    AppScreen.ATTENDANCE -> message(153)
    AppScreen.COURSE_EDIT -> message(154)
    AppScreen.SEARCH -> message(155)
    AppScreen.FAVORITES -> message(156)
    AppScreen.SETTINGS -> message(158)
    AppScreen.PROFILE -> message(159)
    AppScreen.SYNC_CONFIGURATION -> message(160)
    AppScreen.SYNC_PREVIEW -> message(161)
    AppScreen.SOURCE_RANGE -> message(162)
    AppScreen.SOURCE_PREVIEW -> message(163)
    AppScreen.SOURCE_ATTENDANCE -> message(164)
    AppScreen.MANUAL_EVENTS -> message(165)
    AppScreen.MANAGED_SYNC_PREVIEW -> message(166)
    AppScreen.OUTPUT_CLEANUP -> message(167)
    AppScreen.BACKGROUND_CHECK -> message(168)
    AppScreen.RULE_BACKUP -> message(169)
    AppScreen.APPEARANCE -> message(580)
    AppScreen.SOURCE_CHANGES -> message(768)
}

internal fun AppScreen.subtitle(): String? = when (this) {
    AppScreen.OVERVIEW -> message(170)
    AppScreen.SETTINGS -> message(171)
    AppScreen.SYNC_CONFIGURATION -> message(172)
    AppScreen.SYNC_PREVIEW -> message(173)
    AppScreen.SOURCE_RANGE -> message(174)
    AppScreen.SOURCE_PREVIEW -> message(175)
    AppScreen.SOURCE_ATTENDANCE -> message(176)
    AppScreen.MANUAL_EVENTS -> message(177)
    AppScreen.MANAGED_SYNC_PREVIEW -> message(178)
    AppScreen.OUTPUT_CLEANUP -> message(179)
    AppScreen.BACKGROUND_CHECK -> message(180)
    AppScreen.RULE_BACKUP -> message(181)
    AppScreen.SOURCE_CHANGES -> message(769)
    else -> null
}

/**
 * Englische Kleinbeschriftung über dem Seitentitel; auf Nutzerwunsch in allen Sprachen gleich und daher keine Übersetzungsressource.
 * Die Übersicht leitet ihre Kennzeichnung aus den Kursterminen ab (homeSemesterKicker).
 */
internal fun AppScreen.kicker(): String = when (this) {
    AppScreen.OVERVIEW -> NeutralOverviewKicker
    AppScreen.DETAIL -> "COURSE / DETAIL"
    AppScreen.ATTENDANCE -> "COURSE / PREFERENCES"
    AppScreen.COURSE_EDIT -> "COURSE / EDIT"
    AppScreen.SEARCH -> "FIND A COURSE"
    AppScreen.FAVORITES -> "COLLECTION"
    AppScreen.SETTINGS -> "PREFERENCES"
    AppScreen.PROFILE -> "PROFILE"
    AppScreen.SYNC_CONFIGURATION -> "CONNECTIONS"
    AppScreen.SYNC_PREVIEW -> "DEMO / OFFLINE"
    AppScreen.SOURCE_RANGE -> "01 / SOURCE"
    AppScreen.SOURCE_PREVIEW -> "02 / COURSES"
    AppScreen.SOURCE_ATTENDANCE -> "03 / PREFERENCES"
    AppScreen.MANUAL_EVENTS -> "PERSONAL / EVENTS"
    AppScreen.MANAGED_SYNC_PREVIEW -> "04 / REVIEW"
    AppScreen.OUTPUT_CLEANUP -> "CALENDARS / CLEANUP"
    AppScreen.BACKGROUND_CHECK -> "UPDATES / READ ONLY"
    AppScreen.RULE_BACKUP -> "DATA / BACKUP"
    AppScreen.APPEARANCE -> "APPEARANCE"
    AppScreen.SOURCE_CHANGES -> "COURSES / CHANGES"
}
