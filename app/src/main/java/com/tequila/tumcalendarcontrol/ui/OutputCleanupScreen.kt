/*
 * Aufgabe: Bereinigung nach Gesamtbestand, Kurs oder Datum mit zweiter Bestätigung darstellen.
 * Ein-/Ausgabe: Laufzeitquelle, Zuordnungen und Auswahl -> genauer bestätigter Bereinigungsbereich.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: iCal-Lesen, Quelleninhaltsspeicherung, Titelerkennung, Berechtigungen oder direktes Löschen von Kalenderterminen.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.R
import com.tequila.tumcalendarcontrol.sync.ManagedEventCleanupScope
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import java.time.LocalDate
import java.time.ZoneId
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.window.Dialog

private val CleanupBerlinZone = ZoneId.of("Europe/Berlin")

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
    androidx.compose.runtime.LaunchedEffect(mode, selectedSourceUids, startDate, endDate, selectedTargets) {
        state.pageMemory = state.pageMemory.copy(cleanupMode = mode.name, cleanupCourses = selectedSourceUids,
            cleanupStart = startDate?.toString().orEmpty(), cleanupEnd = endDate?.toString().orEmpty(), cleanupTargets = selectedTargets.map { it.name }.toSet())
    }
    val catalogue = state.cleanupOccurrencesForTargets(selectedTargets)
    val sourceLoaded = catalogue.isNotEmpty()
    val courseGroups = cleanupCourseGroups(catalogue)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InformationCard(
            message(167),
            message(446),
        )
        InformationCard(message(447), message(448))
        targets.forEach { target ->
            Button(onClick = { selectedTargets = if (target in selectedTargets) selectedTargets - target else selectedTargets + target }, modifier = Modifier.fillMaxWidth()) {
                Text(tr(message(449, (if (target in selectedTargets) "✓ " else ""), (setOf(target).cleanupTargetLabel()))))
            }
        }
        Button(onClick = { state.requestCleanupCatalogRead(selectedTargets) }, enabled = !state.managedSyncInProgress && state.cleanupCatalogReadTargets.isEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(tr(if (state.cleanupCatalogReadTargets.isEmpty()) message(450) else message(451)))
        }
        InformationCard(message(452), message(453))
        OutputCleanupMode.entries.forEach { choice ->
            CleanupModeRow(mode = choice, selected = mode == choice) {
                mode = choice
                localMessage = null
            }
        }
        when (mode) {
            OutputCleanupMode.ALL -> InformationCard(
                message(454),
                message(455),
            )
            OutputCleanupMode.COURSES -> {
                if (!sourceLoaded) {
                    InformationCard(message(456), message(457))
                } else {
                    InformationCard(message(163), message(458))
                    courseGroups.forEach { group ->
                        val selected = group.sourceUids.all { it in selectedSourceUids }
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedSourceUids = if (selected) selectedSourceUids - group.sourceUids else selectedSourceUids + group.sourceUids
                            },
                            insideMargin = PaddingValues(14.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(tr(group.label), style = MiuixTheme.textStyles.body1)
                                    Text(tr(message(459, (group.occurrenceCount))), color = LocalTumPalette.current.onSurfaceVariant)
                                }
                                Text(
                                    tr(if (selected) message(188) else message(460)),
                                    color = if (selected) {
                                        LocalTumPalette.current.primary
                                    } else {
                                        LocalTumPalette.current.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }
            OutputCleanupMode.DATES -> {
                if (!sourceLoaded) {
                    InformationCard(message(461), message(462))
                } else {
                    InformationCard(message(463), message(464))
                    CleanupDateField(message(465), startDate) {
                        showCleanupDatePicker(context, startDate, darkTheme) { startDate = it }
                    }
                    CleanupDateField(message(466), endDate) {
                        showCleanupDatePicker(context, endDate, darkTheme) { endDate = it }
                    }
                    Button(onClick = { startDate = null; endDate = null }, modifier = Modifier.fillMaxWidth()) { Text(tr(message(467))) }
                }
            }
        }
        val selectedCount = when (mode) {
            OutputCleanupMode.ALL -> null
            OutputCleanupMode.COURSES -> state.cleanupIdentities(selectedSourceUids, null, null, selectedTargets).size
            OutputCleanupMode.DATES -> state.cleanupIdentities(null, startDate, endDate, selectedTargets).size
        }
        if (selectedCount != null) InformationCard(message(468), message(469, (selectedCount)))
        localMessage?.let { Text(tr(it), color = LocalTumPalette.current.onSurfaceVariant) }
        Button(
            onClick = {
                val scope = when (mode) {
                    OutputCleanupMode.ALL -> ManagedEventCleanupScope.AllManagedEvents
                    OutputCleanupMode.COURSES -> state.selectedCleanupScope(selectedSourceUids, null, null, selectedTargets)
                    OutputCleanupMode.DATES -> state.selectedCleanupScope(null, startDate, endDate, selectedTargets)
                }
                if (scope is ManagedEventCleanupScope.SelectedIdentities && scope.identities.isEmpty()) {
                    localMessage = message(470)
                } else {
                    pendingScope = scope
                }
            },
            enabled = !state.managedSyncInProgress && state.cleanupCatalogReadTargets.isEmpty() && selectedTargets.isNotEmpty() && (selectedCount == null || selectedCount > 0) && (startDate == null || endDate == null || startDate!! <= endDate!!),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(tr(message(471))) }
        state.feedback?.let { Text(tr(it), color = LocalTumPalette.current.onSurfaceVariant) }
    }
    pendingScope?.let { scope ->
        CleanupConfirmationDialog(
            summary = when (scope) {
                ManagedEventCleanupScope.AllManagedEvents -> message(472)
                is ManagedEventCleanupScope.SelectedIdentities -> message(473, (scope.identities.size))
            },
            targets = selectedTargets.cleanupTargetLabel(),
            onDismiss = { pendingScope = null },
            onConfirm = {
                pendingScope = null
                state.requestManagedRollback(scope, selectedTargets)
            },
        )
    }
}

@Composable
private fun CleanupModeRow(mode: OutputCleanupMode, selected: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), insideMargin = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr(mode.label), style = MiuixTheme.textStyles.body1)
                Text(tr(mode.description), color = LocalTumPalette.current.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun CleanupDateField(label: String, date: LocalDate?, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), insideMargin = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr(label), style = MiuixTheme.textStyles.body1)
                Text(tr(date?.toString() ?: message(474)), color = LocalTumPalette.current.onSurfaceVariant)
            }
            Text(tr(message(475)))
        }
    }
}

@Composable
private fun CleanupConfirmationDialog(summary: String, targets: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr(message(476)), style = MiuixTheme.textStyles.title2)
                Text(tr(summary))
                Text(tr(message(477, (targets))), color = LocalTumPalette.current.onSurfaceVariant)
                Text(tr(message(478)), color = LocalTumPalette.current.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(tr(message(197))) }
                    Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text(tr(message(479))) }
                }
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

private fun Set<com.tequila.tumcalendarcontrol.sync.OutputTarget>.cleanupTargetLabel(): String = buildList {
    if (com.tequila.tumcalendarcontrol.sync.OutputTarget.LOCAL_CALENDAR in this@cleanupTargetLabel) add(message(480))
    if (com.tequila.tumcalendarcontrol.sync.OutputTarget.GOOGLE_CALENDAR in this@cleanupTargetLabel) add(message(481))
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
