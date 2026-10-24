/*
 * Aufgabe: Quelländerungen darstellen: aufklappbare Karte „Gegenüber der letzten Lesung“ in der Kursauswahl,
 *   Kurzbeschriftungen der Änderungsarten und einzelne Änderungszeilen (auch für die Unterschiedsseite).
 * Ein-/Ausgabe: SourceChangeSummary und Auswahl -> Karte, Zähltexte und Zeilen; Klick öffnet die Unterschiedsseite.
 * Nebenwirkungen: Keine; nur Darstellung und Navigationsaufruf.
 * Nicht zuständig: Vergleich (SourceChangeSummary), Auswahländerung, Regeln oder Ausgabe.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.joinMessages
import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.sync.SourceChangeKind
import com.tequila.tumcalendarcontrol.sync.SourceChangeSummary
import com.tequila.tumcalendarcontrol.sync.SourceOccurrenceChange
import com.tequila.tumcalendarcontrol.ui.design.DesignDisclosureRow
import com.tequila.tumcalendarcontrol.ui.design.DesignExpandable
import com.tequila.tumcalendarcontrol.ui.design.DesignRow
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors

private val TextInset = Modifier.padding(horizontal = 4.dp)

/** Reihenfolge der Zähler: zuerst, was Termine wegnimmt oder verschiebt. */
private val CountOrder = listOf(
    SourceChangeKind.CANCELLED, SourceChangeKind.REMOVED, SourceChangeKind.TIME, SourceChangeKind.LOCATION,
    SourceChangeKind.ADDED, SourceChangeKind.RESTORED, SourceChangeKind.TITLE,
)

internal fun SourceChangeKind.label(): String = when (this) {
    SourceChangeKind.ADDED -> message(758)
    SourceChangeKind.REMOVED -> message(759)
    SourceChangeKind.CANCELLED -> message(760)
    SourceChangeKind.RESTORED -> message(761)
    SourceChangeKind.TIME -> message(762)
    SourceChangeKind.LOCATION -> message(763)
    SourceChangeKind.TITLE -> message(764)
}

/** Kurzbeschriftung je Termin, z. B. „Zeit geändert · Ort geändert“; leer, wenn unverändert. */
internal fun Set<SourceChangeKind>.badgeText(): String =
    if (isEmpty()) "" else joinMessages(CountOrder.filter { it in this }.map { it.label() }, " · ")

/** Zähler nur für vorkommende Arten, z. B. „Abgesagt 1 · Zeit geändert 2“. */
internal fun SourceChangeSummary.countsText(): String =
    joinMessages(CountOrder.map { it to count(it) }.filter { it.second > 0 }.map { (kind, n) -> message(765, kind.label(), n) }, " · ")

/** Anzahl betroffener Kurse (Anzeigeschlüssel wie in der Kursliste; nur zur Beschreibung, nie zur Identität). */
internal fun SourceChangeSummary.courseCount(): Int = changes.map { SourceBulkSelection.keyOf(it.current) }.distinct().size

/**
 * Karte über der Kursliste, solange eine vorherige Lesung existiert und die neueste Version aktiv ist: zugeklappt eine
 * Zeile mit Gesamtzahl; aufgeklappt Zähler und Link zur Unterschiedsseite. Betroffene gewählte Termine immer sichtbar.
 */
@Composable
internal fun SourceChangeCard(state: CalendarControlState) {
    if (!state.hasPreviousSourceSnapshot || state.activeSourceSnapshotSlot != SourceSnapshotSlot.LATEST) return
    val summary = state.sourceChanges
    var open by rememberSaveable { mutableStateOf(false) }
    val affected = summary.affecting(state.selectedSourceOccurrences)
    Column(Modifier.fillMaxWidth().testTag("source-changes")) {
        DesignDisclosureRow(
            tr(message(755)), open, Modifier.testTag("source-changes-toggle"),
            detail = tr(if (summary.isEmpty) message(757) else message(776, summary.changes.size, summary.courseCount())),
        ) { open = !open }
        if (affected > 0) DesignText(tr(message(766, affected)), 12, TextInset.padding(top = 4.dp).testTag("source-changes-affected"), color = LocalDesignColors.current.accent)
        DesignExpandable(open) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!summary.isEmpty) DesignText(tr(summary.countsText()), 12, TextInset, muted = true)
                DesignRow(tr(message(767)), tag = "source-changes-open", inset = 4.dp, onClick = state::openSourceChanges)
            }
        }
    }
}

/** Eine Änderungszeile: (optional) Kursname, Zeit, Arten in Akzentfarbe und – soweit vorhanden – der vorherige Wert. */
@Composable
internal fun SourceChangeItem(change: SourceOccurrenceChange, modifier: Modifier = Modifier, showTitle: Boolean = true) {
    val colors = LocalDesignColors.current
    val locale = java.util.Locale.forLanguageTag(LocalAppLanguage.current.language.tag)
    val pattern = tr(message(628))
    val removing = SourceChangeKind.CANCELLED in change.kinds || SourceChangeKind.REMOVED in change.kinds
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("source-change-item"), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (showTitle) DesignText(tr(change.current.title), 14, maxLines = 2)
        DesignText(tr(change.current.toOccurrenceText(pattern, locale)), if (showTitle) 12 else 14, muted = showTitle)
        DesignText(tr(change.kinds.badgeText()), 11, color = if (removing) colors.danger else colors.accent, weight = FontWeight.Medium)
        val before = change.before
        val after = change.after
        if (before != null && after != null) {
            if (SourceChangeKind.TIME in change.kinds) DesignText(tr(message(770, before.toOccurrenceText(pattern, locale))), 11, muted = true)
            if (SourceChangeKind.LOCATION in change.kinds) {
                DesignText(tr(message(771, before.location.ifBlank { message(772) }, after.location.ifBlank { message(772) })), 11, muted = true)
            }
            if (SourceChangeKind.TITLE in change.kinds) DesignText(tr(message(770, before.title)), 11, muted = true)
        }
    }
}
