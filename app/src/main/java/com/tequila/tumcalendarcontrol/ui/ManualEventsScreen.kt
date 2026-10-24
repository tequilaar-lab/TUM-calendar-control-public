/*
 * Aufgabe: Eigene Campustermine für nächste bestätigte Ausgabe erfassen oder entfernen (neues Erscheinungsbild);
 *   Art Termin/Prüfung/Frist (Frist mit nur einer Fälligkeitszeit).
 * Ein-/Ausgabe: Art, Titel, Datum, Uhrzeit und Status -> lokale Regelbestands-Änderungsabsicht.
 * Nebenwirkungen: Keine externen Aktionen; Speichern/Löschen über bestehende Zustandsaufrufe.
 * Nicht zuständig: Quellenkursbearbeitung, echte Datumsdialoge, OAuth oder Synchronisierungsausführung.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.sync.ManualEventKind
import com.tequila.tumcalendarcontrol.sync.ManualScheduleItem
import com.tequila.tumcalendarcontrol.ui.design.DesignSegmented
import com.tequila.tumcalendarcontrol.ui.design.DesignActionPage
import com.tequila.tumcalendarcontrol.ui.design.DesignChoice
import com.tequila.tumcalendarcontrol.ui.design.DesignField
import com.tequila.tumcalendarcontrol.ui.design.DesignFrostCard
import com.tequila.tumcalendarcontrol.ui.design.DesignNote
import com.tequila.tumcalendarcontrol.ui.design.DesignPillButton
import com.tequila.tumcalendarcontrol.ui.design.DesignSection
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Freier Text auf der Titellinie (4 dp innerhalb der Blockkanten). */
private val TextInset = Modifier.padding(horizontal = 4.dp)

/** Formular (Art, Name, Datum, Zeit, Teilnahme mit Statusfarbe), schwebend „speichern“; darunter vorhandene Termine mit Löschen. */
@Composable
fun ManualEventsScreen(state: CalendarControlState) {
    val colors = LocalDesignColors.current
    // Anfangsart kann ein Einstieg vorgeben (z. B. Verknüpfung „Prüfung oder Frist hinzufügen“).
    var kind by rememberSaveable { mutableStateOf(state.manualEventsInitialKind) }
    var title by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(java.time.LocalDate.now(ZoneId.of("Europe/Berlin")).toString()) }
    var startHour by rememberSaveable { mutableStateOf("9") }
    var startMinute by rememberSaveable { mutableStateOf("0") }
    var endHour by rememberSaveable { mutableStateOf("10") }
    var endMinute by rememberSaveable { mutableStateOf("0") }
    var attendance by rememberSaveable { mutableStateOf(AttendanceState.CHOOSE_TO_ATTEND) }
    DesignActionPage(
        tr(message(437)),
        onAction = {
            if (state.addManualEvent(title, date, startHour, startMinute, endHour, endMinute, attendance, kind)) title = ""
        },
        actionTag = "events-save", contentTag = "events-content",
    ) {
        DesignNote(tr(message(430)), tr(message(431)))
        DesignSection(tr(message(840)), TextInset)
        DesignSegmented(ManualEventKind.entries.map { tr(it.label()) }, kind.ordinal, Modifier.testTag("events-kind")) { kind = ManualEventKind.entries[it] }
        DesignField(tr(message(432)), title, { title = it }, Modifier.testTag("events-title"), singleLine = true)
        DateInputField(tr(message(433)), date, appUsesDarkTheme(state), { date = it }, Modifier.testTag("events-date"))
        if (kind == ManualEventKind.DEADLINE) {
            // Frist: nur Fälligkeit (Endfelder); im Kalender der 30-Minuten-Block davor.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesignField(tr(message(841)), endHour, { endHour = it }, Modifier.weight(1f).testTag("events-due-hour"))
                DesignField(tr(message(842)), endMinute, { endMinute = it }, Modifier.weight(1f).testTag("events-due-minute"))
            }
            DesignText(tr(message(844)), 12, TextInset, muted = true)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesignField(tr(message(349)), startHour, { startHour = it }, Modifier.weight(1f))
                DesignField(tr(message(350)), startMinute, { startMinute = it }, Modifier.weight(1f))
                DesignField(tr(message(351)), endHour, { endHour = it }, Modifier.weight(1f))
                DesignField(tr(message(352)), endMinute, { endMinute = it }, Modifier.weight(1f))
            }
        }
        // Sichtbare Regeln: Grün verbindlich, Blau auswählen, Grau nicht teilnehmen; Punkt in der Statusfarbe.
        DesignSection(tr(message(589)), TextInset)
        AttendanceState.entries.forEach { option ->
            DesignChoice(
                tr(option.label), option == attendance, Modifier.testTag("events-attendance-${option.name}"), detail = tr(option.colorLabel),
                trailing = { Box(Modifier.size(10.dp).clip(CircleShape).background(attendanceInk(option, colors))) },
            ) { attendance = option }
        }
        state.feedback?.let { DesignText(tr(it), 12, TextInset.testTag("events-feedback"), muted = true) }
        val clock = LocalCountdownClock.current
        val countdown = com.tequila.tumcalendarcontrol.sync.DeadlineCountdown.upcoming(state.ruleLedger.manualEvents, clock.now(), clock.zone())
        state.ruleLedger.manualEvents.forEach { item ->
            DesignFrostCard(Modifier.testTag("events-item")) {
                if (item.kind != ManualEventKind.NORMAL) {
                    // Art und Restzeit (laufend/kommend); vergangene Prüfungen/Fristen nur mit Art.
                    val remaining = countdown.firstOrNull { it.item.id == item.id }?.remaining?.text()
                    DesignText(tr(listOfNotNull(item.kind.label(), remaining).let { com.tequila.tumcalendarcontrol.i18n.joinMessages(it, " · ") }), 11, color = colors.accent)
                }
                DesignText(tr(message(147, item.title, item.attendanceState.label)), 15)
                DesignText(tr(item.berlinTimeSummary()), 12, muted = true)
                DesignPillButton(tr(message(438)), primary = false) { state.removeManualEvent(item.id) }
            }
        }
    }
}

private fun ManualScheduleItem.berlinTimeSummary(): String {
    val zone = ZoneId.of("Europe/Berlin")
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    if (kind == ManualEventKind.DEADLINE) return message(843, endsAt.atZone(zone).format(formatter))
    return message(439, (startsAt.atZone(zone).format(formatter)), (endsAt.atZone(zone).format(formatter)))
}

/** Anzeigename der Art (Segment und Kennzeichen). */
internal fun ManualEventKind.label(): String = when (this) {
    ManualEventKind.NORMAL -> message(837)
    ManualEventKind.EXAM -> message(838)
    ManualEventKind.DEADLINE -> message(839)
}
