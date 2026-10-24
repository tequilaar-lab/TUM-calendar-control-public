/*
 * Aufgabe: Navigation, Darstellung, Kurse, Suche, Favoriten, Teilnahme, Profil und Ausgabezustände halten.
 * Ein-/Ausgabe: Benutzerabsichten und Adapterergebnisse -> beobachtbarer Kurs-/Navigations-/Ausgabezustand.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Datenbank, Datumsdialog, echte Kalenderausgabe, Netzwerksynchronisierung oder Kontoidentität.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.i18n.joinMessages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.domain.Course
import com.tequila.tumcalendarcontrol.domain.CourseLocalOverride
import com.tequila.tumcalendarcontrol.domain.SampleCourses
import com.tequila.tumcalendarcontrol.domain.TumEventStatus
import com.tequila.tumcalendarcontrol.domain.displayTitle
import com.tequila.tumcalendarcontrol.domain.browseKey
import com.tequila.tumcalendarcontrol.sync.PrivateSyncConfiguration
import com.tequila.tumcalendarcontrol.sync.OfflinePreviewSamples
import com.tequila.tumcalendarcontrol.sync.RuleLedger
import com.tequila.tumcalendarcontrol.sync.SyncPreview
import com.tequila.tumcalendarcontrol.sync.IcalReadOnlyImportResult
import com.tequila.tumcalendarcontrol.sync.IcalReadOnlyPreview
import com.tequila.tumcalendarcontrol.sync.SourceCourse
import com.tequila.tumcalendarcontrol.sync.RuleEditCommand
import com.tequila.tumcalendarcontrol.sync.RuleLedgerEditor
import com.tequila.tumcalendarcontrol.sync.SyncRule
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceChangeSummary
import com.tequila.tumcalendarcontrol.sync.SourceOccurrenceIdentity
import com.tequila.tumcalendarcontrol.sync.SyncCandidateFactory
import com.tequila.tumcalendarcontrol.sync.ManagedSyncEngine
import com.tequila.tumcalendarcontrol.sync.ManagedSyncPreview
import com.tequila.tumcalendarcontrol.sync.ManagedSyncResult
import com.tequila.tumcalendarcontrol.sync.ManualScheduleEditor
import com.tequila.tumcalendarcontrol.sync.ManualScheduleItem
import com.tequila.tumcalendarcontrol.sync.OutputTarget
import com.tequila.tumcalendarcontrol.sync.DesiredCalendarEvent
import com.tequila.tumcalendarcontrol.sync.SyncRunSummary
import com.tequila.tumcalendarcontrol.sync.StoredEventMapping
import com.tequila.tumcalendarcontrol.sync.ManagedEventCleanupScope
import com.tequila.tumcalendarcontrol.sync.ManagedEventIdentity
import com.tequila.tumcalendarcontrol.sync.RuleLedgerBackup
import com.tequila.tumcalendarcontrol.sync.RuleLedgerBackupCodec
import com.tequila.tumcalendarcontrol.sync.GroupAttendanceRuleEditor
import com.tequila.tumcalendarcontrol.sync.GroupAttendanceRuleResolver
import com.tequila.tumcalendarcontrol.sync.GoogleConnectionCheckResult
import com.tequila.tumcalendarcontrol.sync.SourceCheckSettings
import com.tequila.tumcalendarcontrol.sync.SourceCheckStatus
import com.tequila.tumcalendarcontrol.sync.SourceCheckCadence
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import java.time.LocalDate

enum class AppScreen {
    OVERVIEW, DETAIL, ATTENDANCE, COURSE_EDIT, SEARCH, FAVORITES, SETTINGS, PROFILE, SYNC_CONFIGURATION, SYNC_PREVIEW, SOURCE_RANGE, SOURCE_PREVIEW, SOURCE_ATTENDANCE, MANUAL_EVENTS, MANAGED_SYNC_PREVIEW, OUTPUT_CLEANUP, BACKGROUND_CHECK, RULE_BACKUP, APPEARANCE, SOURCE_CHANGES,
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

class CalendarControlState(
    initialCourses: List<Course> = SampleCourses.all(),
    initialProfile: UserProfile = UserProfile(),
    initialSyncDestinations: SyncDestinations = SyncDestinations(),
    initialPrivateSyncConfiguration: PrivateSyncConfiguration = PrivateSyncConfiguration(),
    initialRuleLedger: RuleLedger = RuleLedger(),
    initialSourceCheckSettings: SourceCheckSettings = SourceCheckSettings(),
    initialSourceCheckStatus: SourceCheckStatus = SourceCheckStatus(),
    initialCoursePresentations: Map<String, CoursePresentation> = emptyMap(),
    initialSourceSnapshots: SavedSourceSnapshots = SavedSourceSnapshots(),
    initialAppearance: AppearanceSettings = AppearanceSettings(),
    private val userDataPersistence: UserDataPersistence = UserDataPersistence.InMemory,
) {
    /** Nur lokale Anzeigemetadaten pro UID speichern; keine Ersatzidentität für Quelleninstanzen. */
    private val savedCoursePresentations = initialCoursePresentations.toMutableMap()
    private val sourceWorkflow = SourceReadWorkflowState(initialSourceSnapshots)
    /** Momentaufnahme nur für Browser und ausdrücklich gewählte Vorschau wiederherstellen; keine automatische Quelle oder Kalenderausgabe. */
    var courses by mutableStateOf(initialCourses)
        private set
    init {
        if (sourceWorkflow.catalogLoaded) {
            courses = SourceCourseProjection.courses(
                sourceCourses = sourceWorkflow.courses,
                occurrences = sourceWorkflow.occurrences,
                rules = GroupAttendanceRuleResolver.materialize(initialRuleLedger, sourceWorkflow.occurrences),
                previousCourses = emptyList(),
                savedPresentations = savedCoursePresentations,
            )
        }
    }
    private val navigation = NavigationState()
    val screen: AppScreen get() = navigation.screen
    var selectedCourseId by mutableStateOf(courses.firstOrNull()?.id.orEmpty())
        private set
    private var selectedInstanceKey by mutableStateOf<String?>(null)
    var searchQuery by mutableStateOf("")
    var profile by mutableStateOf(initialProfile)
        private set
    var pendingAttendance by mutableStateOf(initialCourses.firstOrNull()?.attendance ?: AttendanceState.CHOOSE_TO_ATTEND)
    var pendingAttendanceScope by mutableStateOf(AttendanceEditScope.ALL_OCCURRENCES)
    var pendingEffectiveDate by mutableStateOf("")
    var visibleSourceStatuses by mutableStateOf(TumEventStatus.entries.toSet())
    var localCalendarPermissionRequestPending by mutableStateOf(false)
        private set
    var feedback by mutableStateOf<String?>(null)
    var themeMode by mutableStateOf(initialAppearance.mode)
    var courseTypeNames by mutableStateOf<Map<String, String>>(emptyMap())
    var individualAttendanceMode by mutableStateOf(false)
    var individualAttendanceDrafts by mutableStateOf<Map<String, IndividualAttendanceDraft>>(emptyMap())
    var pageMemory by mutableStateOf(PageMemory())
    private var openedIndividualDrafts: Map<String, IndividualAttendanceDraft> = emptyMap()
    private var sourceEditorOpened = false
    private var sourceEditorChanged = false
    private var sourceAttendanceChanged = false
    private var sourceStateChosen = false
    private var detailAttendanceBaseline: Triple<AttendanceState, AttendanceEditScope, String>? = null
    fun restorePageMemory(memory: PageMemory) {
        pageMemory = memory
        searchQuery = memory.search
        sourceWorkflow.browserQuery = memory.sourceSearch
        sourceWorkflow.browserFilter = runCatching { SourceCourseFilter.valueOf(memory.sourceFilter) }.getOrDefault(SourceCourseFilter.ALL)
        visibleSourceStatuses = memory.visibleSourceStatuses.mapNotNullTo(mutableSetOf()) { runCatching { TumEventStatus.valueOf(it) }.getOrNull() }
            .ifEmpty { TumEventStatus.entries.toMutableSet() }
        sourceWorkflow.includeInSelection(memory.selection.intersect(currentSourceOccurrences.map { it.identity }.toSet()))
    }
    val persistedPageMemory: PageMemory get() = pageMemory.copy(
        selection = selectedSourceOccurrences,
        search = searchQuery,
        sourceSearch = sourceBrowserQuery,
        sourceFilter = sourceBrowserFilter.name,
        visibleSourceStatuses = visibleSourceStatuses.map(TumEventStatus::name).toSet(),
    )
    fun currentSelectionSummary(selection: Set<SourceOccurrenceIdentity>): String {
        val items = courses.filter { SourceOccurrenceIdentity(it.id, it.instanceKey.orEmpty()) in selection }
        val states = items.map { it.attendance }.distinct()
        val favorites = items.map { it.isFavorite }.distinct()
        return message(239, (states.singleOrNull()?.label ?: message(240)), (favorites.singleOrNull()?.let { if (it) message(241) else message(242) } ?: message(243)))
    }
    private fun rememberAttendanceOptions() {
        pageMemory = pageMemory.copy(individual = individualAttendanceMode, scope = pendingSourceAttendanceScope, date = pendingSourceEffectiveDate)
    }

    /** Benutzerdaten vor beobachtbarem Zustand synchron speichern; keine halben Regeln/Favoriten bei Fehler. */
    private fun commitUserData(nextLedger: RuleLedger = ruleLedger, nextCourses: List<Course> = courses): Boolean {
        val committedLedger = userDataPersistence.commit(nextLedger, nextCourses)
        if (committedLedger == null) {
            feedback = message(244)
            return false
        }
        ruleLedger = committedLedger
        courses = nextCourses
        rememberPresentations()
        return true
    }

    private fun projectedCourses(ledger: RuleLedger, previous: List<Course> = courses): List<Course> =
        if (!sourceCatalogLoaded) previous else SourceCourseProjection.courses(
            sourceCourses = sourceCourses,
            occurrences = currentSourceOccurrences,
            rules = GroupAttendanceRuleResolver.materialize(ledger, currentSourceOccurrences),
            previousCourses = previous,
            savedPresentations = savedCoursePresentations,
        )
    val individualAttendanceGroups: List<IndividualAttendanceGroup> get() = sourceWorkflow.occurrences
        .filter { it.identity in selectedSourceOccurrences }
        .groupBy { CourseDisplayGroupModel.titleGroupingKey(it.title) }
        .map { (key, items) -> IndividualAttendanceGroup(key, items.first().title, items.map { it.identity }.toSet()) }
    var lightPaletteStyle by mutableStateOf(initialAppearance.light)
    var darkPaletteStyle by mutableStateOf(initialAppearance.dark)
    val appearanceSettings: AppearanceSettings get() = AppearanceSettings(themeMode, lightPaletteStyle, darkPaletteStyle)
    var syncDestinations by mutableStateOf(initialSyncDestinations)
        private set
    var pendingSyncDestinationChange by mutableStateOf<PendingSyncDestinationChange?>(null)
        private set
    var privateSyncConfiguration by mutableStateOf(initialPrivateSyncConfiguration)
        private set
    var ruleLedger by mutableStateOf(initialRuleLedger)
        private set
    var sourceCheckSettings by mutableStateOf(initialSourceCheckSettings)
        private set
    var sourceCheckStatus by mutableStateOf(initialSourceCheckStatus)
        private set
    var sourceCheckSchedulingRequested by mutableStateOf(false)
        private set
    var notificationPermissionRequestPending by mutableStateOf(false)
        private set
    var ruleBackupExportRequested by mutableStateOf(false)
        private set
    var ruleBackupImportRequested by mutableStateOf(false)
        private set
    var pendingRuleBackup by mutableStateOf<RuleLedgerBackup?>(null)
        private set
    val offlinePreview: SyncPreview = OfflinePreviewSamples.preview()
    private val outputWorkflow = ManagedOutputWorkflowState()
    val sourceReadRequested: Boolean get() = sourceWorkflow.readRequested
    val sourceReadInProgress: Boolean get() = sourceWorkflow.readInProgress
    val sourcePreview: IcalReadOnlyPreview? get() = sourceWorkflow.preview
    val sourcePreviewError: String? get() = sourceWorkflow.previewError
    /** Katalog aus aktiver Momentaufnahme: aktuelle oder ausdrücklich gewählte frühere erfolgreiche Lesung. */
    val sourceCourses: List<SourceCourse> get() = sourceWorkflow.courses
    /** Nach erfolgreicher Lesung/Wiederherstellung Quellenprojektion anzeigen, nicht synthetische Beispiele. */
    val sourceCatalogLoaded: Boolean get() = sourceWorkflow.catalogLoaded
    /** Aktive Termine erst nach ausdrücklicher Vorschau als Kandidaten; Identität immer UID plus Originalinstanz. */
    val currentSourceOccurrences: List<SourceCalendarOccurrence> get() = sourceWorkflow.occurrences
    /** Nur atomare private Momentaufnahmeablage am App-Einstieg; keine Adressen, Token oder Zielkalenderdaten. */
    val savedSourceSnapshots: SavedSourceSnapshots get() = sourceWorkflow.savedSnapshots
    val activeSourceSnapshotSlot: SourceSnapshotSlot get() = sourceWorkflow.activeSnapshotSlot
    val hasPreviousSourceSnapshot: Boolean get() = sourceWorkflow.hasPreviousSnapshot
    val sourceSnapshotDifference: SourceSnapshotDifference? get() = sourceWorkflow.activeSnapshotDifference
    /** Ausführliche Änderungen neueste gegenüber vorheriger Lesung (Unterschiedsseite, unabhängig von aktiver Version). */
    val sourceVersionChanges: SourceChangeSummary get() = sourceWorkflow.latestChanges
    /** Änderungskennzeichen in der Auswahl nur, solange die neueste Version aktiv ist. */
    val sourceChanges: SourceChangeSummary get() = if (activeSourceSnapshotSlot == SourceSnapshotSlot.LATEST) sourceWorkflow.latestChanges else SourceChangeSummary.EMPTY
    val sourceSnapshotLabel: String get() = when (activeSourceSnapshotSlot) {
        SourceSnapshotSlot.LATEST -> if (sourceWorkflow.activeSnapshotWasReadInThisRun) message(245) else message(246)
        SourceSnapshotSlot.PREVIOUS -> message(247)
    }
    val sourceSnapshotSavedAt: String get() = savedSourceSnapshots.active?.capturedAt
        ?.atZone(ZoneId.systemDefault())?.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) ?: message(248)
    /** Konkrete Termine auswählen; Regeln/Favoriten aggregieren Quell-UIDs nur bei Bedarf. */
    val selectedSourceOccurrences: Set<SourceOccurrenceIdentity> get() = sourceWorkflow.selectedOccurrences
    var sourceBrowserQuery: String
        get() = sourceWorkflow.browserQuery
        set(value) { sourceWorkflow.browserQuery = value }
    var sourceBrowserFilter: SourceCourseFilter
        get() = sourceWorkflow.browserFilter
        set(value) { sourceWorkflow.browserFilter = value }
    val sourceReloadConfirmationPending: Boolean get() = sourceWorkflow.reloadConfirmationPending
    var pendingSourceAttendance: AttendanceState
        get() = sourceWorkflow.pendingAttendance
        set(value) { sourceWorkflow.pendingAttendance = value; sourceEditorChanged = true; sourceAttendanceChanged = true; sourceStateChosen = true }
    var pendingSourceAttendanceScope: AttendanceEditScope
        get() = sourceWorkflow.pendingAttendanceScope
        set(value) { sourceWorkflow.pendingAttendanceScope = value; sourceEditorChanged = true }
    var pendingSourceEffectiveDate: String
        get() = sourceWorkflow.pendingEffectiveDate
        set(value) { sourceWorkflow.pendingEffectiveDate = value; sourceEditorChanged = true }
    var pendingSourceFavoriteAction: SourceFavoriteAction
        get() = sourceWorkflow.pendingFavoriteAction
        set(value) { sourceWorkflow.pendingFavoriteAction = value; sourceEditorChanged = true }
    var managedSyncPreview: ManagedSyncPreview?
        get() = outputWorkflow.preview
        private set(value) { outputWorkflow.preview = value }
    private var managedDesiredEvents: List<DesiredCalendarEvent>
        get() = outputWorkflow.desiredEvents
        set(value) { outputWorkflow.desiredEvents = value }
    /** Bereinigungsbereich nur kurz vor/nach Bestätigung im Speicher; niemals im Regelbestand speichern. */
    private var managedRollbackScope by mutableStateOf<ManagedEventCleanupScope>(ManagedEventCleanupScope.AllManagedEvents)
    private var managedRollbackTargets by mutableStateOf<Set<OutputTarget>>(emptySet())
    private var confirmedWriteTargets: Set<OutputTarget> = emptySet()
    private var previewChangedDuringWrite = false
    var cleanupCatalogReadTargets by mutableStateOf<Set<OutputTarget>>(emptySet())
        private set
    var cleanupCatalogReadToken by mutableStateOf(0L)
        private set

    fun requestCleanupCatalogRead(targets: Set<OutputTarget>) {
        if (managedSyncInProgress || cleanupCatalogReadTargets.isNotEmpty()) return
        val requested = targets.intersect(availableManagedOutputTargets())
        if (requested.isEmpty()) { feedback = message(249); return }
        if (OutputTarget.GOOGLE_CALENDAR in requested && !hasGoogleAuthorization) { feedback = message(250); return }
        if (OutputTarget.LOCAL_CALENDAR in requested && !localCalendarPermissionGranted) { feedback = message(251); return }
        cleanupCatalogReadTargets = requested
        cleanupCatalogReadToken += 1
        feedback = message(252)
    }

    fun resolveCleanupCatalogRead(token: Long, result: com.tequila.tumcalendarcontrol.sync.CleanupCatalogReadResult?) {
        if (token != cleanupCatalogReadToken) return
        cleanupCatalogReadTargets = emptySet()
        if (result == null) { feedback = message(253); return }
        ruleLedger = ruleLedger.copy(mappings = result.mappings)
        feedback = message(254, (result.checkedCount), (result.failedCount))
    }
    /** Vorschau zählt nur generierte Identitätspräfixe; keine Quelltitel oder Inhalte offenlegen. */
    val managedSourceCandidateCount: Int get() = managedDesiredEvents.count { !it.identity.sourceUid.startsWith("manual:") }
    val managedManualCandidateCount: Int get() = managedDesiredEvents.count { it.identity.sourceUid.startsWith("manual:") }
    /** Explizit abgesagte zuvor geschriebene Termine; normale Ausgabe behandelt Abwahl nicht als Löschung. */
    val managedCancelledMappedCount: Int get() {
        val cancelled = currentSourceOccurrences.filter(SourceCalendarOccurrence::isCancelled)
            .map { ManagedEventIdentity(it.sourceUid, it.instanceKey) }
            .toSet()
        val targets = managedSyncPreview?.targetChanges?.keys ?: enabledOutputTargets()
        return ruleLedger.mappings.filter { it.identity in cancelled && it.target in targets }
            .distinctBy { it.identity to it.target }
            .size
    }
    val managedSyncRequested: Boolean get() = outputWorkflow.syncRequested
    val managedRollbackRequested: Boolean get() = outputWorkflow.rollbackRequested
    val managedSyncInProgress: Boolean get() = outputWorkflow.inProgress
    /** Fortschritt/Anhalten der laufenden Ausgabe (nur Schreiben, nicht Bereinigung). */
    internal val writeProgress = WriteProgressState()
    val googleAuthorizationRequested: Boolean get() = outputWorkflow.googleAuthorizationRequested
    val hasGoogleAuthorization: Boolean get() = outputWorkflow.hasGoogleAuthorization
    val googleAuthorizationStatus: GoogleAuthorizationStatus get() = outputWorkflow.googleAuthorizationStatus
    val googleAuthorizationDetail: String get() = outputWorkflow.googleAuthorizationDetail
    val googleConnectionCheckRequested: Boolean get() = outputWorkflow.googleConnectionCheckRequested
    val googleConnectionCheckInProgress: Boolean get() = outputWorkflow.googleConnectionCheckInProgress
    val googleConnectionCheckDetail: String get() = outputWorkflow.googleConnectionCheckDetail
    val googleConnectionVerification: GoogleConnectionVerification get() = outputWorkflow.googleConnectionVerification
    val googleConnectionCheckToken: Long get() = outputWorkflow.googleConnectionCheckToken
    val localCalendarPermissionGranted: Boolean get() = outputWorkflow.localCalendarPermissionGranted
    /** Monotone Leseanfragenummer verwirft alte Ergebnisse nach Timeout; neue Nutzeraktionen bleiben geschützt. */
    val sourceReadToken: Long get() = sourceWorkflow.readToken
    val navigationDirection: NavigationDirection get() = navigation.direction

    /** Hauptseite unter Aktionsüberlagerung stabil halten; keine gemeinsame Haupttab-Überblendung. */
    val contentScreen: AppScreen
        get() = navigation.contentScreen

    val selectedCourse: Course
        get() = courses.firstOrNull { it.id == selectedCourseId && it.instanceKey == selectedInstanceKey }
            ?: courses.first { it.id == selectedCourseId }

    /** Kein verlässliches TUM-Statusfeld in echter Quelle; gelesenen Katalog vollständig anzeigen. */
    val displayedCourses: List<Course>
        get() = if (sourceCatalogLoaded) courses else courses.filter { it.hasFixedPlus && it.sourceStatus in visibleSourceStatuses }

    val searchedCourses: List<Course>
        get() = displayedCourses.filter { it.displayTitle.contains(searchQuery, ignoreCase = true) }

    val favoriteCourses: List<Course>
        get() = courses.filter { it.isFavorite && it.hasFixedPlus }

    /** Haupttabs animieren nach relativer Position; hierarchische Details weiterhin vorwärts. */
    fun open(screen: AppScreen) {
        navigation.open(screen)
    }

    fun openCourse(courseId: String) {
        val course = courses.firstOrNull { it.browseKey == courseId || it.id == courseId } ?: return
        selectedCourseId = course.id
        selectedInstanceKey = course.instanceKey
        pendingAttendance = selectedCourse.attendance
        navigation.openDetail()
    }

    fun openAttendance() {
        pendingAttendance = selectedCourse.attendance
        pendingAttendanceScope = pageMemory.scope
        pendingEffectiveDate = pageMemory.date
        detailAttendanceBaseline = Triple(pendingAttendance, pendingAttendanceScope, pendingEffectiveDate)
        feedback = null
        open(AppScreen.ATTENDANCE)
    }

    fun openCourseEdit() = navigation.openCourseEdit()

    /** Nur private App-Änderung; Quell-title/schedule/location unverändert. */
    fun updateSelectedCourseOverride(override: CourseLocalOverride) {
        val nextCourses = courses.map { if (it.id == selectedCourseId) it.copy(localOverride = override) else it }
        if (!commitUserData(nextCourses = nextCourses)) return
        invalidateWritePreview()
        feedback = message(255)
    }

    fun toggleSourceStatusVisible(status: TumEventStatus) {
        visibleSourceStatuses = if (status in visibleSourceStatuses) visibleSourceStatuses - status else visibleSourceStatuses + status
    }

    fun openProfile() = navigation.openProfile()

    /** Validiertes lokales Profil; kein externes Konto. */
    fun updateProfile(updatedProfile: UserProfile) {
        profile = updatedProfile.copy(displayName = updatedProfile.displayName.trim().ifBlank { UserProfile().displayName })
    }

    /** Nur künftige Ausgabeerlaubnis ändern; Kalenderverbindung und Schreiben im nachfolgenden autorisierten Modul. */
    fun setSyncDestinationEnabled(destination: SyncDestination, enabled: Boolean) {
        invalidateWritePreview()
        syncDestinations = syncDestinations.withEnabled(destination, enabled)
        if (destination == SyncDestination.GOOGLE_CALENDAR && !enabled) {
            outputWorkflow.disconnectGoogle()
        }
    }

    /** Schalter zunächst im Bestätigungsdialog; vorher keine Berechtigung, Tokenlöschung oder Präferenzänderung. */
    fun requestSyncDestinationChange(destination: SyncDestination, enabled: Boolean) {
        if (syncDestinations.isEnabled(destination) == enabled) return
        pendingSyncDestinationChange = PendingSyncDestinationChange(destination, enabled)
    }

    fun cancelSyncDestinationChange() { pendingSyncDestinationChange = null }

    fun confirmSyncDestinationChange() {
        val change = pendingSyncDestinationChange ?: return
        pendingSyncDestinationChange = null
        if (change.destination == SyncDestination.LOCAL_CALENDAR && change.enabled) {
            requestLocalCalendarOutput()
        } else {
            setSyncDestinationEnabled(change.destination, change.enabled)
            feedback = if (change.enabled) message(256, (change.destination.title)) else message(257, (change.destination.title))
        }
    }

    /** Berechtigung ist keine Ausgabe; erst nach Systemzustimmung künftige lokale Ausgabeerlaubnis speichern. */
    private var permissionForCurrentWriteOnly = false
    fun requestLocalCalendarOutput() { permissionForCurrentWriteOnly = false; localCalendarPermissionRequestPending = true }

    fun requestCurrentWriteLocalPermission() { permissionForCurrentWriteOnly = true; localCalendarPermissionRequestPending = true }

    fun resolveLocalCalendarPermission(granted: Boolean) {
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
    fun refreshLocalCalendarPermission(granted: Boolean) { outputWorkflow.localCalendarPermissionGranted = granted }

    /** Formatprobleme vor Speichern anzeigen; Zustandsmaschine verbindet oder schreibt keine Kalender. */
    fun updatePrivateSyncConfiguration(configuration: PrivateSyncConfiguration) {
        require(configuration.validate().isEmpty()) { message(260) }
        privateSyncConfiguration = configuration
        invalidateWritePreview()
    }

    fun openSyncConfiguration() = navigation.openSyncConfiguration()

    /** Zeitraum unabhängig vom Kontoformular; Öffnen/Speichern löst keine Netzlesung aus. */
    fun openSourceRange() = navigation.openSourceRange()

    /** Hintergrundprüfung nur mit konfigurierter Quelle; Einplanung liest/schreibt keinen Kalender. */
    fun updateSourceCheck(enabled: Boolean, cadence: SourceCheckCadence = sourceCheckSettings.cadence) {
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

    fun consumeSourceCheckSchedulingRequest() { sourceCheckSchedulingRequested = false }
    fun consumeNotificationPermissionRequest() { notificationPermissionRequestPending = false }
    fun resolveNotificationPermission(granted: Boolean) {
        notificationPermissionRequestPending = false
        if (!granted && sourceCheckSettings.enabled) feedback = message(264)
    }
    fun refreshSourceCheckStatus(status: SourceCheckStatus) { sourceCheckStatus = status }

    fun openBackgroundCheck() = navigation.openBackgroundCheck()

    fun openRuleBackup() = navigation.openRuleBackup()
    fun openSourceChanges() = navigation.openSourceChanges()

    /** SAF-Ausgabe erst nach Dateiwahl; hier nur Oberflächenabsicht. */
    fun requestRuleBackupExport() { ruleBackupExportRequested = true }
    fun consumeRuleBackupExportRequest() { ruleBackupExportRequested = false }
    fun resolveRuleBackupExport(saved: Boolean) { feedback = if (saved) message(265) else message(266) }

    /** SAF-Inhalt nur als zu bestätigender Entwurf im Speicher; Regelbestand vor Bestätigung unverändert. */
    fun requestRuleBackupImport() { ruleBackupImportRequested = true }
    fun consumeRuleBackupImportRequest() { ruleBackupImportRequested = false }
    fun resolveRuleBackupImport(text: String?) {
        pendingRuleBackup = text?.let(RuleLedgerBackupCodec::decode)
        feedback = if (pendingRuleBackup == null) message(267) else message(268)
    }
    fun cancelRuleBackupImport() { pendingRuleBackup = null }
    fun confirmRuleBackupImport() {
        val backup = pendingRuleBackup ?: return
        val nextLedger = ruleLedger.copy(rules = backup.rules, manualEvents = backup.manualEvents, groupRules = backup.groupRules)
        val nextCourses = projectedCourses(nextLedger)
        if (!commitUserData(nextLedger, nextCourses)) return
        invalidateWritePreview()
        pendingRuleBackup = null
        feedback = message(269)
    }

    /** Nächsten Lesezeitraum speichern; aktuelle Daten, Auswahl und Kandidaten unverändert. */
    fun saveSourceOutputRange(startDate: String, endDate: String): List<String> {
        val updated = privateSyncConfiguration.copy(sourceRangeStartDate = startDate, sourceRangeEndDate = endDate)
        val problems = updated.validate()
        if (problems.isEmpty()) {
            privateSyncConfiguration = updated
            feedback = message(270)
        }
        return problems.map { it.message }
    }

    /** Erneute Lesung ersetzt Laufzeitkatalog; bei vorhandener Auswahl ausdrückliche Bestätigung nötig. */
    fun requestSourceReadForCurrentRange() {
        when (val request = sourceWorkflow.requestRead(privateSyncConfiguration.hasTumSubscription)) {
            is SourceReadRequest.Rejected -> feedback = request.message
            SourceReadRequest.Ignored, SourceReadRequest.NeedsReloadConfirmation, is SourceReadRequest.Requested -> Unit
        }
    }

    /** Erst nach erfolgreicher Datumsprüfung lesen; sichtbarer und tatsächlicher Bereich identisch. */
    fun requestSourceReadForRange(start: String, end: String): List<String> {
        if (sourceReadInProgress) return listOf(message(271))
        val errors = saveSourceOutputRange(start, end)
        if (errors.isEmpty()) requestSourceReadForCurrentRange()
        return errors
    }

    val sourceAttendanceAffectedCount: Int? get() = sourceWorkflow.attendanceScopeCount()

    fun resolveSourceReloadConfirmation(accepted: Boolean) {
        when (val request = sourceWorkflow.resolveReloadConfirmation(accepted, privateSyncConfiguration.hasTumSubscription)) {
            is SourceReadRequest.Rejected -> feedback = request.message
            SourceReadRequest.Ignored, SourceReadRequest.NeedsReloadConfirmation, is SourceReadRequest.Requested -> Unit
        }
    }

    /** Vorhandenen Katalog direkt öffnen; ohne Katalog nur Hinweis, kein verstecktes Lesen. */
    fun continueWithSavedCourses(): List<String> {
        if (sourceReadInProgress) return listOf(message(271))
        if (sourcePreview == null) return listOf(message(272))
        resolveSourceReloadConfirmation(false)
        openSourceCourseSelector()
        feedback = message(273, (sourceSnapshotLabel))
        return emptyList()
    }

    /** Vorhandenen Katalog öffnen; keine Lesung oder Auswahlentfernung. */
    fun openSourceCourseSelector() {
        if (sourceWorkflow.preview == null) {
            feedback = message(274)
            return
        }
        navigation.openExistingSourcePreview()
    }

    /** Synthetische Vorschau ohne Netzwerk, Berechtigungen oder Kalenderausgabe. */
    fun openSyncPreview() = navigation.openSyncPreview()

    /** Leseanfrage nur als Absicht; Oberfläche führt einen lesenden HTTPS-Abruf im Hintergrund aus. Ohne Konfiguration keine Anfrage. */
    fun requestSourcePreview() {
        when (val request = sourceWorkflow.requestDirectRead(privateSyncConfiguration.hasTumSubscription)) {
            is SourceReadRequest.Rejected -> feedback = request.message
            is SourceReadRequest.Requested -> feedback = message(275)
            SourceReadRequest.Ignored, SourceReadRequest.NeedsReloadConfirmation -> Unit
        }
    }

    /** Nach Lesung Zusammenfassung und Laufzeitkatalog behalten; keine URL oder Original-ICS. */
    fun resolveSourcePreview(token: Long, result: IcalReadOnlyImportResult) {
        when (val resolution = sourceWorkflow.resolveRead(token, result)) {
            is SourceReadResolution.Success -> {
                invalidateWritePreview()
                refreshCoursesFromSource()
                selectedCourseId = courses.firstOrNull()?.id ?: ""
                navigation.openSourcePreview()
                feedback = if (sourceWorkflow.lastSelectionRemovedCount > 0) {
                    message(276, (sourceWorkflow.lastSelectionRemovedCount))
                } else {
                    message(277)
                }
            }
            is SourceReadResolution.Failure -> feedback = message(278, (resolution.message))
            SourceReadResolution.Ignored -> Unit
        }
    }

    /** Momentaufnahmewechsel projiziert nur lokalen Katalog; kein Netzwerk, Regeländerung oder Kalenderzugriff. */
    fun selectSourceSnapshot(slot: SourceSnapshotSlot) {
        if (managedSyncInProgress || sourceReadInProgress) { feedback = message(279); return }
        if (!sourceWorkflow.selectSnapshot(slot)) {
            feedback = message(280)
            return
        }
        refreshCoursesFromSource()
        invalidateWritePreview()
        selectedCourseId = courses.firstOrNull()?.id.orEmpty()
        feedback = message(281, (sourceSnapshotLabel), (if (sourceWorkflow.lastSelectionRemovedCount > 0) message(282, (sourceWorkflow.lastSelectionRemovedCount)) else ""))
    }

    /** Mehrfachauswahl bewahrt Terminidentität; Titel, Orte und Inhalte nicht im Regelbestand. */
    fun toggleSourceOccurrenceSelection(identity: SourceOccurrenceIdentity) {
        invalidateWritePreview()
        sourceWorkflow.toggleSelection(identity)
    }

    fun selectAllSourceOccurrences() { invalidateWritePreview(); sourceWorkflow.selectAllOccurrences() }

    /** Nur hinzufügen (z. B. ganzer aktueller Kurs); bestehende Auswahl bleibt, wiederholter Aufruf ändert nichts. */
    fun includeSourceOccurrences(identities: Set<SourceOccurrenceIdentity>) { invalidateWritePreview(); sourceWorkflow.includeInSelection(identities) }
    fun excludeSourceOccurrences(identities: Set<SourceOccurrenceIdentity>) { invalidateWritePreview(); sourceWorkflow.excludeFromSelection(identities) }

    fun clearSourceCourseSelection() { invalidateWritePreview(); sourceWorkflow.clearSelection() }

    fun updateSourceBrowserQuery(query: String) { sourceBrowserQuery = query }

    fun updateSourceBrowserFilter(filter: SourceCourseFilter) { sourceBrowserFilter = filter }

    /** Neue Termine ohne Regel standardmäßig blau; gleicher Dreizustandseditor für Einzel-/Mehrfachwahl. */
    fun sourceAttendanceFor(sourceUid: String): AttendanceState = GroupAttendanceRuleResolver
        .materialize(ruleLedger, currentSourceOccurrences)
        .firstOrNull { it.sourceUid == sourceUid }
        ?.defaultState
        ?: AttendanceState.CHOOSE_TO_ATTEND

    fun openSourceAttendanceEditor(selection: Set<SourceOccurrenceIdentity> = selectedSourceOccurrences) {
        if (selection.isEmpty()) {
            feedback = message(283)
            return
        }
        val firstState = courses.firstOrNull { it.id == selection.first().sourceUid && it.instanceKey == selection.first().instanceKey }?.attendance
            ?: sourceAttendanceFor(selection.first().sourceUid)
        sourceWorkflow.prepareAttendanceEditor(selection, firstState)
        individualAttendanceMode = pageMemory.individual
        sourceWorkflow.pendingAttendanceScope = pageMemory.scope
        sourceWorkflow.pendingEffectiveDate = pageMemory.date
        val selectedCourses = courses.filter { SourceOccurrenceIdentity(it.id, it.instanceKey.orEmpty()) in selection }
        sourceWorkflow.pendingFavoriteAction = selectedCourses.map { it.isFavorite }.distinct().singleOrNull()
            ?.let { if (it) SourceFavoriteAction.ADD else SourceFavoriteAction.REMOVE } ?: SourceFavoriteAction.KEEP
        individualAttendanceDrafts = individualAttendanceGroups.associate { group ->
            val members = courses.filter { SourceOccurrenceIdentity(it.id, it.instanceKey.orEmpty()) in group.selection }
            group.key to IndividualAttendanceDraft(members.map { it.attendance }.distinct().singleOrNull(),
                members.map { it.isFavorite }.distinct().singleOrNull()?.let { if (it) SourceFavoriteAction.ADD else SourceFavoriteAction.REMOVE } ?: SourceFavoriteAction.KEEP)
        }
        openedIndividualDrafts = individualAttendanceDrafts
        sourceEditorOpened = true
        sourceEditorChanged = false
        sourceAttendanceChanged = false
        sourceStateChosen = false
        navigation.openSourceAttendance()
    }

    /** Oberflächenbereich in UID-Regeln umwandeln; Einzelterminregel verwendet Originalinstanz. */
    fun confirmSourceAttendance() {
        if (sourceEditorOpened && !sourceEditorChanged && (!individualAttendanceMode || individualAttendanceDrafts == openedIndividualDrafts)) {
            rememberAttendanceOptions()
            feedback = message(284)
            navigation.handleBack()
            return
        }
        if (individualAttendanceMode) { confirmIndividualSourceAttendance(); return }
        if (sourceAttendanceChanged && !sourceStateChosen && openedIndividualDrafts.values.map { it.attendance }.let { it.contains(null) || it.distinct().size > 1 }) {
            feedback = message(285)
            return
        }
        val result = sourceWorkflow.applyAttendanceRules(ruleLedger, attendance = if (!sourceEditorOpened || sourceAttendanceChanged) pendingSourceAttendance else null)
        if (result is SourceAttendanceRuleResult.Invalid) {
            feedback = result.message
            if (result.message.startsWith(message(286))) navigation.handleBack()
            return
        }
        result as SourceAttendanceRuleResult.Applied
        var nextCourses = projectedCourses(result.rules)
        if (result.favoriteAction != SourceFavoriteAction.KEEP) {
            nextCourses = nextCourses.map { course ->
                if (course.id in result.sourceUids) course.copy(isFavorite = result.favoriteAction == SourceFavoriteAction.ADD) else course
            }
        }
        if (!commitUserData(result.rules, nextCourses)) return
        sourceWorkflow.includeInSelection(result.affectedOccurrences)
        invalidateWritePreview()
        rememberAttendanceOptions()
        feedback = message(287, (selectedSourceOccurrences.size), (if (result.favoriteAction == SourceFavoriteAction.KEEP) "" else message(288)))
        navigation.handleBack()
    }

    /** Erst nach vollständiger Prüfung atomar speichern; kein Teilzustand bei späterem Datumsfehler. */
    private fun confirmIndividualSourceAttendance() {
        val groups = individualAttendanceGroups
        if (groups.isEmpty()) { feedback = message(289); return }
        var nextLedger = ruleLedger
        val results = mutableListOf<SourceAttendanceRuleResult.Applied>()
        for (group in groups) {
            val draft = individualAttendanceDrafts[group.key] ?: IndividualAttendanceDraft()
            if (!sourceEditorChanged && draft == openedIndividualDrafts[group.key]) continue
            val before = openedIndividualDrafts[group.key]
            val result = sourceWorkflow.applyAttendanceRules(nextLedger, group.selection,
                if (sourceAttendanceChanged || draft.attendance != before?.attendance) draft.attendance else null,
                if (draft.favorite != before?.favorite) draft.favorite else SourceFavoriteAction.KEEP)
            if (result is SourceAttendanceRuleResult.Invalid) { feedback = result.message; return }
            result as SourceAttendanceRuleResult.Applied
            nextLedger = result.rules
            results += result
        }
        var nextCourses = projectedCourses(nextLedger)
        results.filter { it.favoriteAction != SourceFavoriteAction.KEEP }.forEach { result ->
            nextCourses = nextCourses.map { course -> if (course.id in result.sourceUids) course.copy(isFavorite = result.favoriteAction == SourceFavoriteAction.ADD) else course }
        }
        if (!commitUserData(nextLedger, nextCourses)) return
        sourceWorkflow.includeInSelection(results.flatMap { it.affectedOccurrences }.toSet())
        invalidateWritePreview()
        rememberAttendanceOptions()
        feedback = message(290, (groups.size))
        navigation.handleBack()
    }

    fun confirmAttendance() {
        if (detailAttendanceBaseline == Triple(pendingAttendance, pendingAttendanceScope, pendingEffectiveDate)) {
            feedback = message(291)
            open(AppScreen.DETAIL)
            return
        }
        if (sourceCatalogLoaded && selectedCourse.instanceKey != null) {
            val result = sourceWorkflow.applyAttendanceRules(
                ruleLedger, setOf(SourceOccurrenceIdentity(selectedCourseId, selectedCourse.instanceKey!!)),
                pendingAttendance, SourceFavoriteAction.KEEP, pendingAttendanceScope, pendingEffectiveDate,
            )
            if (result is SourceAttendanceRuleResult.Invalid) { feedback = result.message; return }
            result as SourceAttendanceRuleResult.Applied
            val nextCourses = projectedCourses(result.rules)
            if (!commitUserData(result.rules, nextCourses)) return
            pageMemory = pageMemory.copy(scope = pendingAttendanceScope, date = pendingEffectiveDate)
            sourceWorkflow.includeInSelection(result.affectedOccurrences)
            invalidateWritePreview()
            feedback = message(292, (result.affectedOccurrences.size))
            open(AppScreen.DETAIL)
            return
        }
        val command = attendanceCommand() ?: return
        val selectedRule = ruleLedger.rules.firstOrNull { it.sourceUid == selectedCourseId }
            ?: SyncRule(selectedCourseId, hasConfirmedFixplatz = selectedCourse.hasFixedPlus, defaultState = selectedCourse.attendance)
        val nextLedger = ruleLedger.copy(rules = RuleLedgerEditor.apply(
            ruleLedger.rules.filterNot { it.sourceUid == selectedCourseId } + selectedRule,
            command,
        ))
        val nextCourses = if (sourceCatalogLoaded) projectedCourses(nextLedger) else courses.map { if (it.id == selectedCourseId) it.copy(attendance = pendingAttendance) else it }
        if (!commitUserData(nextLedger, nextCourses)) return
        invalidateWritePreview()
        feedback = message(293, (selectedCourse.displayTitle), (pendingAttendanceScope.label))
        open(AppScreen.DETAIL)
    }

    private fun attendanceCommand(): RuleEditCommand? = when (pendingAttendanceScope) {
        AttendanceEditScope.ALL_OCCURRENCES -> RuleEditCommand.WholeCourse(setOf(selectedCourseId), pendingAttendance)
        AttendanceEditScope.ONLY_THIS_OCCURRENCE -> selectedCourse.instanceKey?.let {
            RuleEditCommand.Instances(setOf(selectedCourseId), setOf(it), pendingAttendance)
        } ?: run { feedback = message(294); null }
        AttendanceEditScope.CURRENT_AND_FUTURE -> selectedCourse.startsAt?.let {
            RuleEditCommand.FromOccurrence(mapOf(selectedCourseId to it), pendingAttendance)
        } ?: run { feedback = message(294); null }
        AttendanceEditScope.SPECIFIED_AND_FUTURE -> AttendanceEffectiveDateParser.parseOrNull(pendingEffectiveDate)
            ?.let { RuleEditCommand.FromDate(setOf(selectedCourseId), it, pendingAttendance) }
            ?: run { feedback = message(295); null }
    }

    fun toggleFavorite(courseId: String) {
        val nextCourses = courses.map { course ->
            if (course.id == courseId) course.copy(isFavorite = !course.isFavorite) else course
        }
        commitUserData(nextCourses = nextCourses)
    }

    fun requestSync() {
        prepareCalendarWrite()
    }

    /** Vorbereitung nach „In Kalender schreiben“ berechnet nur Bestätigungsinformationen; kein Schreiben. */
    fun prepareCalendarWrite() = requestManagedSyncPreview()

    /** Bereinigungsseite bereitet Bereich und zweite Bestätigung vor; Öffnen greift nicht auf Kalender zu. */
    fun openOutputCleanup() {
        if (availableManagedOutputTargets().isEmpty()) {
            feedback = message(296)
            return
        }
        managedSyncPreview = null
        navigation.openOutputCleanup()
    }

    /** Differenz aus Quelle und Regeln berechnen; keine Zielabfrage, Erstellung oder Ausgabe. */
    fun requestManagedSyncPreview() {
        if (managedSyncInProgress) { feedback = message(297); return }
        if (currentSourceOccurrences.isEmpty() && ruleLedger.manualEvents.isEmpty()) {
            feedback = message(298)
            return
        }
        val targets = enabledOutputTargets()
        managedDesiredEvents = SyncCandidateFactory().build(
            occurrences = currentSourceOccurrences,
            selectedSourceOccurrences = selectedSourceOccurrences,
            rules = GroupAttendanceRuleResolver.materialize(ruleLedger, currentSourceOccurrences),
            manualEvents = ruleLedger.manualEvents,
            overrides = courses.associate { it.id to it.localOverride },
        )
        managedSyncPreview = ManagedSyncEngine().preview(managedDesiredEvents, ruleLedger.mappings, targets)
        navigation.openManagedSyncPreview()
    }

    /** Google-Autorisierung nur nach ausdrücklichem Antippen; Token bleibt nur im Prozess. */
    fun requestGoogleAuthorization() {
        val rejection = outputWorkflow.requestGoogleAuthorization(syncDestinations.googleCalendarEnabled || OutputTarget.GOOGLE_CALENDAR in currentWriteTargets)
        if (rejection != null) {
            feedback = rejection
            return
        }
        feedback = googleAuthorizationDetail
    }

    fun resolveGoogleAuthorization(outcome: GoogleAuthorizationOutcome) {
        feedback = outputWorkflow.resolveGoogleAuthorization(outcome)
    }

    /** Nur lesende Verbindungsabsicht; App-Einstieg prüft eigenes Ziel, erstellt oder schreibt nichts. */
    fun requestGoogleConnectionCheck() {
        outputWorkflow.requestGoogleConnectionCheck()?.let { feedback = it }
    }

    fun resolveGoogleConnectionCheck(token: Long, result: GoogleConnectionCheckResult) {
        if (outputWorkflow.resolveGoogleConnectionCheck(token, result)) feedback = result.userSummary
    }

    /** Nur strukturiert erkannter Google-401-Pfad; Kalender, Zuordnungen und andere Ziele unverändert. */
    fun invalidateGoogleAuthorizationAfterUnauthorized(): String {
        val summary = outputWorkflow.invalidateGoogleAuthorization()
        feedback = summary
        return summary
    }

    /** Nur bereinigtes Token-Cache-Ergebnis anzeigen; keine Token, Konten oder Google-Antworten speichern. */
    fun resolveRejectedGoogleTokenCacheClear(cleared: Boolean) {
        outputWorkflow.resolveRejectedGoogleTokenCacheClear(cleared)?.let { feedback = it }
    }

    fun consumeGoogleAuthorizationRequest() = outputWorkflow.consumeGoogleAuthorizationRequest()

    /** Ausführung nur nach Nutzerbestätigung; Oberfläche erzeugt eigenes Ziel im Hintergrund und ruft Gateway auf. */
    fun requestManagedSyncExecution() {
        if (managedSyncInProgress) return
        if (cleanupCatalogReadTargets.isNotEmpty()) { feedback = message(299); return }
        if (managedSyncPreview == null) { feedback = message(300); return }
        if (currentWriteTargets.isEmpty()) { feedback = message(301); return }
        if (OutputTarget.LOCAL_CALENDAR in currentWriteTargets && !localCalendarPermissionGranted) { feedback = message(302); return }
        if (OutputTarget.GOOGLE_CALENDAR in currentWriteTargets && !hasGoogleAuthorization) { feedback = message(303); return }
        confirmedWriteTargets = managedSyncPreview!!.targetChanges.keys
        if (outputWorkflow.beginSync() != null) feedback = message(304)
    }

    val readyWriteTargets: Set<OutputTarget> get() = managedSyncPreview?.targetChanges?.keys.orEmpty().filterTo(mutableSetOf()) {
        if (it == OutputTarget.LOCAL_CALENDAR) localCalendarPermissionGranted else hasGoogleAuthorization
    }

    val currentWriteTargets: Set<OutputTarget> get() = managedSyncPreview?.targetChanges?.keys.orEmpty()

    /** Auswahl berechnet nur Bestätigung neu; keine Standardänderung oder Netzwerkanfrage. */
    fun toggleCurrentWriteTarget(target: OutputTarget) {
        if (managedSyncInProgress || managedSyncPreview == null) return
        val targets = if (target in currentWriteTargets) currentWriteTargets - target else currentWriteTargets + target
        managedSyncPreview = ManagedSyncEngine().preview(managedDesiredEvents, ruleLedger.mappings, targets)
    }

    /** „Nur bereite Kalender“ ausdrücklich wählen; normale Bestätigung niemals still auf weniger Ziele reduzieren. */
    fun requestReadyManagedSyncExecution() {
        if (managedSyncInProgress) return
        if (cleanupCatalogReadTargets.isNotEmpty()) return
        if (managedSyncPreview == null || readyWriteTargets.isEmpty()) { feedback = message(305); return }
        confirmedWriteTargets = readyWriteTargets
        if (outputWorkflow.beginSync() != null) feedback = message(306)
    }

    /** Erst nach zweiter Bestätigung; Löschobjekte über UID/Originalinstanz, niemals Anzeigetitel bestimmen. */
    fun requestManagedRollback(scope: ManagedEventCleanupScope = ManagedEventCleanupScope.AllManagedEvents, selectedTargets: Set<OutputTarget> = availableManagedOutputTargets()) {
        if (managedSyncInProgress) return
        if (cleanupCatalogReadTargets.isNotEmpty()) { feedback = message(307); return }
        if (scope is ManagedEventCleanupScope.SelectedIdentities && scope.identities.isEmpty()) {
            feedback = message(308)
            return
        }
        val targets = selectedTargets.intersect(availableManagedOutputTargets())
        if (targets.isEmpty()) { feedback = message(309); return }
        if (OutputTarget.LOCAL_CALENDAR in targets && !localCalendarPermissionGranted) { feedback = message(310); return }
        if (OutputTarget.GOOGLE_CALENDAR in targets && !hasGoogleAuthorization) { feedback = message(311); return }
        managedRollbackScope = scope
        managedRollbackTargets = targets
        if (outputWorkflow.beginRollback() != null) feedback = message(312)
    }

    /** Leere Kursmenge bedeutet keine Auswahl; null nur im Datumsmodus als unbegrenzte Kurswahl. */
    fun cleanupIdentities(
        sourceUids: Set<String>?,
        startsOn: LocalDate?,
        endsOn: LocalDate?,
        targets: Set<OutputTarget> = availableManagedOutputTargets(),
    ): Set<ManagedEventIdentity> = cleanupOccurrencesForTargets(targets).asSequence()
        .filter { sourceUids == null || it.sourceUid in sourceUids }
        .filter { occurrence ->
            val localDate = occurrence.startsAt.atZone(ZoneId.of("Europe/Berlin")).toLocalDate()
            (startsOn == null || !localDate.isBefore(startsOn)) && (endsOn == null || !localDate.isAfter(endsOn))
        }
        .map { ManagedEventIdentity(it.sourceUid, it.instanceKey) }
        .toSet()

    /** Quelle und Schreibprotokoll vereinigen; auch abgesagte/entfallene geschriebene Termine bleiben bereinigbar. */
    val cleanupOccurrences: List<SourceCalendarOccurrence> get() = cleanupOccurrencesForTargets(availableManagedOutputTargets())

    fun cleanupOccurrencesForTargets(targets: Set<OutputTarget>): List<SourceCalendarOccurrence> {
        val written = ruleLedger.mappings.filter { it.target in targets }.mapNotNull { it.writtenEvent }.map {
            SourceCalendarOccurrence(it.identity.sourceUid, it.identity.instanceKey, it.title, it.startsAt, it.endsAt, false, location = it.location)
        }
        val known = written.map { it.identity }.toSet()
        val fallback = currentSourceOccurrences + savedSourceSnapshots.previous?.occurrences.orEmpty() + ruleLedger.manualEvents.map {
            SourceCalendarOccurrence("manual:${(it.id)}", it.id, it.title, it.startsAt, it.endsAt, false)
        }
        return (written + fallback.filter { it.identity !in known }).distinct()
    }

    /** Ziele können verschiedene Zeitversionen enthalten; Datumsfilter je Ziel, keine zielübergreifende Löschwahl. */
    fun selectedCleanupScope(sourceUids: Set<String>?, startsOn: LocalDate?, endsOn: LocalDate?, targets: Set<OutputTarget>): ManagedEventCleanupScope.SelectedIdentities {
        val byTarget = targets.associateWith { cleanupIdentities(sourceUids, startsOn, endsOn, setOf(it)) }
        return ManagedEventCleanupScope.SelectedIdentities(byTarget.values.flatten().toSet(), byTarget)
    }

    val managedOutputTargetsForCleanup: Set<OutputTarget> get() = availableManagedOutputTargets()

    fun currentGoogleAccessToken(): String? = outputWorkflow.currentGoogleAccessToken()
    fun currentManagedOperationToken(): Long = outputWorkflow.operationToken
    fun currentDesiredEvents(): List<DesiredCalendarEvent> = managedDesiredEvents
    fun currentManagedTargets(): Set<OutputTarget> = if (managedRollbackRequested) {
        managedRollbackTargets
    } else {
        confirmedWriteTargets
    }
    fun currentManagedCleanupScope(): ManagedEventCleanupScope = managedRollbackScope

    /** Nur zurückgegebene Zuordnungen und bereinigte Zahlen übernehmen; keine Token/Quellinhalte im Bestand. */
    fun resolveManagedOperation(token: Long, result: ManagedSyncResult, configuration: PrivateSyncConfiguration) {
        if (!outputWorkflow.finishOperation(token)) return
        writeProgress.finish()
        ruleLedger = ruleLedger.copy(
            mappings = result.mappings,
            lastSummary = SyncRunSummary(
                Instant.now(),
                result.createdCount,
                result.updatedCount,
                result.removedCount,
                result.targetResults.sumOf { target -> target.changes.count { it.type == com.tequila.tumcalendarcontrol.sync.SyncChangeType.KEEP } },
                result.failureSummary,
                result.cleanupDetailSummary ?: result.writeDetailSummary,
            ),
        )
        privateSyncConfiguration = privateSyncConfiguration.copy(localCalendarId = configuration.localCalendarId, googleCalendarId = configuration.googleCalendarId)
        if (previewChangedDuringWrite) invalidateWritePreview()
        feedback = listOfNotNull(result.cleanupDetailSummary ?: result.writeDetailSummary, result.failureSummary)
            .takeIf(List<String>::isNotEmpty)
            ?.let { joinMessages(it) }
            ?: message(314, (result.createdCount), (result.updatedCount), (result.removedCount))
    }

    fun failManagedOperation(token: Long, summary: String) {
        if (outputWorkflow.finishOperation(token)) {
            writeProgress.finish()
            if (previewChangedDuringWrite) invalidateWritePreview()
            feedback = summary
        }
    }

    fun openManualEvents() = navigation.openManualEvents()

    /** Eigene generierte Termin-ID; Europe/Berlin-Zeiten. Eingabefehler verändern keinen Bestand. */
    fun addManualEvent(title: String, date: String, startHour: String, startMinute: String, endHour: String, endMinute: String, attendance: AttendanceState) {
        val item = runCatching {
            val day = LocalDate.parse(date)
            val start = LocalDateTime.of(day, java.time.LocalTime.of(startHour.toInt(), startMinute.toInt())).atZone(ZoneId.of("Europe/Berlin")).toInstant()
            val end = LocalDateTime.of(day, java.time.LocalTime.of(endHour.toInt(), endMinute.toInt())).atZone(ZoneId.of("Europe/Berlin")).toInstant()
            require(end.isAfter(start))
            ManualScheduleItem(UUID.randomUUID().toString(), title.trim().ifBlank { error(message(315)) }, start, end, attendance)
        }.getOrElse { feedback = message(316); return }
        val nextLedger = ruleLedger.copy(manualEvents = ManualScheduleEditor().upsert(ruleLedger.manualEvents, item))
        if (!commitUserData(nextLedger)) return
        invalidateWritePreview()
        feedback = message(317)
    }

    fun removeManualEvent(itemId: String) {
        val nextLedger = ruleLedger.copy(manualEvents = ManualScheduleEditor().remove(ruleLedger.manualEvents, itemId))
        if (!commitUserData(nextLedger)) return
        invalidateWritePreview()
        feedback = message(318)
    }

    /** Hierarchische Rückkehr innerhalb der App; nur Hauptseite übergibt Beenden an das System. */
    fun handleBack(): Boolean = navigation.handleBack()

    fun canHandleBack(): Boolean = navigation.canHandleBack()

    private fun enabledOutputTargets(): Set<OutputTarget> = buildSet {
        if (syncDestinations.localCalendarEnabled) add(OutputTarget.LOCAL_CALENDAR)
        if (syncDestinations.googleCalendarEnabled) add(OutputTarget.GOOGLE_CALENDAR)
    }

    /** Gespeicherte Ziel-ID erlaubt Bereinigung alter App-markierter Termine; keine Inhaltsanalyse oder Zielerstellung. */
    private fun availableManagedOutputTargets(): Set<OutputTarget> = buildSet {
        addAll(ruleLedger.mappings.map(StoredEventMapping::target))
        if (privateSyncConfiguration.localCalendarId.isNotBlank()) add(OutputTarget.LOCAL_CALENDAR)
        if (privateSyncConfiguration.googleCalendarId.isNotBlank()) add(OutputTarget.GOOGLE_CALENDAR)
    }

    /** Einziger Anzeige-Rückfluss nach Quelle/Regeln; Quelle, Regeln und Zielkalender unverändert. */
    private fun refreshCoursesFromSource() {
        if (!sourceCatalogLoaded) return
        rememberPresentations()
        courses = SourceCourseProjection.courses(
            sourceCourses = sourceCourses,
            occurrences = currentSourceOccurrences,
            rules = GroupAttendanceRuleResolver.materialize(ruleLedger, currentSourceOccurrences),
            previousCourses = courses,
            savedPresentations = savedCoursePresentations,
        )
    }

    private fun rememberPresentations() {
        courses.forEach { savedCoursePresentations[it.id] = CoursePresentation(it.isFavorite, it.localOverride) }
    }

    private fun invalidateWritePreview() {
        if (managedSyncInProgress) { previewChangedDuringWrite = true; return }
        previewChangedDuringWrite = false
        managedSyncPreview = null
        managedDesiredEvents = emptyList()
    }

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
