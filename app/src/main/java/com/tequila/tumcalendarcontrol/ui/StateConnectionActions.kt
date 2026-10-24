/*
 * Aufgabe: Aktionen des CalendarControlState zu Ausgabezielen, Kalenderberechtigung, privater Quellenkonfiguration und
 *   Hintergrundprüfung (aus CalendarControlState ausgelagert, Verhalten unverändert).
 * Ein-/Ausgabe: Nutzerabsichten und Systemantworten -> Zustandsänderungen und Rückmeldungen.
 * Nebenwirkungen: Keine externen Aktionen; nur Zustand (Anfragen werden von CalendarControlApp ausgeführt).
 * Nicht zuständig: Lesen der Quelle, Schreiben in Kalender, Google-Autorisierung oder Darstellung.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.sync.PrivateSyncConfiguration
import com.tequila.tumcalendarcontrol.sync.SourceCheckCadence
import com.tequila.tumcalendarcontrol.sync.SourceCheckSettings
import com.tequila.tumcalendarcontrol.sync.SourceCheckStatus

/** Nur künftige Ausgabeerlaubnis ändern; Kalenderverbindung und Schreiben im nachfolgenden autorisierten Modul. */
fun CalendarControlState.setSyncDestinationEnabled(destination: SyncDestination, enabled: Boolean) {
    invalidateWritePreview()
    syncDestinations = syncDestinations.withEnabled(destination, enabled)
    if (destination == SyncDestination.GOOGLE_CALENDAR && !enabled) {
        outputWorkflow.disconnectGoogle()
    }
}

/** Schalter zunächst im Bestätigungsdialog; vorher keine Berechtigung, Tokenlöschung oder Präferenzänderung. */
fun CalendarControlState.requestSyncDestinationChange(destination: SyncDestination, enabled: Boolean) {
    if (syncDestinations.isEnabled(destination) == enabled) return
    pendingSyncDestinationChange = PendingSyncDestinationChange(destination, enabled)
}

fun CalendarControlState.cancelSyncDestinationChange() { pendingSyncDestinationChange = null }

fun CalendarControlState.confirmSyncDestinationChange() {
    val change = pendingSyncDestinationChange ?: return
    pendingSyncDestinationChange = null
    if (change.destination == SyncDestination.LOCAL_CALENDAR && change.enabled) {
        requestLocalCalendarOutput()
    } else {
        setSyncDestinationEnabled(change.destination, change.enabled)
        feedback = if (change.enabled) message(256, (change.destination.title)) else message(257, (change.destination.title))
    }
}

fun CalendarControlState.requestLocalCalendarOutput() { permissionForCurrentWriteOnly = false; localCalendarPermissionRequestPending = true }

fun CalendarControlState.requestCurrentWriteLocalPermission() { permissionForCurrentWriteOnly = true; localCalendarPermissionRequestPending = true }

fun CalendarControlState.resolveLocalCalendarPermission(granted: Boolean) {
    localCalendarPermissionRequestPending = false
    outputWorkflow.localCalendarPermissionGranted = granted
    if (!permissionForCurrentWriteOnly) {
        syncDestinations = syncDestinations.withEnabled(SyncDestination.LOCAL_CALENDAR, granted)
        invalidateWritePreview()
    }
    permissionForCurrentWriteOnly = false
    feedback = if (granted) message(258) else message(259)
}

/** Beim Start nur bestehende Systemrechte lesen; keine Anfragen oder Änderung der Ausgabeschalter. */
fun CalendarControlState.refreshLocalCalendarPermission(granted: Boolean) { outputWorkflow.localCalendarPermissionGranted = granted }

/** Formatprobleme vor Speichern anzeigen; Zustandsmaschine verbindet oder schreibt keine Kalender. */
fun CalendarControlState.updatePrivateSyncConfiguration(configuration: PrivateSyncConfiguration) {
    require(configuration.validate().isEmpty()) { message(260) }
    privateSyncConfiguration = configuration
    invalidateWritePreview()
}

/** Hintergrundprüfung nur mit konfigurierter Quelle; Einplanung liest/schreibt keinen Kalender. */
fun CalendarControlState.updateSourceCheck(enabled: Boolean, cadence: SourceCheckCadence = sourceCheckSettings.cadence) {
    if (enabled && !privateSyncConfiguration.hasTumSubscription) {
        feedback = message(261)
        return
    }
    val newlyEnabled = enabled && !sourceCheckSettings.enabled
    sourceCheckSettings = SourceCheckSettings(enabled, cadence)
    sourceCheckSchedulingRequested = true
    if (newlyEnabled) notificationPermissionRequestPending = true
    feedback = if (enabled) message(262) else message(263)
}

fun CalendarControlState.consumeSourceCheckSchedulingRequest() { sourceCheckSchedulingRequested = false }

fun CalendarControlState.consumeNotificationPermissionRequest() { notificationPermissionRequestPending = false }

fun CalendarControlState.resolveNotificationPermission(granted: Boolean) {
    notificationPermissionRequestPending = false
    if (!granted && sourceCheckSettings.enabled) feedback = message(264)
}

fun CalendarControlState.refreshSourceCheckStatus(status: SourceCheckStatus) { sourceCheckStatus = status }
