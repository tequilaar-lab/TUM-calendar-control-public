/*
 * Aufgabe: Bestätigte Ziele, kurzlebige Token und eigene Kalender mit kontrollierter Ausgabe/Rücknahme verbinden.
 * Ein-/Ausgabe: Bestätigte Ziele, private Konfiguration und Sitzungstoken -> kontrollierte Ausgabe-/Rücknahmeergebnisse.
 * Nebenwirkungen: Nur zuvor bestätigte eigene Kalenderaktionen über die Plattformadapter.
 * Nicht zuständig: iCal-Lesen, Berechtigungen, OAuth-Oberfläche, Ergebnisspeicherung, Fortschrittsdarstellung oder Hauptkalender.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.i18n.message

import android.content.Context

data class PlatformManagedSyncOutcome(
    val result: ManagedSyncResult,
    val configuration: PrivateSyncConfiguration,
) {
    /** Nur bei Google HTTP 401; Oberfläche verwirft kurzlebigen Token und verlangt erneute ausdrückliche Verbindung. */
    val requiresGoogleReauthorization: Boolean get() = result.targetResults.any {
        it.target == OutputTarget.GOOGLE_CALENDAR && it.failureCode == TargetFailureCode.GOOGLE_AUTHORIZATION_EXPIRED
    }
}

class PlatformManagedSyncRunner(private val context: Context, private val engine: ManagedSyncEngine = ManagedSyncEngine()) {
    /** Lesender Abgleich von der Bereinigungsseite; kein ensure/Erstellen und keine Löschung vorhandener Zuordnungen. */
    fun readCleanupCatalog(mappings: List<StoredEventMapping>, targets: Set<OutputTarget>, configuration: PrivateSyncConfiguration, googleAccessToken: String?): CleanupCatalogReadResult {
        val bindings = buildList {
            if (OutputTarget.LOCAL_CALENDAR in targets) {
                val resolution = AndroidLocalCalendarTarget(context.contentResolver).resolveExisting(configuration.localCalendarId)
                if (resolution is CalendarTargetResolution.Success) add(TargetBinding(resolution.calendar, AndroidLocalCalendarGateway(context.contentResolver)))
            }
            if (OutputTarget.GOOGLE_CALENDAR in targets && !googleAccessToken.isNullOrBlank()) {
                val api = GoogleCalendarRestApi(googleAccessToken)
                val resolution = GoogleCalendarTarget(api).resolveExisting(configuration.googleCalendarId)
                if (resolution is CalendarTargetResolution.Success) add(TargetBinding(resolution.calendar, GoogleCalendarGateway(api)))
            }
        }
        val result = ManagedCleanupCatalog.read(mappings, bindings)
        val unavailable = mappings.count { it.target in targets && bindings.none { binding -> binding.calendar.target == it.target } }
        return result.copy(failedCount = result.failedCount + unavailable)
    }
    /** Ausdrücklich ausgelöste lesende Diagnose prüft nur gespeicherten App-Kalender; kein Erstellen oder Schreiben. */
    fun checkGoogleConnection(configuration: PrivateSyncConfiguration, googleAccessToken: String?): GoogleConnectionCheckResult {
        if (googleAccessToken.isNullOrBlank()) return GoogleConnectionCheckResult.Failed(message(108))
        return GoogleConnectionChecker(GoogleCalendarRestApi(googleAccessToken)).check(configuration.googleCalendarId)
    }

    fun execute(
        desired: List<DesiredCalendarEvent>,
        mappings: List<StoredEventMapping>,
        targets: Set<OutputTarget>,
        configuration: PrivateSyncConfiguration,
        googleAccessToken: String?,
    ): PlatformManagedSyncOutcome = run(desired, mappings, targets, configuration, googleAccessToken, rollback = false)

    fun rollback(
        mappings: List<StoredEventMapping>,
        targets: Set<OutputTarget>,
        configuration: PrivateSyncConfiguration,
        googleAccessToken: String?,
        scope: ManagedEventCleanupScope = ManagedEventCleanupScope.AllManagedEvents,
    ): PlatformManagedSyncOutcome = run(emptyList(), mappings, targets, configuration, googleAccessToken, rollback = true, cleanupScope = scope)

    private fun run(
        desired: List<DesiredCalendarEvent>,
        mappings: List<StoredEventMapping>,
        targets: Set<OutputTarget>,
        configuration: PrivateSyncConfiguration,
        googleAccessToken: String?,
        rollback: Boolean,
        cleanupScope: ManagedEventCleanupScope = ManagedEventCleanupScope.AllManagedEvents,
    ): PlatformManagedSyncOutcome {
        var nextConfiguration = configuration
        val bindings = mutableListOf<TargetBinding>()
        val failures = mutableListOf<TargetSyncResult>()
        if (OutputTarget.LOCAL_CALENDAR in targets) {
            when (val resolution = if (rollback) AndroidLocalCalendarTarget(context.contentResolver).resolveExisting(configuration.localCalendarId)
            else AndroidLocalCalendarTarget(context.contentResolver).ensure(configuration.localCalendarId)) {
                is CalendarTargetResolution.Success -> {
                    nextConfiguration = nextConfiguration.copy(localCalendarId = resolution.calendar.calendarId)
                    bindings += TargetBinding(resolution.calendar, AndroidLocalCalendarGateway(context.contentResolver))
                }
                is CalendarTargetResolution.Failure -> failures += TargetSyncResult(
                    OutputTarget.LOCAL_CALENDAR,
                    mappings,
                    emptyList(),
                    resolution.summary,
                    resolution.failureCode,
                )
            }
        }
        if (OutputTarget.GOOGLE_CALENDAR in targets) {
            if (googleAccessToken.isNullOrBlank()) {
                failures += TargetSyncResult(OutputTarget.GOOGLE_CALENDAR, mappings, emptyList(), message(109))
            } else {
                val api = GoogleCalendarRestApi(googleAccessToken)
                when (val resolution = if (rollback) GoogleCalendarTarget(api).resolveExisting(configuration.googleCalendarId)
                else GoogleCalendarTarget(api).ensure(configuration.googleCalendarId, configuration.googleCalendarName)) {
                    is CalendarTargetResolution.Success -> {
                        nextConfiguration = nextConfiguration.copy(googleCalendarId = resolution.calendar.calendarId)
                        bindings += TargetBinding(resolution.calendar, GoogleCalendarGateway(api))
                    }
                    is CalendarTargetResolution.Failure -> failures += TargetSyncResult(
                        OutputTarget.GOOGLE_CALENDAR,
                        mappings,
                        emptyList(),
                        resolution.summary,
                        resolution.failureCode,
                    )
                }
            }
        }
        // Geprüftes Ziel vor Terminen speichern, damit es nach Prozessende wieder auffindbar ist.
        val configurationStore = PrivateSyncConfigurationStore(context)
        configurationStore.save(configurationStore.load().copy(localCalendarId = nextConfiguration.localCalendarId, googleCalendarId = nextConfiguration.googleCalendarId))
        val completed = if (rollback) engine.rollback(mappings, bindings, cleanupScope) else engine.execute(desired, mappings, bindings, RuleLedgerPreferences(context)::checkpointMappings)
        return PlatformManagedSyncOutcome(
            result = completed.copy(targetResults = completed.targetResults + failures),
            configuration = nextConfiguration,
        )
    }
}
