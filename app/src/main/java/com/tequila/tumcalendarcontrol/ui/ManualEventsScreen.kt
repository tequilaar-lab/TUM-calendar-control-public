/*
 * Aufgabe: Eigene Campustermine für nächste bestätigte Ausgabe erfassen oder entfernen.
 * Ein-/Ausgabe: Titel, Datum, Uhrzeit und Status -> lokale Regelbestands-Änderungsabsicht.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Quellenkursbearbeitung, echte Datumsdialoge, OAuth oder Synchronisierungsausführung.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.sync.ManualScheduleItem
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField

@Composable
fun ManualEventsScreen(state: CalendarControlState) {
    var title by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var date by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(java.time.LocalDate.now(ZoneId.of("Europe/Berlin")).toString())
    }
    var startHour by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("9") }
    var startMinute by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("0") }
    var endHour by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("10") }
    var endMinute by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("0") }
    var attendance by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(AttendanceState.CHOOSE_TO_ATTEND) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        InformationCard(message(430), message(431))
        TextField(title, { title = it }, Modifier.fillMaxWidth(), label = tr(message(432)))
        TextField(date, { date = it }, Modifier.fillMaxWidth(), label = tr(message(433)))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(startHour, { startHour = it }, Modifier.weight(1f), label = tr(message(349)))
            TextField(startMinute, { startMinute = it }, Modifier.weight(1f), label = tr(message(350)))
            TextField(endHour, { endHour = it }, Modifier.weight(1f), label = tr(message(351)))
            TextField(endMinute, { endMinute = it }, Modifier.weight(1f), label = tr(message(352)))
        }
        AttendanceState.entries.forEach { option ->
            Button(onClick = { attendance = option }, modifier = Modifier.fillMaxWidth()) {
                Text(tr(if (attendance == option) message(434, (option.label), (option.colorLabel)) else message(435, (option.label), (option.colorLabel))))
            }
        }
        Button(
            onClick = {
                state.addManualEvent(title, date, startHour, startMinute, endHour, endMinute, attendance)
                if (state.feedback?.startsWith(message(436)) == true) title = ""
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(tr(message(437))) }
        state.ruleLedger.manualEvents.forEach { item ->
            InformationCard(message(147, (item.title), (item.attendanceState.label)), item.berlinTimeSummary())
            Button(onClick = { state.removeManualEvent(item.id) }, modifier = Modifier.fillMaxWidth()) { Text(tr(message(438))) }
        }
        state.feedback?.let { Text(tr(it), color = LocalTumPalette.current.onSurfaceVariant) }
    }
}

private fun ManualScheduleItem.berlinTimeSummary(): String {
    val zone = ZoneId.of("Europe/Berlin")
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    return message(439, (startsAt.atZone(zone).format(formatter)), (endsAt.atZone(zone).format(formatter)))
}
