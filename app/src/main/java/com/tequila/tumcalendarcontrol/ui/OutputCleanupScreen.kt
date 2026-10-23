/*
 * Aufgabe: Bereinigung nach Gesamtbestand, Kurs oder Datum mit zweiter Bestätigung (Glasdialog) darstellen.
 * Ein-/Ausgabe: Laufzeitquelle, Zuordnungen und Auswahl -> genauer bestätigter Bereinigungsbereich.
 * Nebenwirkungen: Keine externen Aktionen; Löschen erst nach Bestätigung über bestehenden Zustandsaufruf.
 * Nicht zuständig: iCal-Lesen, Quelleninhaltsspeicherung, Titelerkennung, Berechtigungen oder direktes Löschen von Kalenderterminen.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import android.app.DatePickerDialog
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.R
import com.tequila.tumcalendarcontrol.sync.ManagedEventCleanupScope
import com.tequila.tumcalendarcontrol.sync.OutputTarget
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.ui.design.DesignActionPage
import com.tequila.tumcalendarcontrol.ui.design.DesignCheck
import com.tequila.tumcalendarcontrol.ui.design.DesignChoice
import com.tequila.tumcalendarcontrol.ui.design.DesignConfirmDialog
import com.tequila.tumcalendarcontrol.ui.design.DesignConfirmRequest
import com.tequila.tumcalendarcontrol.ui.design.DesignLine
import com.tequila.tumcalendarcontrol.ui.design.DesignNote
import com.tequila.tumcalendarcontrol.ui.design.DesignPillButton
import com.tequila.tumcalendarcontrol.ui.design.DesignRow
import com.tequila.tumcalendarcontrol.ui.design.DesignSection
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import java.time.LocalDate
import java.time.ZoneId

private val CleanupBerlinZone = ZoneId.of("Europe/Berlin")

/** Freier Text auf der Titellinie (4 dp innerhalb der Blockkanten). */
private val TextInset = Modifier.padding(horizontal = 4.dp)

private enum class OutputCleanupMode(val label: String, val description: String) {
    ALL(message(440), message(441)),
    COURSES(message(442), message(443)),
    DATES(message(444), message(445)),
}

private data class CleanupCourseGroup(
    val label: String,
    val sourceUids: Set<String>,
    val occurrenceCount: Int,
)

/**
 * Ziele wählen und (nur lesend) prüfen, Umfang wählen, Treffer zählen; „Weiter“ öffnet die zweite Bestätigung.
 * Seitenzustand bleibt in pageMemory, damit Rückkehr dieselbe Auswahl zeigt.
 */
@Composable
internal fun OutputCleanupScreen(state: CalendarControlState) {
    var mode by remember { mutableStateOf(runCatching { OutputCleanupMode.valueOf(state.pageMemory.cleanupMode) }.getOrDefault(OutputCleanupMode.COURSES)) }
    var selectedSourceUids by remember { mutableStateOf(state.pageMemory.cleanupCourses) }
    var startDate by remember { mutableStateOf(runCatching { LocalDate.parse(state.pageMemory.cleanupStart) }.getOrNull()) }
    var endDate by remember { mutableStateOf(runCatching { LocalDate.parse(state.pageMemory.cleanupEnd) }.getOrNull()) }
    var pendingScope by remember { mutableStateOf<ManagedEventCleanupScope?>(null) }
    var localMessage by remember { mutableStateOf<String?>(null) }
    val context = localizedContext()
    val darkTheme = state.themeMode.usesDarkTheme(isSystemInDarkTheme())
    val targets = state.managedOutputTargetsForCleanup
    var selectedTargets by remember(targets) { mutableStateOf(targets.filter { it.name in state.pageMemory.cleanupTargets }.toSet()) }
    LaunchedEffect(mode, selectedSourceUids, startDate, endDate, selectedTargets) {
        state.pageMemory = state.pageMemory.copy(cleanupMode = mode.name, cleanupCourses = selectedSourceUids,
            cleanupStart = startDate?.toString().orEmpty(), cleanupEnd = endDate?.toString().orEmpty(), cleanupTargets = selectedTargets.map { it.name }.toSet())
    }
    val catalogue = state.cleanupOccurrencesForTargets(selectedTargets)
    val sourceLoaded = catalogue.isNotEmpty()
    val selectedCount = when (mode) {
        OutputCleanupMode.ALL -> null
        OutputCleanupMode.COURSES -> state.cleanupIdentities(selectedSourceUids, null, null, selectedTargets).size
        OutputCleanupMode.DATES -> state.cleanupIdentities(null, startDate, endDate, selectedTargets).size
    }
    val canContinue = !state.managedSyncInProgress && state.cleanupCatalogReadTargets.isEmpty() && selectedTargets.isNotEmpty() &&
        (selectedCount == null || selectedCount > 0) && (startDate == null || endDate == null || startDate!! <= endDate!!)

    DesignActionPage(
        tr(message(471)),
        onAction = {
            val scope = when (mode) {
                OutputCleanupMode.ALL -> ManagedEventCleanupScope.AllManagedEvents
                OutputCleanupMode.COURSES -> state.selectedCleanupScope(selectedSourceUids, null, null, selectedTargets)
                OutputCleanupMode.DATES -> state.selectedCleanupScope(null, startDate, endDate, selectedTargets)
            }
            if (scope is ManagedEventCleanupScope.SelectedIdentities && scope.identities.isEmpty()) localMessage = message(470) else pendingScope = scope
        },
        actionEnabled = canContinue, actionTag = "cleanup-continue", contentTag = "cleanup-content",
    ) {
        // Seitentitel nennt die Aufgabe schon; hier nur die Abgrenzung zum normalen Schreiben.
        DesignText(tr(message(446)), 13, TextInset)
        // Ziele: nur angehakte Kalender; Prüfen liest nur App-eigene Einträge und löscht nichts.
        DesignSection(tr(message(447)), TextInset)
        Column {
            targets.forEach { target ->
                DesignCheck(tr(setOf(target).cleanupTargetLabel()), target in selectedTargets, TextInset.testTag("cleanup-target-${target.name}")) {
                    selectedTargets = if (target in selectedTargets) selectedTargets - target else selectedTargets + target
                }
            }
        }
        DesignText(tr(message(448)), 12, TextInset, muted = true)
        DesignPillButton(
            tr(if (state.cleanupCatalogReadTargets.isEmpty()) message(450) else message(451)), Modifier.testTag("cleanup-read-catalog"), primary = false,
            enabled = !state.managedSyncInProgress && state.cleanupCatalogReadTargets.isEmpty(),
        ) { state.requestCleanupCatalogRead(selectedTargets) }
        DesignText(tr(message(453)), 12, TextInset, muted = true)
        DesignLine()
        DesignSection(tr(message(723)), TextInset)
        OutputCleanupMode.entries.forEach { choice ->
            DesignChoice(tr(choice.label), mode == choice, Modifier.testTag("cleanup-mode-${choice.name}"), detail = tr(choice.description)) {
                mode = choice
                localMessage = null
            }
        }
        CleanupModeContent(mode, sourceLoaded, catalogue, selectedSourceUids, { selectedSourceUids = it }, startDate, endDate,
            onPickStart = { showCleanupDatePicker(context, startDate, darkTheme) { startDate = it } },
            onPickEnd = { showCleanupDatePicker(context, endDate, darkTheme) { endDate = it } },
            onClearDates = { startDate = null; endDate = null })
        if (selectedCount != null) DesignNote(tr(message(468)), tr(message(469, selectedCount)), Modifier.testTag("cleanup-count"))
        localMessage?.let { DesignText(tr(it), 12, TextInset, muted = true) }
        state.feedback?.let { DesignText(tr(it), 12, TextInset.testTag("cleanup-feedback"), muted = true) }
    }
    // Zweite Bestätigung als Glasdialog über der ganzen Seite; erst „Bereinigen bestätigen“ löst das Löschen aus.
    DesignConfirmDialog(pendingScope?.let { scope ->
        DesignConfirmRequest(
            kicker = tr(message(719)), title = tr(message(476)),
            body = tr(when (scope) {
                ManagedEventCleanupScope.AllManagedEvents -> message(472)
                is ManagedEventCleanupScope.SelectedIdentities -> message(473, scope.identities.size)
            }) + "\n" + tr(message(477, selectedTargets.cleanupTargetLabel())),
            note = tr(message(478)), cancelLabel = tr(message(197)), confirmLabel = tr(message(479)), danger = true,
            onCancel = { pendingScope = null },
            onConfirm = {
                pendingScope = null
                state.requestManagedRollback(scope, selectedTargets)
            },
        )
    })
}

/** Inhalt je Umfang: Schutzhinweis (alle), Kurse zum Anhaken oder Datumsbereich; ohne geprüften Bestand ein Hinweis. */
@Composable
private fun CleanupModeContent(
    mode: OutputCleanupMode,
    sourceLoaded: Boolean,
    catalogue: List<SourceCalendarOccurrence>,
    selectedSourceUids: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    startDate: LocalDate?,
    endDate: LocalDate?,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onClearDates: () -> Unit,
) {
    when (mode) {
        OutputCleanupMode.ALL -> DesignNote(tr(message(454)), tr(message(455)))
        OutputCleanupMode.COURSES -> if (!sourceLoaded) DesignNote(tr(message(456)), tr(message(457))) else {
            DesignText(tr(message(458)), 12, TextInset, muted = true)
            Column {
                cleanupCourseGroups(catalogue).forEach { group ->
                    val selected = group.sourceUids.all { it in selectedSourceUids }
                    DesignCheck(tr(group.label), selected, TextInset.testTag("cleanup-course"), detail = tr(message(459, group.occurrenceCount))) {
                        onSelectedChange(if (selected) selectedSourceUids - group.sourceUids else selectedSourceUids + group.sourceUids)
                    }
                }
            }
        }
        OutputCleanupMode.DATES -> if (!sourceLoaded) DesignNote(tr(message(461)), tr(message(462))) else {
            DesignText(tr(message(464)), 12, TextInset, muted = true)
            Column {
                DesignRow(tr(message(465)), detail = tr(startDate?.toString() ?: message(474)), value = tr(message(475)), tag = "cleanup-start", inset = 4.dp, onClick = onPickStart)
                DesignRow(tr(message(466)), detail = tr(endDate?.toString() ?: message(474)), value = tr(message(475)), tag = "cleanup-end", inset = 4.dp, onClick = onPickEnd)
                DesignRow(tr(message(467)), tag = "cleanup-clear-dates", inset = 4.dp, onClick = onClearDates)
            }
        }
    }
}

private fun cleanupCourseGroups(occurrences: List<SourceCalendarOccurrence>): List<CleanupCourseGroup> = occurrences
    .asSequence()
    .groupBy { it.title.trim().replace(Regex("\\s+"), " ").ifBlank { message(328) } }
    .map { (label, members) -> CleanupCourseGroup(label, members.map(SourceCalendarOccurrence::sourceUid).toSet(), members.size) }
    .sortedBy(CleanupCourseGroup::label)
    .toList()

private fun Set<OutputTarget>.cleanupTargetLabel(): String = buildList {
    if (OutputTarget.LOCAL_CALENDAR in this@cleanupTargetLabel) add(message(480))
    if (OutputTarget.GOOGLE_CALENDAR in this@cleanupTargetLabel) add(message(481))
}.let { com.tequila.tumcalendarcontrol.i18n.joinMessages(it, "、") }.ifBlank { message(482) }

private fun showCleanupDatePicker(context: android.content.Context, current: LocalDate?, darkTheme: Boolean, onPicked: (LocalDate) -> Unit) {
    val seed = current ?: LocalDate.now(CleanupBerlinZone)
    DatePickerDialog(
        context,
        if (darkTheme) R.style.Theme_TumCalendarControl_DatePicker_Dark else R.style.Theme_TumCalendarControl_DatePicker_Light,
        { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
        seed.year,
        seed.monthValue - 1,
        seed.dayOfMonth,
    ).show()
}
