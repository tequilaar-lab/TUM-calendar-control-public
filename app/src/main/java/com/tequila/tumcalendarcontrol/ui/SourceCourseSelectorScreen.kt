/*
 * Aufgabe: Gruppierte/datumsbezogene Terminauswahl mit responsiver Faltbildschirmansicht darstellen.
 * Ein-/Ausgabe: Aktive Momentaufnahme und Browserbedingungen -> genaue Auswahl oder Vorschauabsicht.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Synchronisierungsidentität anhand von Titeln, Änderung der Parsergebnisse oder Ersatz der abschließenden Schreibbestätigung.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.tequila.tumcalendarcontrol.sync.SourceOutputDateWindow
import java.time.ZoneId
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val SourceSelectionBarShape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
private val ExpandedSelectorBreakpoint = 720.dp
private val CourseBrowserZone = ZoneId.of("Europe/Berlin")

private enum class SourceBrowserMode(val label: String) { GROUPS(message(613)), DATES(message(596)) }

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
    var mode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(SourceBrowserMode.GROUPS) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= ExpandedSelectorBreakpoint) {
            ExpandedSourceCourseSelector(state, outputWindow, groups, mode, onModeChange = { mode = it })
        } else {
            CompactSourceCourseSelector(state, outputWindow, groups, mode, onModeChange = { mode = it })
        }
    }
}

/** Breite Ansicht: links Steuerung, rechts vollständige unabhängig scrollbare Kurse/Termine. */
@Composable
private fun ExpandedSourceCourseSelector(
    state: CalendarControlState,
    outputWindow: SourceOutputDateWindow,
    groups: List<SourceCourseDisplayGroup>,
    mode: SourceBrowserMode,
    onModeChange: (SourceBrowserMode) -> Unit,
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(0.39f).fillMaxHeight()) {
            var floatingHeight by remember { mutableStateOf(184.dp) }
            val density = LocalDensity.current
            SourceSelectorControls(
                state = state,
                mode = mode,
                onModeChange = onModeChange,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp, 12.dp, 12.dp, floatingHeight + 12.dp),
            )
            SourceSelectionBottomBar(
                selectedCount = state.selectedSourceOccurrences.size,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { floatingHeight = with(density) { it.height.toDp() } }.padding(12.dp),
                onClear = state::clearSourceCourseSelection,
                onAttendance = state::openSourceAttendanceEditor,
                onWrite = state::prepareCalendarWrite,
            )
        }
        Box(Modifier.weight(0.61f).fillMaxHeight().padding(end = 12.dp).testTag("source-list-viewport")) {
            SourceCourseSelectionList(state, outputWindow, groups, mode, Modifier.fillMaxSize())
        }
    }
}

/** Schmale Ansicht einspaltig; Liste unabhängig scrollen, Aktionskarte schwebt nur unten. */
@Composable
private fun CompactSourceCourseSelector(
    state: CalendarControlState,
    outputWindow: SourceOutputDateWindow,
    groups: List<SourceCourseDisplayGroup>,
    mode: SourceBrowserMode,
    onModeChange: (SourceBrowserMode) -> Unit,
) {
    var floatingHeight by remember { mutableStateOf(184.dp) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceSelectorControls(state, mode, onModeChange, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))
            SourceCourseSelectionList(state, outputWindow, groups, mode, Modifier.weight(1f).padding(horizontal = 12.dp), floatingHeight + 12.dp)
        }
        SourceSelectionBottomBar(
            selectedCount = state.selectedSourceOccurrences.size,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().widthIn(max = 560.dp).onSizeChanged { floatingHeight = with(density) { it.height.toDp() } }.padding(12.dp),
            onClear = state::clearSourceCourseSelection,
            onAttendance = state::openSourceAttendanceEditor,
            onWrite = state::prepareCalendarWrite,
        )
    }
}

@Composable
private fun SourceSelectorControls(
    state: CalendarControlState,
    mode: SourceBrowserMode,
    onModeChange: (SourceBrowserMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InformationCard(message(614), message(615))
        TextField(
            value = state.sourceBrowserQuery,
            onValueChange = state::updateSourceBrowserQuery,
            modifier = Modifier.fillMaxWidth(),
            label = tr(message(616)),
        )
        SourceFilterRow(state)
        SourceModeRow(mode = mode, onChange = onModeChange)
    }
}

@Composable
private fun SourceCourseSelectionList(
    state: CalendarControlState,
    outputWindow: SourceOutputDateWindow,
    groups: List<SourceCourseDisplayGroup>,
    mode: SourceBrowserMode,
    modifier: Modifier,
    bottomClearance: Dp = 16.dp,
) {
    when (mode) {
        SourceBrowserMode.GROUPS -> SourceGroupList(state, groups, modifier, bottomClearance)
        SourceBrowserMode.DATES -> SourceCourseDateBrowser(state, outputWindow, modifier, bottomClearance)
    }
}

@Composable
private fun SourceFilterRow(state: CalendarControlState) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        SourceCourseFilter.entries.forEach { filter ->
            val selected = state.sourceBrowserFilter == filter
            Button(onClick = { state.updateSourceBrowserFilter(filter) }, modifier = Modifier.weight(1f)) {
                Text(tr(if (selected) message(202, (filter.label)) else filter.label))
            }
        }
    }
}

@Composable
private fun SourceModeRow(mode: SourceBrowserMode, onChange: (SourceBrowserMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        SourceBrowserMode.entries.forEach { item ->
            Button(onClick = { onChange(item) }, modifier = Modifier.weight(1f)) {
                Text(tr(if (item == mode) message(202, (item.label)) else item.label))
            }
        }
    }
}

@Composable
private fun SourceGroupList(state: CalendarControlState, groups: List<SourceCourseDisplayGroup>, modifier: Modifier, bottomClearance: Dp) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = bottomClearance),
    ) {
        if (groups.isEmpty()) item { InformationCard(message(617), message(618)) }
        items(groups, key = { it.displayTitle + it.earliestStart }) { group -> SourceGroupRow(state, group) }
    }
}

@Composable
private fun SourceGroupRow(state: CalendarControlState, group: SourceCourseDisplayGroup) {
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(group.displayTitle) { mutableStateOf(group.selectedOccurrenceCount > 0) }
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr(group.displayTitle), style = MiuixTheme.textStyles.title3)
                    Text(
                        tr(message(619, (group.sourceUidCount), (group.totalOccurrenceCount), (group.selectedOccurrenceCount))),
                        color = LocalTumPalette.current.onSurfaceVariant,
                    )
                    Text(
                        tr(message(620, (group.inOutputRangeOccurrenceCount))),
                        color = LocalTumPalette.current.onSurfaceVariant,
                    )
                }
                Text(tr(if (expanded) message(621) else message(622)))
            }
            if (expanded) group.occurrences.forEach { occurrence ->
                SourceOccurrenceChoiceRow(
                    occurrence = occurrence,
                    selected = occurrence.identity in state.selectedSourceOccurrences,
                    onToggle = { state.toggleSourceOccurrenceSelection(occurrence.identity) },
                )
            }
        }
    }
}

/** Schwebende Karte umfasst nur Aktionen; färbt weder Seitenhintergrund noch rechte Liste. */
@Composable
private fun SourceSelectionBottomBar(
    selectedCount: Int,
    modifier: Modifier = Modifier,
    onClear: () -> Unit,
    onAttendance: () -> Unit,
    onWrite: () -> Unit,
) {
    val palette = LocalTumPalette.current
    Card(
        modifier = modifier
            .shadow(elevation = 16.dp, shape = SourceSelectionBarShape, clip = false)
            .clip(SourceSelectionBarShape)
            .background(palette.surfaceHighest)
            .border(width = 1.dp, color = palette.outline.copy(alpha = 0.52f), shape = SourceSelectionBarShape),
        insideMargin = PaddingValues(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr(message(623, (selectedCount))), style = MiuixTheme.textStyles.title3)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onClear, enabled = selectedCount > 0, modifier = Modifier.weight(1f)) { Text(tr(message(624))) }
                Button(onClick = onAttendance, enabled = selectedCount > 0, modifier = Modifier.weight(1f)) { Text(tr(message(625))) }
            }
            Button(onClick = onWrite, enabled = selectedCount > 0, modifier = Modifier.fillMaxWidth()) { Text(tr(message(626))) }
            Text(tr(message(627)), color = palette.onSurfaceVariant)
        }
    }
}
