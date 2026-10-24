/*
 * Aufgabe: Lesende Quellenanfrage, Laufzeitkatalog und Terminauswahl halten.
 * Ein-/Ausgabe: Leseergebnis und Terminwahl -> beobachtbarer Katalogzustand und Anfragetoken.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Preference-Kodierung, Kursprojektion, Teilnahmeregelspeicherung, Ausgabekandidaten, Kontoverbindungen oder Kalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.sync.RuleEditCommand
import com.tequila.tumcalendarcontrol.sync.RuleLedger
import com.tequila.tumcalendarcontrol.sync.RuleLedgerEditor
import com.tequila.tumcalendarcontrol.sync.SyncRule
import com.tequila.tumcalendarcontrol.sync.SourceChangeSummary
import com.tequila.tumcalendarcontrol.sync.IcalReadOnlyImportResult
import com.tequila.tumcalendarcontrol.sync.IcalReadOnlyPreview
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceCourse
import com.tequila.tumcalendarcontrol.sync.SourceOccurrenceIdentity
import com.tequila.tumcalendarcontrol.sync.GroupAttendanceRuleEditor

/** Reines Lesezustandsergebnis; Aufrufer entscheidet Rückmeldung oder Hintergrundauftrag. */
internal sealed interface SourceReadRequest {
    data object Ignored : SourceReadRequest
    data object NeedsReloadConfirmation : SourceReadRequest
    data class Rejected(val message: String) : SourceReadRequest
    data class Requested(val token: Long) : SourceReadRequest
}

/** Nach Lesung nur Laufzeitdaten zurückgeben; Aufrufer projiziert sie in die Kursanzeige. */
internal sealed interface SourceReadResolution {
    data object Ignored : SourceReadResolution
    data class Success(val preview: IcalReadOnlyPreview) : SourceReadResolution
    data class Failure(val message: String) : SourceReadResolution
}

/** Regelberechnung ohne Quellinhalt; Aufrufer verbindet Regeln und Kursprojektion mit Oberfläche. */
internal sealed interface SourceAttendanceRuleResult {
    data class Invalid(val message: String) : SourceAttendanceRuleResult
    data class Applied(
        val rules: RuleLedger,
        val sourceUids: Set<String>,
        val favoriteAction: SourceFavoriteAction,
        val affectedOccurrences: Set<SourceOccurrenceIdentity> = emptySet(),
    ) : SourceAttendanceRuleResult
}

internal class SourceReadWorkflowState(initialSnapshots: SavedSourceSnapshots = SavedSourceSnapshots()) {
    /** Private Präferenzen stellen Daten wieder her; Zustandsmaschine wählt und rotiert erfolgreiche Lesungen. */
    private var snapshots by mutableStateOf(
        initialSnapshots.let { snapshots ->
            if (snapshots.active == null && snapshots.previous != null) snapshots.select(SourceSnapshotSlot.PREVIOUS) else snapshots
        },
    )
    var readRequested by mutableStateOf(false)
        private set
    var readInProgress by mutableStateOf(false)
        private set
    /** Markiert nur gerade abgeschlossene Lesung; nach Neustart bleibt „neueste“ die letzte gespeicherte erfolgreiche Version. */
    private var freshReadCompletedInThisRun by mutableStateOf(false)
    var preview by mutableStateOf(snapshots.active?.toPreview())
        private set
    var previewError by mutableStateOf<String?>(null)
        private set
    var courses by mutableStateOf(preview?.courses.orEmpty())
        private set
    var catalogLoaded by mutableStateOf(preview != null)
        private set
    var occurrences by mutableStateOf(preview?.occurrences.orEmpty())
        private set
    /** Private Seitenpräferenzen dürfen Auswahl herstellen; jedes Element bezeichnet UID plus Originalinstanz. */
    var selectedOccurrences by mutableStateOf<Set<SourceOccurrenceIdentity>>(emptySet())
        private set
    var browserQuery by mutableStateOf("")
    var browserFilter by mutableStateOf(SourceCourseFilter.ALL)
    var reloadConfirmationPending by mutableStateOf(false)
        private set
    var pendingAttendance by mutableStateOf(AttendanceState.CHOOSE_TO_ATTEND)
    var pendingAttendanceScope by mutableStateOf(AttendanceEditScope.ALL_OCCURRENCES)
    var pendingEffectiveDate by mutableStateOf("")
    var pendingFavoriteAction by mutableStateOf(SourceFavoriteAction.KEEP)
    var readToken by mutableStateOf(0L)
        private set
    var lastSelectionRemovedCount by mutableStateOf(0)
        private set

    /** Nur App-Einstieg schreibt private Präferenzen; keine Abonnementadresse oder Kalenderdaten offenlegen. */
    val savedSnapshots: SavedSourceSnapshots get() = snapshots
    val activeSnapshotSlot: SourceSnapshotSlot get() = snapshots.activeSlot
    val hasPreviousSnapshot: Boolean get() = snapshots.previous != null
    val activeSnapshotWasReadInThisRun: Boolean get() =
        freshReadCompletedInThisRun && snapshots.activeSlot == SourceSnapshotSlot.LATEST
    val activeSnapshotDifference: SourceSnapshotDifference? get() = snapshots.latest
        ?.let { latest -> snapshots.previous?.let { previous -> SourceSnapshotDifferenceModel.compare(previous, latest) } }

    /** Zwischenspeicher je Momentaufnahmepaar: Zeilen fragen Änderungsarten einzeln ab, Vergleich nur einmal. */
    private var changeCache: Pair<SavedSourceSnapshots, SourceChangeSummary>? = null

    /** Ausführliche Änderungen der neuesten gegenüber der vorherigen Lesung; leer ohne Vorgänger. */
    val latestChanges: SourceChangeSummary get() {
        val current = snapshots
        changeCache?.takeIf { it.first === current }?.let { return it.second }
        val latest = current.latest
        val previous = current.previous
        val summary = if (latest == null || previous == null) SourceChangeSummary.EMPTY else SourceChangeSummary.between(previous.occurrences, latest.occurrences)
        changeCache = current to summary
        return summary
    }

    /** Mit altem Katalog Bestätigung verlangen; ohne alten Katalog direkt lesende Anfrage vorbereiten. */
    fun requestRead(hasSubscription: Boolean): SourceReadRequest = when {
        !hasSubscription -> SourceReadRequest.Rejected(message(649))
        readInProgress -> SourceReadRequest.Ignored
        courses.isNotEmpty() -> {
            reloadConfirmationPending = true
            SourceReadRequest.NeedsReloadConfirmation
        }
        else -> requestDirectRead(hasSubscription)
    }

    fun resolveReloadConfirmation(accepted: Boolean, hasSubscription: Boolean): SourceReadRequest {
        reloadConfirmationPending = false
        return if (accepted) requestDirectRead(hasSubscription) else SourceReadRequest.Ignored
    }

    /** Monotoner Token; resolveRead ignoriert veraltete Hintergrundresultate. */
    fun requestDirectRead(hasSubscription: Boolean): SourceReadRequest = when {
        !hasSubscription -> SourceReadRequest.Rejected(message(649))
        readInProgress -> SourceReadRequest.Ignored
        else -> beginRead()
    }

    /** Nach Konfigurationsprüfung neue Anfrage ohne zusätzliche Katalogbestätigung erzeugen. */
    fun beginRead(): SourceReadRequest.Requested {
        previewError = null
        readToken += 1
        readInProgress = true
        readRequested = true
        return SourceReadRequest.Requested(readToken)
    }

    fun resolveRead(token: Long, result: IcalReadOnlyImportResult): SourceReadResolution {
        if (token != readToken || !readInProgress) return SourceReadResolution.Ignored
        readRequested = false
        readInProgress = false
        return when (result) {
            is IcalReadOnlyImportResult.Success -> {
                snapshots = snapshots.storeFresh(SavedSourceSnapshot.from(result.preview))
                freshReadCompletedInThisRun = true
                bindActiveSnapshot(clearSelection = true)
                browserQuery = ""
                browserFilter = SourceCourseFilter.ALL
                previewError = null
                SourceReadResolution.Success(result.preview)
            }
            is IcalReadOnlyImportResult.Failure -> {
                previewError = result.message
                SourceReadResolution.Failure(result.message)
            }
        }
    }

    /** Nur zwischen zwei gespeicherten erfolgreichen Versionen wechseln; weder Quelle lesen noch schreiben. */
    fun selectSnapshot(slot: SourceSnapshotSlot): Boolean {
        if (slot == SourceSnapshotSlot.PREVIOUS && snapshots.previous == null) return false
        snapshots = snapshots.select(slot)
        bindActiveSnapshot(clearSelection = true)
        previewError = null
        return true
    }

    private fun bindActiveSnapshot(clearSelection: Boolean) {
        val previousSelection = selectedOccurrences
        preview = snapshots.active?.toPreview()
        courses = preview?.courses.orEmpty()
        occurrences = preview?.occurrences.orEmpty()
        catalogLoaded = preview != null
        if (clearSelection) {
            selectedOccurrences = previousSelection.intersect(occurrences.filterNot(SourceCalendarOccurrence::isCancelled).map { it.identity }.toSet())
            lastSelectionRemovedCount = previousSelection.size - selectedOccurrences.size
        }
    }

    fun toggleSelection(identity: SourceOccurrenceIdentity) {
        selectedOccurrences = if (identity in selectedOccurrences) selectedOccurrences - identity else selectedOccurrences + identity
    }

    fun selectAllOccurrences() { selectedOccurrences = occurrences.filterNot(SourceCalendarOccurrence::isCancelled).map(SourceCalendarOccurrence::identity).toSet() }
    fun clearSelection() { selectedOccurrences = emptySet() }

    fun includeInSelection(identities: Set<SourceOccurrenceIdentity>) { selectedOccurrences = selectedOccurrences + identities }
    fun excludeFromSelection(identities: Set<SourceOccurrenceIdentity>) { selectedOccurrences = selectedOccurrences - identities }

    fun attendanceScopeCount(): Int? = SourceAttendanceScope.resolve(occurrences, selectedOccurrences, pendingAttendanceScope, pendingEffectiveDate)?.size

    /** Sammelformular vor Öffnen zurücksetzen; vorheriger Bereich/Favoritenaktion nicht auf neue Auswahl übertragen. */
    fun prepareAttendanceEditor(selection: Set<SourceOccurrenceIdentity>, firstState: AttendanceState): Boolean {
        if (selection.isEmpty()) return false
        pendingAttendance = firstState
        pendingAttendanceScope = AttendanceEditScope.ALL_OCCURRENCES
        pendingEffectiveDate = ""
        pendingFavoriteAction = SourceFavoriteAction.KEEP
        selectedOccurrences = selection
        return true
    }

    /** Auswahl zu stabilen UID-Regeln; Einzelterminregel bewahrt Originalinstanz jeder Auswahl. */
    fun applyAttendanceRules(
        currentLedger: RuleLedger,
        selection: Set<SourceOccurrenceIdentity> = selectedOccurrences,
        attendance: AttendanceState? = pendingAttendance,
        favoriteAction: SourceFavoriteAction = pendingFavoriteAction,
        scope: AttendanceEditScope = pendingAttendanceScope,
        effectiveDate: String = pendingEffectiveDate,
    ): SourceAttendanceRuleResult {
        val anchors = occurrences.filter { it.identity in selection && !it.isCancelled }
        val selected = SourceAttendanceScope.resolve(occurrences, selection, scope, effectiveDate)
            ?: return SourceAttendanceRuleResult.Invalid(message(295))
        if (selected.isEmpty()) return SourceAttendanceRuleResult.Invalid(message(650))
        val sourceUids = selected.map(SourceCalendarOccurrence::sourceUid).toSet()
        if (attendance == null) return SourceAttendanceRuleResult.Applied(
            currentLedger, sourceUids, favoriteAction, selected.map(SourceCalendarOccurrence::identity).toSet(),
        )
        val command = when (scope) {
            AttendanceEditScope.ALL_OCCURRENCES -> RuleEditCommand.WholeCourse(sourceUids, attendance)
            AttendanceEditScope.CURRENT_AND_FUTURE -> RuleEditCommand.FromOccurrence(selected.groupBy { it.sourceUid }.mapValues { (_, items) -> items.minOf { it.startsAt } }, attendance)
            AttendanceEditScope.SPECIFIED_AND_FUTURE -> AttendanceEffectiveDateParser.parseOrNull(effectiveDate)
                ?.let { RuleEditCommand.FromDate(sourceUids, it, attendance) }
                ?: return SourceAttendanceRuleResult.Invalid(message(295))
            AttendanceEditScope.ONLY_THIS_OCCURRENCE -> RuleEditCommand.InstancesBySource(
                sourceUids = sourceUids,
                instanceKeysBySourceUid = selected.groupBy(SourceCalendarOccurrence::sourceUid)
                    .mapValues { (_, items) -> items.map(SourceCalendarOccurrence::instanceKey).toSet() },
                state = attendance,
            )
        }
        val existing = currentLedger.rules.associateBy(SyncRule::sourceUid)
        val ensuredRules = sourceUids.map { sourceUid ->
            existing[sourceUid] ?: SyncRule(sourceUid, hasConfirmedFixplatz = true, defaultState = AttendanceState.CHOOSE_TO_ATTEND)
        }
        val nextRules = RuleLedgerEditor.apply(
                RuleLedgerEditor.apply(currentLedger.rules.filterNot { it.sourceUid in sourceUids } + ensuredRules, command),
                RuleEditCommand.InstancesBySource(sourceUids, selected.groupBy { it.sourceUid }.mapValues { (_, items) -> items.map { it.instanceKey }.toSet() }, attendance),
            )
        return SourceAttendanceRuleResult.Applied(
            rules = currentLedger.copy(
                rules = nextRules,
                groupRules = GroupAttendanceRuleEditor.apply(
                    currentLedger.groupRules,
                    selected.map(SourceCalendarOccurrence::title).toSet(),
                    command,
                    anchors.groupBy(SourceCalendarOccurrence::title).mapValues { (_, items) -> items.minOf(SourceCalendarOccurrence::startsAt) },
                ),
            ),
            sourceUids = sourceUids,
            favoriteAction = favoriteAction,
            affectedOccurrences = selected.map { it.identity }.toSet(),
        )
    }
}
