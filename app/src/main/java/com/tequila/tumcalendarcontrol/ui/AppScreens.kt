/*
 * Aufgabe: Stabile AppScreen-Werte an Compose-Seiten weiterleiten.
 * Ein-/Ausgabe: AppScreen und vorhandener Zustand -> passende Compose-Seite.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Seitenlayout, Kurs-/Einstellungslogik, Navigationsstack, Quellenlesen, Autorisierung oder Kalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.runtime.Composable

/** Explizite Routingliste nur für synthetische Unit-Tests. Laufzeit nutzt erschöpfendes when; Tests verlangen Aktualisierung bei neuen Seiten. */
internal val routedAppScreens: Set<AppScreen> =
    setOf(
    AppScreen.OVERVIEW,
    AppScreen.DETAIL,
    AppScreen.ATTENDANCE,
    AppScreen.COURSE_EDIT,
    AppScreen.SEARCH,
    AppScreen.FAVORITES,
    AppScreen.MORE,
    AppScreen.SETTINGS,
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
    )

@Composable
fun AppScreenContent(
    state: CalendarControlState,
    screen: AppScreen,
) {
    when (screen) {
        AppScreen.OVERVIEW,
        AppScreen.DETAIL,
        AppScreen.COURSE_EDIT,
        AppScreen.SEARCH,
        AppScreen.FAVORITES,
        -> CourseScreenContent(state, screen)

        AppScreen.MORE,
        AppScreen.SETTINGS,
        AppScreen.PROFILE,
        AppScreen.SYNC_CONFIGURATION,
        AppScreen.SYNC_PREVIEW,
        -> SettingsScreenContent(state, screen)

        AppScreen.ATTENDANCE,
        AppScreen.SOURCE_RANGE,
        AppScreen.SOURCE_PREVIEW,
        AppScreen.SOURCE_ATTENDANCE,
        AppScreen.MANUAL_EVENTS,
        AppScreen.MANAGED_SYNC_PREVIEW,
        AppScreen.OUTPUT_CLEANUP,
        AppScreen.BACKGROUND_CHECK,
        AppScreen.RULE_BACKUP,
        -> WorkflowScreenContent(state, screen)
    }
}

@Composable
private fun CourseScreenContent(state: CalendarControlState, screen: AppScreen) {
    when (screen) {
        AppScreen.OVERVIEW -> OverviewScreen(state)
        AppScreen.DETAIL -> DetailScreen(state)
        AppScreen.COURSE_EDIT -> CourseEditScreen(state)
        AppScreen.SEARCH -> SearchScreen(state)
        AppScreen.FAVORITES -> FavoritesScreen(state)
        else -> error(message(182))
    }
}

@Composable
private fun SettingsScreenContent(state: CalendarControlState, screen: AppScreen) {
    when (screen) {
        AppScreen.MORE -> MoreScreen(state)
        AppScreen.SETTINGS -> SettingsScreen(state)
        AppScreen.PROFILE -> ProfileScreen(state)
        AppScreen.SYNC_CONFIGURATION -> SyncConfigurationScreen(state)
        AppScreen.SYNC_PREVIEW -> SyncPreviewScreen(state)
        else -> error(message(183))
    }
}

@Composable
private fun WorkflowScreenContent(state: CalendarControlState, screen: AppScreen) {
    when (screen) {
        AppScreen.ATTENDANCE -> AttendanceScreen(state)
        AppScreen.SOURCE_RANGE -> SourceRangeScreen(state)
        AppScreen.SOURCE_PREVIEW -> SourceCourseSelectorScreen(state)
        AppScreen.SOURCE_ATTENDANCE -> SourceAttendanceScreen(state)
        AppScreen.MANUAL_EVENTS -> ManualEventsScreen(state)
        AppScreen.MANAGED_SYNC_PREVIEW -> ManagedSyncPreviewScreen(state)
        AppScreen.OUTPUT_CLEANUP -> OutputCleanupScreen(state)
        AppScreen.BACKGROUND_CHECK -> BackgroundCheckScreen(state)
        AppScreen.RULE_BACKUP -> RuleBackupScreen(state)
        else -> error(message(184))
    }
}
