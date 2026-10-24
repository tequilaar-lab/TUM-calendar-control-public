/*
 * Aufgabe: Aktionen des CalendarControlState zur Teilnahme: Editor für gelesene Termine, Bestätigung für Kurse und
 *   Einzeltermine, lokale Anzeigeänderungen und Favoriten (aus CalendarControlState ausgelagert, Verhalten unverändert).
 * Ein-/Ausgabe: Auswahl, Bereich und Datum aus dem Zustand -> Regelbefehle, Regelbestand, Kursprojektion, Rückmeldungen.
 * Nebenwirkungen: Nur über commitUserData (private Präferenzen); keine Kalenderausgabe.
 * Nicht zuständig: Regelauflösung (GroupAttendanceRuleResolver, RuleLedgerEditor) oder Darstellung.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.domain.CourseLocalOverride
import com.tequila.tumcalendarcontrol.domain.displayTitle
import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.sync.GroupAttendanceRuleResolver
import com.tequila.tumcalendarcontrol.sync.RuleEditCommand
import com.tequila.tumcalendarcontrol.sync.RuleLedgerEditor
import com.tequila.tumcalendarcontrol.sync.SourceOccurrenceIdentity
import com.tequila.tumcalendarcontrol.sync.SyncRule

fun CalendarControlState.openAttendance() {
    pendingAttendance = selectedCourse.attendance
    pendingAttendanceScope = pageMemory.scope
    pendingEffectiveDate = pageMemory.date
    detailAttendanceBaseline = Triple(pendingAttendance, pendingAttendanceScope, pendingEffectiveDate)
    feedback = null
    open(AppScreen.ATTENDANCE)
}

/** Nur private App-Änderung; Quell-title/schedule/location unverändert. */
fun CalendarControlState.updateSelectedCourseOverride(override: CourseLocalOverride) {
    val nextCourses = courses.map { if (it.id == selectedCourseId) it.copy(localOverride = override) else it }
    if (!commitUserData(nextCourses = nextCourses)) return
    invalidateWritePreview()
    feedback = message(255)
}

/** Neue Termine ohne Regel standardmäßig blau; gleicher Dreizustandseditor für Einzel-/Mehrfachwahl. */
fun CalendarControlState.sourceAttendanceFor(sourceUid: String): AttendanceState = GroupAttendanceRuleResolver
    .materialize(ruleLedger, currentSourceOccurrences)
    .firstOrNull { it.sourceUid == sourceUid }
    ?.defaultState
    ?: AttendanceState.CHOOSE_TO_ATTEND

fun CalendarControlState.openSourceAttendanceEditor(selection: Set<SourceOccurrenceIdentity> = selectedSourceOccurrences) {
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
fun CalendarControlState.confirmSourceAttendance() {
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
internal fun CalendarControlState.confirmIndividualSourceAttendance() {
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

fun CalendarControlState.confirmAttendance() {
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

internal fun CalendarControlState.attendanceCommand(): RuleEditCommand? = when (pendingAttendanceScope) {
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

fun CalendarControlState.toggleFavorite(courseId: String) {
    val nextCourses = courses.map { course ->
        if (course.id == courseId) course.copy(isFavorite = !course.isFavorite) else course
    }
    commitUserData(nextCourses = nextCourses)
}
