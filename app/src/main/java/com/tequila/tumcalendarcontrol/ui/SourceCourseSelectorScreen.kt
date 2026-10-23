/*
 * Aufgabe: Kursauswahl darstellen: Steuerung (Suche, Filter, Schnellauswahl, Ansicht), Kurs- oder Datumsliste
 *   und schwebende Hauptaktion; Faltbildschirm zweispaltig.
 * Ein-/Ausgabe: Aktive Momentaufnahme und Browserbedingungen -> genaue Auswahl, Schnellauswahl oder Weiter zur Teilnahme.
 * Nebenwirkungen: Keine externen Aktionen; nur bestehende Zustandsaufrufe.
 * Nicht zuständig: Synchronisierungsidentität anhand von Titeln, Änderung der Parsergebnisse oder Ersatz der abschließenden Schreibbestätigung.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.sync.SourceOutputDateWindow
import com.tequila.tumcalendarcontrol.ui.design.DesignActionFrame
import com.tequila.tumcalendarcontrol.ui.design.DesignExpandable
import com.tequila.tumcalendarcontrol.ui.design.DesignDisclosureRow
import com.tequila.tumcalendarcontrol.ui.design.DesignField
import com.tequila.tumcalendarcontrol.ui.design.DesignPillButton
import com.tequila.tumcalendarcontrol.ui.design.DesignRow
import com.tequila.tumcalendarcontrol.ui.design.DesignSection
import com.tequila.tumcalendarcontrol.ui.design.DesignSegmented
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.FloatingActionContentClearance
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors
import java.time.ZoneId

private val ExpandedSelectorBreakpoint = 720.dp
private val CourseBrowserZone = ZoneId.of("Europe/Berlin")

/** Freier Text auf der Titellinie (4 dp innerhalb der Blockkanten). */
private val TextInset = Modifier.padding(horizontal = 4.dp)

/** Kurze Segmentbeschriftungen; lange Varianten würden im Deutschen gekürzt. */
private enum class SourceBrowserMode(val label: String) { GROUPS(message(717)), DATES(message(718)) }

/** Seitenzustand: Ansicht, aufgeklappte Kurse (zeilengetrennte Schlüssel) und „aktueller Kurs“ der Schnellauswahl; alles speicherbar. */
private class SelectorUi(mode: MutableState<String>, expanded: MutableState<String>, current: MutableState<String>, quick: MutableState<Boolean>) {
    var mode by mode
    var expanded by expanded
    var current by current
    var quick by quick
}

@Composable
fun SourceCourseSelectorScreen(state: CalendarControlState) {
    // Aktive Daten wurden beim Lesen begrenzt; nächster Leseentwurf darf alte Momentaufnahme nicht erneut kürzen.
    val outputWindow = SourceOutputDateWindow()
    val groups = SourceCourseBrowserModel.groups(
        occurrences = state.currentSourceOccurrences,
        selectedOccurrences = state.selectedSourceOccurrences,
        query = state.sourceBrowserQuery,
        filter = state.sourceBrowserFilter,
        outputWindow = outputWindow,
        zoneId = CourseBrowserZone,
    )
    val ui = SelectorUi(
        rememberSaveable { mutableStateOf(SourceBrowserMode.GROUPS.name) },
        // Anfangs alles zugeklappt: schneller Überblick und Auswahl auch bei vielen Kursen (Rückmeldung vom Gerät).
        rememberSaveable { mutableStateOf("") },
        rememberSaveable { mutableStateOf("") },
        rememberSaveable { mutableStateOf(false) },
    )
    val dates = rememberSourceDateBrowser(state, outputWindow)
    val expandedKeys = ui.expanded.split("\n").filter(String::isNotEmpty).toSet()
    val list: LazyListScope.() -> Unit = {
        if (SourceBrowserMode.valueOf(ui.mode) == SourceBrowserMode.GROUPS) {
            sourceGroupItems(
                state, groups, expandedKeys,
                onExpand = { key -> ui.expanded = (if (key in expandedKeys) expandedKeys - key else expandedKeys + key).joinToString("\n") },
                onCurrent = { ui.current = it },
            )
        } else {
            sourceDateItems(state, dates, onCurrent = { ui.current = it })
        }
    }
    val hasSelection = state.selectedSourceOccurrences.isNotEmpty()
    val next = sourceNextStep(state)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= ExpandedSelectorBreakpoint) {
            // Breit: links Steuerung mit schwebender Aktion, rechts unabhängig scrollende Kurse/Termine.
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(0.4f).fillMaxHeight()) {
                    DesignActionFrame(
                        next.label, next.onAction, actionEnabled = hasSelection, actionTag = "source-next",
                        secondaryLabel = next.secondaryLabel, onSecondary = state::openSourceAttendanceEditor, secondaryTag = "source-attendance-again",
                    ) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp).padding(bottom = FloatingActionContentClearance),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) { SourceSelectorControls(state, ui) }
                    }
                }
                Box(Modifier.padding(top = 12.dp, bottom = 24.dp).width(1.dp).fillMaxHeight().background(LocalDesignColors.current.ink.copy(alpha = .10f)))
                LazyColumn(
                    Modifier.weight(0.6f).fillMaxHeight().padding(start = 16.dp, end = 8.dp).testTag("source-list-viewport"),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    content = list,
                )
            }
        } else {
            // Schmal: eine Liste mit Steuerung oben; Aktion schwebt über dem Listenende.
            DesignActionFrame(
                        next.label, next.onAction, actionEnabled = hasSelection, actionTag = "source-next",
                        secondaryLabel = next.secondaryLabel, onSecondary = state::openSourceAttendanceEditor, secondaryTag = "source-attendance-again",
                    ) {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 8.dp).testTag("source-selection-list"),
                    contentPadding = PaddingValues(bottom = FloatingActionContentClearance),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item(key = "controls") { Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { SourceSelectorControls(state, ui) } }
                    list()
                }
            }
        }
    }
}

/**
 * Steuerung: Datenstand mit Kurs-/Terminzahl, Suche, Anzeigefilter, Ansicht; Schnellauswahl in einer Schublade
 * (zugeklappt, damit die Kursliste schnell sichtbar ist). Beide Schnellaktionen sind umkehrbar: „alle“ ⇄ „Auswahl
 * aufheben“, „ganzer Kurs“ ⇄ „Kursauswahl aufheben“; beide unabhängig von Suche und Filter.
 */
@Composable
private fun SourceSelectorControls(state: CalendarControlState, ui: SelectorUi) {
    val occurrences = state.currentSourceOccurrences
    val selection = state.selectedSourceOccurrences
    val unfiltered = SourceBulkSelection.unfilteredGroups(occurrences, selection)
    val all = SourceBulkSelection.allSelectable(occurrences)
    val current = SourceBulkSelection.currentGroup(unfiltered, ui.current)
    val allSelected = all.isNotEmpty() && selection.containsAll(all)
    DesignSection(tr(state.sourceSnapshotLabel), TextInset)
    DesignText(tr(message(693, unfiltered.size, all.size)), 19, TextInset)
    DesignField("", state.sourceBrowserQuery, state::updateSourceBrowserQuery, Modifier.testTag("source-search"), placeholder = tr(message(616)), search = true)
    SourceFilterTabs(state.sourceBrowserFilter, state::updateSourceBrowserFilter)
    val mode = SourceBrowserMode.valueOf(ui.mode)
    DesignSegmented(SourceBrowserMode.entries.map { tr(it.label) }, mode.ordinal, Modifier.testTag("source-mode")) { ui.mode = SourceBrowserMode.entries[it].name }
    Column {
        DesignDisclosureRow(tr(message(704)), ui.quick, Modifier.testTag("source-quick"), detail = current?.let { tr(message(707, it.displayTitle)) } ?: "") { ui.quick = !ui.quick }
        DesignExpandable(ui.quick) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DesignPillButton(
                    tr(if (allSelected) message(746, all.size) else message(705, all.size)), Modifier.testTag("source-select-all"), primary = false, enabled = all.isNotEmpty(),
                ) { if (allSelected) state.clearSourceCourseSelection() else state.selectAllSourceOccurrences() }
                if (current != null) {
                    val courseIds = SourceBulkSelection.courseAdditions(current)
                    val courseSelected = selection.containsAll(courseIds)
                    DesignPillButton(
                        tr(if (courseSelected) message(747, courseIds.size) else message(706, courseIds.size)), Modifier.testTag("source-select-course"), primary = false,
                    ) { if (courseSelected) state.excludeSourceOccurrences(courseIds) else state.includeSourceOccurrences(courseIds) }
                    DesignText(tr(message(707, current.displayTitle)), 12, TextInset.testTag("source-current-course"), muted = true)
                }
                DesignText(tr(message(708)), 11, TextInset, muted = true)
                DesignText(tr(message(615)), 11, TextInset, muted = true)
            }
        }
    }
    // Leeren und Schreibvorschau nur mit Auswahl; beides ändert keine Kalender.
    if (selection.isNotEmpty()) {
        Column {
            DesignRow(tr(message(624)), detail = tr(message(710)), value = tr(message(623, selection.size)), tag = "source-clear", inset = 4.dp, onClick = state::clearSourceCourseSelection)
            // Vor dem Festlegen der Teilnahme direkt zur Ausgabe; danach übernimmt die schwebende Hauptaktion diesen Schritt.
            if (!sourceAttendanceSet(state)) DesignRow(tr(message(626)), detail = tr(message(627)), tag = "source-write-preview", inset = 4.dp, onClick = state::prepareCalendarWrite)
        }
    }
}

/** Hauptaktion der Auswahlseite; secondaryLabel != null blendet links „Teilnahme & Favoriten“ ein. */
private class SourceNextStep(val label: String, val onAction: () -> Unit, val secondaryLabel: String?)

/** Für jede gewählte Quelle liegt eine gespeicherte Teilnahmeregel vor (nach „Speichern“ auf der Teilnahmeseite). */
private fun sourceAttendanceSet(state: CalendarControlState): Boolean {
    val selectedUids = state.selectedSourceOccurrences.map { it.sourceUid }.toSet()
    return selectedUids.isNotEmpty() && state.ruleLedger.rules.map { it.sourceUid }.toSet().containsAll(selectedUids)
}

/**
 * Logische Reihenfolge: zuerst Teilnahme festlegen; ist sie für die Auswahl gespeichert, wird „Ausgabe prüfen“ zur
 * Hauptaktion und Teilnahme rückt als kleinere Nebenaktion nach links (statt erneut „Weiter · Teilnahme“).
 */
@Composable
private fun sourceNextStep(state: CalendarControlState): SourceNextStep {
    val count = state.selectedSourceOccurrences.size
    return when {
        count == 0 -> SourceNextStep(tr(message(709)), state::openSourceAttendanceEditor, null)
        sourceAttendanceSet(state) -> SourceNextStep(tr(message(750, count)), state::prepareCalendarWrite, tr(message(751)))
        else -> SourceNextStep(tr(message(748, count)), state::openSourceAttendanceEditor, null)
    }
}
