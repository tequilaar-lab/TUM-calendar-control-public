/*
 * Aufgabe: Standardmäßig deaktivierte lesende Hintergrundprüfung und Status bedienen (neues Erscheinungsbild).
 * Ein-/Ausgabe: Prüfeinstellungen und Status -> ausdrücklich betätigte Einstellungs-/Prüfabsichten.
 * Nebenwirkungen: Keine externen Aktionen; Einstellungen über bestehenden Zustandsaufruf.
 * Nicht zuständig: Quelleninhalt, automatische Synchronisierung, Kalenderausgabe, Google-Autorisierung oder Bereichskonfiguration.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.sync.SourceCheckCadence
import com.tequila.tumcalendarcontrol.sync.SourceCheckResult
import com.tequila.tumcalendarcontrol.sync.SourceCheckStatus
import com.tequila.tumcalendarcontrol.ui.design.DesignFrostCard
import com.tequila.tumcalendarcontrol.ui.design.DesignNote
import com.tequila.tumcalendarcontrol.ui.design.DesignPage
import com.tequila.tumcalendarcontrol.ui.design.DesignPillButton
import com.tequila.tumcalendarcontrol.ui.design.DesignSection
import com.tequila.tumcalendarcontrol.ui.design.DesignSegmented
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Freier Text auf der Titellinie (4 dp innerhalb der Blockkanten). */
private val TextInset = Modifier.padding(horizontal = 4.dp)

/** Nur lesende Prüfung: Häufigkeit, Ein/Aus, letzter Stand, Hinweis zu Benachrichtigungen; schreibt nie in Kalender. */
@Composable
fun BackgroundCheckScreen(state: CalendarControlState) {
    val settings = state.sourceCheckSettings
    DesignPage(contentTag = "background-content") {
        DesignText(tr(message(200)), 13, TextInset)
        DesignSection(tr(message(201)), TextInset)
        DesignSegmented(SourceCheckCadence.entries.map { tr(it.label) }, settings.cadence.ordinal, Modifier.testTag("background-cadence")) {
            state.updateSourceCheck(settings.enabled, SourceCheckCadence.entries[it])
        }
        DesignPillButton(tr(if (settings.enabled) message(203) else message(204)), Modifier.testTag("background-toggle"), primary = !settings.enabled) {
            state.updateSourceCheck(!settings.enabled)
        }
        DesignFrostCard(Modifier.testTag("background-status")) {
            DesignSection(tr(message(205)))
            DesignText(tr(state.sourceCheckStatus.toUserSummary()), 14)
        }
        DesignNote(tr(message(206)), tr(message(207)))
        state.feedback?.let { DesignText(tr(it), 12, TextInset, muted = true) }
    }
}

private fun SourceCheckStatus.toUserSummary(): String {
    val time = checkedAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) ?: message(208)
    val resultText = when (result) {
        SourceCheckResult.NEVER -> message(209)
        SourceCheckResult.INITIAL -> message(210)
        SourceCheckResult.UNCHANGED -> message(211)
        SourceCheckResult.CHANGED -> message(212)
        SourceCheckResult.FAILED -> message(213)
        SourceCheckResult.NEEDS_CONFIGURATION -> message(214)
    }
    return message(147, (time), (resultText))
}
