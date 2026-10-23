/*
 * Aufgabe: Listenbausteine der Kursauswahl: Kursgruppen mit aufklappbaren Terminen und Anzeigefilter-Reiter.
 * Ein-/Ausgabe: Anzeigegruppen, Auswahl und Rückrufe -> LazyColumn-Einträge bzw. Reiterzeile.
 * Nebenwirkungen: Keine; Auswahl und „aktueller Kurs“ werden über Rückrufe gemeldet.
 * Nicht zuständig: Gruppierung (SourceCourseBrowserModel), Schnellauswahl-Berechnung oder Seitenlayout.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import com.tequila.tumcalendarcontrol.ui.design.hapticClickable
import com.tequila.tumcalendarcontrol.ui.design.animatedSelectionColor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.ui.design.DesignLine
import com.tequila.tumcalendarcontrol.ui.design.DesignMotion
import com.tequila.tumcalendarcontrol.ui.design.DesignNote
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors
import top.yukonga.miuix.kmp.basic.Icon

/**
 * Kursgruppen als Verzeichnis: Kopfzeile klappt Termine auf; Aufklappen oder Antippen eines Termins macht die Gruppe
 * zum „aktuellen Kurs“ der Schnellauswahl. Aufklappzustand liegt beim Aufrufer (gespeichert über Gruppenschlüssel).
 */
internal fun LazyListScope.sourceGroupItems(
    state: CalendarControlState,
    groups: List<SourceCourseDisplayGroup>,
    expandedKeys: Set<String>,
    onExpand: (String) -> Unit,
    onCurrent: (String) -> Unit,
) {
    if (groups.isEmpty()) item { DesignNote(tr(message(617)), tr(message(618)), Modifier.padding(top = 8.dp)) }
    groups.forEach { group ->
        val key = SourceBulkSelection.keyOf(group)
        val expanded = key in expandedKeys
        item(key = "group:$key") {
            SourceGroupHeader(group, expanded, Modifier.animateItem()) { onExpand(key); onCurrent(key) }
        }
        // Aufgeklappt: Kennzahlen, dann Termine; Einträge blenden ein und schieben die Liste weich (animateItem).
        if (expanded) {
            item(key = "info:$key") {
                Column(Modifier.animateItem().padding(start = 16.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    DesignText(tr(message(619, group.sourceUidCount, group.totalOccurrenceCount, group.selectedOccurrenceCount)), 11, muted = true)
                    DesignText(tr(message(620, group.inOutputRangeOccurrenceCount)), 11, muted = true)
                }
            }
            group.occurrences.forEach { occurrence ->
                item(key = "occ:${occurrence.itemKey()}") {
                    SourceOccurrenceChoiceRow(
                        occurrence = occurrence,
                        selected = occurrence.identity in state.selectedSourceOccurrences,
                        showTitle = false,
                        modifier = Modifier.animateItem().padding(start = 16.dp),
                        onToggle = { onCurrent(key); state.toggleSourceOccurrenceSelection(occurrence.identity) },
                    )
                }
            }
        }
        item(key = "line:$key") { DesignLine(Modifier.animateItem()) }
    }
}

/**
 * Kompakte Kopfzeile (eine Zeile, damit viele Kurse auf einen Blick passen): Kursname, rechts „gewählt x/y“ und Pfeil;
 * Pfeil geschlossen nach links, offen 90° gegen den Uhrzeigersinn nach unten. Druck als Schwebeblock.
 */
@Composable
private fun SourceGroupHeader(group: SourceCourseDisplayGroup, expanded: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalDesignColors.current
    val rotation by animateFloatAsState(if (expanded) -90f else 0f, DesignMotion.enter(), label = "source-group-arrow")
    val anySelected = group.selectedOccurrenceCount > 0
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).hapticClickable(onClick = onClick).padding(start = 4.dp, top = 10.dp, bottom = 10.dp).testTag("source-group"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DesignText(tr(group.displayTitle), 16, Modifier.weight(1f), serif = true, maxLines = 2)
        DesignText(
            tr(message(749, group.selectedOccurrenceCount, group.totalOccurrenceCount)), 11, Modifier.padding(start = 12.dp),
            color = if (anySelected) colors.accent else colors.muted, weight = if (anySelected) FontWeight.Medium else FontWeight.Normal,
        )
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = tr(if (expanded) message(621) else message(622)),
            tint = colors.muted, modifier = Modifier.padding(horizontal = 6.dp).size(20.dp).rotate(rotation),
        )
    }
}

/** Anzeigefilter als Textreiter (wie Lehrtyp-Reiter der Übersicht): ändert nur die Anzeige, nie die Auswahl. */
@Composable
internal fun SourceFilterTabs(selected: SourceCourseFilter, onSelect: (SourceCourseFilter) -> Unit) {
    val colors = LocalDesignColors.current
    Row(Modifier.fillMaxWidth().padding(start = 4.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        SourceCourseFilter.entries.forEach { filter ->
            val active = filter == selected
            Column(
                Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).hapticClickable { onSelect(filter) }.testTag("source-filter-${filter.name}"),
                verticalArrangement = Arrangement.Center,
            ) {
                val ink by animatedSelectionColor(active, colors.accent, colors.muted, "filter-${filter.name}")
                val line by animatedSelectionColor(active, colors.accent, Color.Transparent, "filter-line-${filter.name}")
                DesignText(tr(filter.label), 13, color = ink, weight = if (active) FontWeight.Medium else FontWeight.Normal)
                Box(Modifier.padding(top = 4.dp).width(32.dp).height(2.dp).background(line))
            }
        }
    }
}

/** Gemeinsamer Schlüssel für Termine in LazyColumn-Einträgen (Quell-UID und Instanz). */
internal fun SourceCalendarOccurrence.itemKey(): String = "$sourceUid|$instanceKey"
