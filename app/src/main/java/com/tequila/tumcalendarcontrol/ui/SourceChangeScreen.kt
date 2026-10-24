/*
 * Aufgabe: Unterschiedsseite (nur lesen): alle Änderungen der neuesten gegenüber der vorherigen Lesung, nach Kurs gruppiert.
 * Ein-/Ausgabe: SourceChangeSummary und Auswahl -> Zähler, Hinweis auf betroffene Auswahl, Änderungszeilen.
 * Nebenwirkungen: Keine; weder Auswahl, Regeln, Momentaufnahmen noch Kalender werden verändert.
 * Nicht zuständig: Vergleich selbst, Wechsel der Datenversion oder erneutes Lesen.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.ui.design.DesignLine
import com.tequila.tumcalendarcontrol.ui.design.DesignNote
import com.tequila.tumcalendarcontrol.ui.design.DesignPage
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors

private val TextInset = Modifier.padding(horizontal = 4.dp)

@Composable
fun SourceChangeScreen(state: CalendarControlState) {
    DesignPage(contentTag = "source-changes-content") {
        if (!state.hasPreviousSourceSnapshot) {
            DesignNote(tr(message(768)), tr(message(775)))
            return@DesignPage
        }
        val summary = state.sourceVersionChanges
        DesignText(tr(if (summary.isEmpty) message(757) else message(776, summary.changes.size, summary.courseCount())), 20, TextInset)
        if (!summary.isEmpty) DesignText(tr(summary.countsText()), 12, TextInset, muted = true)
        val affected = summary.affecting(state.selectedSourceOccurrences)
        if (affected > 0) DesignText(tr(message(766, affected)), 12, TextInset, color = LocalDesignColors.current.accent)
        // Gruppierung nur zur Lesbarkeit (Anzeigeschlüssel des Kursnamens); Reihenfolge innerhalb nach Beginn.
        summary.changes.groupBy { SourceBulkSelection.keyOf(it.current) }.values.forEach { group ->
            Column {
                DesignText(tr(group.first().current.title), 16, TextInset.padding(top = 12.dp), serif = true)
                group.forEach { change ->
                    SourceChangeItem(change, TextInset, showTitle = false)
                    DesignLine()
                }
            }
        }
    }
}
