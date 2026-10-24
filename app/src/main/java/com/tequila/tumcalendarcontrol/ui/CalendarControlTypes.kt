/*
 * Aufgabe: Gemeinsame Typen des Oberflächenzustands: Seiten, Darstellungsmodus, Navigationsrichtung, Favoritenaktion,
 *   Google-Autorisierungsstatus und Bearbeitungsbereich der Teilnahme (aus CalendarControlState ausgelagert).
 * Ein-/Ausgabe: Aufzählungen mit Beschriftungen; primaryIndex ordnet Haupttabs.
 * Nebenwirkungen: Keine.
 * Nicht zuständig: Zustandsänderungen (CalendarControlState) oder Seitendarstellung.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

enum class AppScreen {
    OVERVIEW, DETAIL, ATTENDANCE, COURSE_EDIT, SEARCH, FAVORITES, SETTINGS, PROFILE, SYNC_CONFIGURATION, SYNC_PREVIEW, SOURCE_RANGE, SOURCE_PREVIEW, SOURCE_ATTENDANCE, MANUAL_EVENTS, MANAGED_SYNC_PREVIEW, OUTPUT_CLEANUP, BACKGROUND_CHECK, RULE_BACKUP, APPEARANCE, SOURCE_CHANGES, WIDGETS, CLASS_LIVE,
}

enum class ThemeMode(val label: String) {
    LIGHT(message(222)),
    DARK(message(223)),
    SYSTEM(message(224)),
}

enum class NavigationDirection {
    FORWARD, BACKWARD, PROFILE_ENTER, PROFILE_EXIT,
}

/** Teilnahme und Favoriten gemeinsam setzen; unverändert verhindert unbeabsichtigte Favoritenänderungen. */
enum class SourceFavoriteAction(val label: String, val description: String) {
    KEEP(message(225), message(226)),
    ADD(message(227), message(228)),
    REMOVE(message(229), message(230)),
}

/** Google-Autorisierung nur für aktuellen Prozess; Token nie persistieren, nach Neustart erneut autorisieren. */
enum class GoogleAuthorizationStatus(val title: String, val defaultDetail: String) {
    NOT_CONNECTED(message(231), message(232)),
    AUTHORIZING(message(233), message(234)),
    AUTHORIZED(message(235), message(236)),
    FAILED(message(237), message(238)),
}

enum class AttendanceEditScope(val label: String, val description: String) {
    ALL_OCCURRENCES(message(319), message(320)),
    ONLY_THIS_OCCURRENCE(message(321), message(322)),
    CURRENT_AND_FUTURE(message(323), message(324)),
    SPECIFIED_AND_FUTURE(message(325), message(326)),
}

internal fun AppScreen.primaryIndex(): Int? = when (this) {
    AppScreen.OVERVIEW -> 0
    AppScreen.SEARCH -> 1
    AppScreen.FAVORITES -> 2
    AppScreen.SETTINGS -> 3
    else -> null
}
