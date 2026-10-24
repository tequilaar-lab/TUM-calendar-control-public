/*
 * Aufgabe: Navigation, Darstellung, Kurse, Suche, Favoriten, Teilnahme, Profil und Ausgabezustände halten. Aktionen
 *   zu Teilnahme, Verbindungen/Prüfung, Sicherung, eigenen Terminen, Bereinigung und Google liegen als Erweiterungen in
 *   State*Actions.kt, gemeinsame Typen in CalendarControlTypes.kt; dafür nötige Mitglieder sind modulintern (internal).
 * Ein-/Ausgabe: Benutzerabsichten und Adapterergebnisse -> beobachtbarer Kurs-/Navigations-/Ausgabezustand.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Datenbank, Datumsdialog, echte Kalenderausgabe, Netzwerksynchronisierung oder Kontoidentität.
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.i18n.joinMessages

import androidx.compose.runtime.mutableStateOf
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.domain.Course
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
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceChangeSummary
import com.tequila.tumcalendarcontrol.sync.SourceOccurrenceIdentity
import com.tequila.tumcalendarcontrol.sync.SyncCandidateFactory
import com.tequila.tumcalendarcontrol.sync.ManagedSyncEngine
import com.tequila.tumcalendarcontrol.sync.ManagedSyncPreview
import com.tequila.tumcalendarcontrol.sync.ManagedSyncResult
import com.tequila.tumcalendarcontrol.sync.ManualEventKind
import com.tequila.tumcalendarcontrol.sync.OutputTarget
import com.tequila.tumcalendarcontrol.sync.DesiredCalendarEvent
import com.tequila.tumcalendarcontrol.sync.SyncRunSummary
import com.tequila.tumcalendarcontrol.sync.StoredEventMapping
import com.tequila.tumcalendarcontrol.sync.ManagedEventCleanupScope
import com.tequila.tumcalendarcontrol.sync.ManagedEventIdentity
import com.tequila.tumcalendarcontrol.sync.RuleLedgerBackup
import com.tequila.tumcalendarcontrol.sync.GroupAttendanceRuleResolver
import com.tequila.tumcalendarcontrol.sync.SourceCheckSettings
import com.tequila.tumcalendarcontrol.sync.SourceCheckStatus
import java.time.Instant
import java.time.ZoneId

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
    internal val sourceWorkflow = SourceReadWorkflowState(initialSourceSnapshots)
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
    internal val navigation = NavigationState()
    val screen: AppScreen get() = navigation.screen
    /** Herkunft der Kursdetails (für die zweispaltige Darstellung). */
    val detailOrigin: AppScreen get() = navigation.detailOrigin
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
        internal set
    var feedback by mutableStateOf<String?>(null)
    var themeMode by mutableStateOf(initialAppearance.mode)
    var courseTypeNames by mutableStateOf<Map<String, String>>(emptyMap())
    var individualAttendanceMode by mutableStateOf(false)
    var individualAttendanceDrafts by mutableStateOf<Map<String, IndividualAttendanceDraft>>(emptyMap())
    var pageMemory by mutableStateOf(PageMemory())
    internal var openedIndividualDrafts: Map<String, IndividualAttendanceDraft> = emptyMap()
    internal var sourceEditorOpened = false
    internal var sourceEditorChanged = false
    internal var sourceAttendanceChanged = false
    internal var sourceStateChosen = false
    internal var detailAttendanceBaseline: Triple<AttendanceState, AttendanceEditScope, String>? = null
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
    internal fun rememberAttendanceOptions() {
        pageMemory = pageMemory.copy(individual = individualAttendanceMode, scope = pendingSourceAttendanceScope, date = pendingSourceEffectiveDate)
    }

    /** Benutzerdaten vor beobachtbarem Zustand synchron speichern; keine halben Regeln/Favoriten bei Fehler. */
    internal fun commitUserData(nextLedger: RuleLedger = ruleLedger, nextCourses: List<Course> = courses): Boolean {
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

    internal fun projectedCourses(ledger: RuleLedger, previous: List<Course> = courses): List<Course> =
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
        internal set
    var pendingSyncDestinationChange by mutableStateOf<PendingSyncDestinationChange?>(null)
        internal set
    var privateSyncConfiguration by mutableStateOf(initialPrivateSyncConfiguration)
        internal set
    var ruleLedger by mutableStateOf(initialRuleLedger)
        internal set
    var sourceCheckSettings by mutableStateOf(initialSourceCheckSettings)
        internal set
    var sourceCheckStatus by mutableStateOf(initialSourceCheckStatus)
        internal set
    var sourceCheckSchedulingRequested by mutableStateOf(false)
        internal set
    var notificationPermissionRequestPending by mutableStateOf(false)
        internal set
    var ruleBackupExportRequested by mutableStateOf(false)
        internal set
    var ruleBackupImportRequested by mutableStateOf(false)
        internal set
    var pendingRuleBackup by mutableStateOf<RuleLedgerBackup?>(null)
        internal set
    val offlinePreview: SyncPreview = OfflinePreviewSamples.preview()
    internal val outputWorkflow = ManagedOutputWorkflowState()
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
        internal set(value) { outputWorkflow.preview = value }
    private var managedDesiredEvents: List<DesiredCalendarEvent>
        get() = outputWorkflow.desiredEvents
        set(value) { outputWorkflow.desiredEvents = value }
    /** Bereinigungsbereich nur kurz vor/nach Bestätigung im Speicher; niemals im Regelbestand speichern. */
    internal var managedRollbackScope by mutableStateOf<ManagedEventCleanupScope>(ManagedEventCleanupScope.AllManagedEvents)
    internal var managedRollbackTargets by mutableStateOf<Set<OutputTarget>>(emptySet())
    private var confirmedWriteTargets: Set<OutputTarget> = emptySet()
    private var previewChangedDuringWrite = false
    var cleanupCatalogReadTargets by mutableStateOf<Set<OutputTarget>>(emptySet())
        internal set
    var cleanupCatalogReadToken by mutableStateOf(0L)
        internal set

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

    /** Kursdetails öffnen; false = Kurs nicht im Verzeichnis (Seite bleibt, Aufrufer entscheidet über Rückfall). */
    fun openCourse(courseId: String): Boolean {
        val course = courses.firstOrNull { it.browseKey == courseId || it.id == courseId } ?: return false
        selectedCourseId = course.id
        selectedInstanceKey = course.instanceKey
        pendingAttendance = selectedCourse.attendance
        navigation.openDetail()
        return true
    }

    fun openCourseEdit() = navigation.openCourseEdit()

    fun toggleSourceStatusVisible(status: TumEventStatus) {
        visibleSourceStatuses = if (status in visibleSourceStatuses) visibleSourceStatuses - status else visibleSourceStatuses + status
    }

    fun openProfile() = navigation.openProfile()

    /** Validiertes lokales Profil; kein externes Konto. */
    fun updateProfile(updatedProfile: UserProfile) {
        profile = updatedProfile.copy(displayName = updatedProfile.displayName.trim().ifBlank { UserProfile().displayName })
    }

    /** Berechtigung ist keine Ausgabe; erst nach Systemzustimmung künftige lokale Ausgabeerlaubnis speichern. */
    internal var permissionForCurrentWriteOnly = false

    fun openSyncConfiguration() = navigation.openSyncConfiguration()

    /** Zeitraum unabhängig vom Kontoformular; Öffnen/Speichern löst keine Netzlesung aus. */
    fun openSourceRange() = navigation.openSourceRange()

    fun openBackgroundCheck() = navigation.openBackgroundCheck()

    fun openRuleBackup() = navigation.openRuleBackup()
    fun openSourceChanges() = navigation.openSourceChanges()

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

    fun requestSync() {
        prepareCalendarWrite()
    }

    /** Vorbereitung nach „In Kalender schreiben“ berechnet nur Bestätigungsinformationen; kein Schreiben. */
    fun prepareCalendarWrite() = requestManagedSyncPreview()

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
            mustAttendReminder = calendarReminderMinutes,
        )
        managedSyncPreview = ManagedSyncEngine().preview(managedDesiredEvents, ruleLedger.mappings, targets)
        navigation.openManagedSyncPreview()
    }

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

    /** Quelle und Schreibprotokoll vereinigen; auch abgesagte/entfallene geschriebene Termine bleiben bereinigbar. */
    val cleanupOccurrences: List<SourceCalendarOccurrence> get() = cleanupOccurrencesForTargets(availableManagedOutputTargets())

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
    /** Vorgewählte Art beim nächsten Öffnen der Campustermine (Einstiege wie „Prüfung hinzufügen“). */
    var manualEventsInitialKind by mutableStateOf(ManualEventKind.NORMAL)
    /** Widget, das die Widget-Seite beim Öffnen bearbeitet (aus der Einrichtung); null = erstes platziertes. */
    var editingWidgetId by mutableStateOf<Int?>(null)
    /** Erinnerung beim Schreiben (CalendarReminderPreferences): null = nicht verwaltet, NO_REMINDER oder Minuten. */
    var calendarReminderMinutes by mutableStateOf<Int?>(null)
        internal set
    /** Entwurf aus geteiltem Text für das Formular der Campustermine; das Formular übernimmt und leert ihn. */
    var manualEventDraft by mutableStateOf<com.tequila.tumcalendarcontrol.sync.ManualEventDraft?>(null)

    /** Hierarchische Rückkehr innerhalb der App; nur Hauptseite übergibt Beenden an das System. */
    fun handleBack(): Boolean = navigation.handleBack()

    fun canHandleBack(): Boolean = navigation.canHandleBack()

    private fun enabledOutputTargets(): Set<OutputTarget> = buildSet {
        if (syncDestinations.localCalendarEnabled) add(OutputTarget.LOCAL_CALENDAR)
        if (syncDestinations.googleCalendarEnabled) add(OutputTarget.GOOGLE_CALENDAR)
    }

    /** Gespeicherte Ziel-ID erlaubt Bereinigung alter App-markierter Termine; keine Inhaltsanalyse oder Zielerstellung. */
    internal fun availableManagedOutputTargets(): Set<OutputTarget> = buildSet {
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

    internal fun invalidateWritePreview() {
        if (managedSyncInProgress) { previewChangedDuringWrite = true; return }
        previewChangedDuringWrite = false
        managedSyncPreview = null
        managedDesiredEvents = emptyList()
    }

}
