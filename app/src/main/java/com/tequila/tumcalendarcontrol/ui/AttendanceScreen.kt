/*
 * Aufgabe: Dreizustandsauswahl mit vier gemeinsamen Geltungsbereichen im neuen Erscheinungsbild darstellen.
 * Ein-/Ausgabe: Teilnahmeentwurf und Bereich -> ausdrückliche Bestätigung über die schwebende Hauptaktion.
 * Nebenwirkungen: Keine externen Aktionen; nur bestehende Zustandsaufrufe.
 * Nicht zuständig: Regelauflösung, echte Quelleninstanzauswahl oder Kalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.ui.design.DesignActionPage
import com.tequila.tumcalendarcontrol.ui.design.DesignChoice
import com.tequila.tumcalendarcontrol.ui.design.DesignField
import com.tequila.tumcalendarcontrol.ui.design.DesignPillButton
import com.tequila.tumcalendarcontrol.ui.design.DesignSection
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors

@Composable
fun AttendanceScreen(state: CalendarControlState) {
    val colors = LocalDesignColors.current
    DesignActionPage(tr(message(198)), onAction = state::confirmAttendance, actionTag = "attendance-confirm", contentTag = "attendance-content") {
        DesignText(tr(message(193, state.selectedCourse.title)), 20)
        DesignText(tr(message(194)), 12, muted = true)
        // Sichtbare Regeln: Grün verbindlich, Blau auswählen, Grau ab Termin nicht teilnehmen; Punkt in der Statusfarbe.
        AttendanceState.entries.forEach { option ->
            DesignChoice(
                label = tr(option.label),
                selected = option == state.pendingAttendance,
                detail = tr(message(199, option.colorLabel, option.description)),
                modifier = Modifier.testTag("attendance-${option.name}"),
                trailing = { Box(Modifier.size(10.dp).clip(CircleShape).background(attendanceInk(option, colors))) },
            ) { state.pendingAttendance = option }
        }
        DesignSection(tr(message(195)))
        AttendanceEditScope.entries.forEach { scope ->
            DesignChoice(tr(scope.label), scope == state.pendingAttendanceScope, Modifier.testTag("scope-${scope.name}"), detail = tr(scope.description)) {
                state.pendingAttendanceScope = scope
            }
        }
        if (state.pendingAttendanceScope == AttendanceEditScope.SPECIFIED_AND_FUTURE) {
            DesignField(tr(message(196)), state.pendingEffectiveDate, { state.pendingEffectiveDate = it }, Modifier.testTag("attendance-date"))
        }
        state.feedback?.let { DesignText(tr(it), 12, muted = true) }
        // Abbrechen bleibt als Nebenaktion im Inhalt; die Bestätigung schwebt.
        DesignPillButton(tr(message(197)), Modifier.testTag("attendance-cancel"), primary = false) { state.open(AppScreen.DETAIL) }
    }
}
