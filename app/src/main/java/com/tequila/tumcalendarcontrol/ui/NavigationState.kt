/*
 * Aufgabe: Seiten, Rückkehrziele, Überlagerungshintergrund und Navigationsrichtung halten.
 * Ein-/Ausgabe: AppScreen-Absicht -> aktuelle/stabile Seite und Rückkehrverhalten.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Kursauswahl, Regelbearbeitung, Quellenlesen, Autorisierung, Synchronisierungsvorschau oder Ausgabeausführung.
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class NavigationState {
    var screen by mutableStateOf(AppScreen.OVERVIEW)
        private set
    var direction by mutableStateOf(NavigationDirection.FORWARD)
        private set

    private var detailReturnScreen by mutableStateOf(AppScreen.OVERVIEW)
    private var profileReturnScreen by mutableStateOf(AppScreen.OVERVIEW)
    private var syncConfigurationReturnScreen by mutableStateOf(AppScreen.SETTINGS)
    private var sourceRangeReturnScreen by mutableStateOf(AppScreen.SETTINGS)
    private var syncPreviewReturnScreen by mutableStateOf(AppScreen.SETTINGS)
    private var courseEditReturnScreen by mutableStateOf(AppScreen.DETAIL)
    private var sourceAttendanceReturnScreen by mutableStateOf(AppScreen.SOURCE_PREVIEW)
    private var manualEventsReturnScreen by mutableStateOf(AppScreen.SETTINGS)
    private var managedSyncPreviewReturnScreen by mutableStateOf(AppScreen.SETTINGS)
    private var outputCleanupReturnScreen by mutableStateOf(AppScreen.SETTINGS)
    private var backgroundCheckReturnScreen by mutableStateOf(AppScreen.SETTINGS)
    private var ruleBackupReturnScreen by mutableStateOf(AppScreen.SETTINGS)
    private var sourceChangesReturnScreen by mutableStateOf(AppScreen.SOURCE_PREVIEW)

    val contentScreen: AppScreen
        get() = screen

    fun open(destination: AppScreen) {
        direction = directionBetween(contentScreen, destination)
        screen = destination
    }

    fun openDetail() { detailReturnScreen = contentScreen; open(AppScreen.DETAIL) }
    fun openCourseEdit() { courseEditReturnScreen = AppScreen.DETAIL; open(AppScreen.COURSE_EDIT) }
    /** Profil mit eigener Richtungskennung öffnen/verlassen (Darstellung: von rechts herein, nach rechts hinaus). */
    fun openProfile() { profileReturnScreen = contentScreen; direction = NavigationDirection.PROFILE_ENTER; screen = AppScreen.PROFILE }
    fun openSyncConfiguration() { syncConfigurationReturnScreen = contentScreen; openChild(AppScreen.SYNC_CONFIGURATION) }
    fun openSourceRange() { sourceRangeReturnScreen = contentScreen; direction = NavigationDirection.FORWARD; screen = AppScreen.SOURCE_RANGE }
    fun openSyncPreview() { syncPreviewReturnScreen = contentScreen; openChild(AppScreen.SYNC_PREVIEW) }
    fun openSourcePreview() { syncPreviewReturnScreen = contentScreen; openChild(AppScreen.SOURCE_PREVIEW) }
    fun openExistingSourcePreview() { openChild(AppScreen.SOURCE_PREVIEW) }
    fun openSourceAttendance() { sourceAttendanceReturnScreen = AppScreen.SOURCE_PREVIEW; openChild(AppScreen.SOURCE_ATTENDANCE) }
    fun openManualEvents() { manualEventsReturnScreen = contentScreen; openChild(AppScreen.MANUAL_EVENTS) }
    fun openManagedSyncPreview() { managedSyncPreviewReturnScreen = contentScreen; openChild(AppScreen.MANAGED_SYNC_PREVIEW) }
    fun openOutputCleanup() { outputCleanupReturnScreen = contentScreen; openChild(AppScreen.OUTPUT_CLEANUP) }
    fun openBackgroundCheck() { backgroundCheckReturnScreen = contentScreen; direction = NavigationDirection.FORWARD; screen = AppScreen.BACKGROUND_CHECK }
    fun openRuleBackup() { ruleBackupReturnScreen = contentScreen; direction = NavigationDirection.FORWARD; screen = AppScreen.RULE_BACKUP }
    fun openSourceChanges() { sourceChangesReturnScreen = contentScreen; openChild(AppScreen.SOURCE_CHANGES) }

    fun handleBack(): Boolean {
        val closingProfile = screen == AppScreen.PROFILE
        val destination = when (screen) {
            AppScreen.ATTENDANCE -> AppScreen.DETAIL
            AppScreen.COURSE_EDIT -> courseEditReturnScreen
            AppScreen.DETAIL -> detailReturnScreen
            AppScreen.PROFILE -> profileReturnScreen
            AppScreen.SYNC_CONFIGURATION -> syncConfigurationReturnScreen
            AppScreen.SYNC_PREVIEW -> syncPreviewReturnScreen
            AppScreen.SOURCE_RANGE -> sourceRangeReturnScreen
            AppScreen.SOURCE_PREVIEW -> syncPreviewReturnScreen
            AppScreen.SOURCE_ATTENDANCE -> sourceAttendanceReturnScreen
            AppScreen.MANUAL_EVENTS -> manualEventsReturnScreen
            AppScreen.MANAGED_SYNC_PREVIEW -> managedSyncPreviewReturnScreen
            AppScreen.OUTPUT_CLEANUP -> outputCleanupReturnScreen
            AppScreen.BACKGROUND_CHECK -> backgroundCheckReturnScreen
            AppScreen.RULE_BACKUP -> ruleBackupReturnScreen
            AppScreen.APPEARANCE -> AppScreen.SETTINGS
            AppScreen.SOURCE_CHANGES -> sourceChangesReturnScreen
            else -> return false
        }
        direction = if (closingProfile) NavigationDirection.PROFILE_EXIT else NavigationDirection.BACKWARD
        screen = destination
        return true
    }

    fun canHandleBack(): Boolean = screen !in setOf(
        AppScreen.OVERVIEW,
        AppScreen.SEARCH,
        AppScreen.FAVORITES,
        AppScreen.SETTINGS,
    )

    private fun directionBetween(source: AppScreen, destination: AppScreen): NavigationDirection {
        val sourceIndex = source.primaryIndex()
        val destinationIndex = destination.primaryIndex()
        return if (sourceIndex != null && destinationIndex != null && destinationIndex < sourceIndex) {
            NavigationDirection.BACKWARD
        } else {
            NavigationDirection.FORWARD
        }
    }

    /** Unterseiten kommen immer von rechts, unabhängig von letzter Haupttab-Richtung. */
    private fun openChild(destination: AppScreen) {
        direction = NavigationDirection.FORWARD
        screen = destination
    }
}
