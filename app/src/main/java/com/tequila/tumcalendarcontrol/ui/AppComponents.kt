/*
 * Aufgabe: Wiederverwendbare Kurszeilen, Informationskarten und Teilnahmefarben bereitstellen.
 * Ein-/Ausgabe: Kurs-/Hinweisdaten und Rückrufe -> Kurszeilen, Informationskarten und Statusfarben.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Domänenregeln, Datenspeicherung oder Navigationsentscheidungen.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Star
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.domain.Course
import com.tequila.tumcalendarcontrol.domain.displayLocation
import com.tequila.tumcalendarcontrol.domain.displaySchedule
import com.tequila.tumcalendarcontrol.domain.displayTitle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CourseRow(course: Course, onClick: () -> Unit, condensed: Boolean = false) {
    val palette = LocalTumPalette.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        insideMargin = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(palette.primaryContainer, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Star, tr(message(146))) }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.padding(end = 8.dp)) {
                Text(
                    tr(if (condensed) message(147, (course.displaySchedule), (course.attendance.label)) else message(147, (course.displayTitle), (course.attendance.label))),
                    style = MiuixTheme.textStyles.title3,
                )
                Text(
                    tr(com.tequila.tumcalendarcontrol.i18n.concatenateMessages((if (condensed) message(147, (course.displayLocation), (course.attendance.colorLabel)) else message(148, (course.displaySchedule), (course.displayLocation), (course.attendance.colorLabel))),
                        if (course.attendance == AttendanceState.STOP_ATTENDING) message(149) else "")),
                    color = palette.onSurfaceVariant,
                )
            }
            StatusDot(course.attendance)
            Spacer(Modifier.size(8.dp))
            Icon(Icons.Rounded.ChevronRight, tr(message(150)))
        }
    }
}

@Composable
fun InformationCard(title: String, body: String, modifier: Modifier = Modifier) {
    val palette = LocalTumPalette.current
    Card(modifier = modifier.fillMaxWidth(), insideMargin = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr(title), style = MiuixTheme.textStyles.title3)
            Text(tr(body), color = palette.onSurfaceVariant)
        }
    }
}

@Composable
fun StatusDot(state: AttendanceState) {
    val palette = LocalTumPalette.current
    Box(
        Modifier
            .size(12.dp)
            .background(attendanceColor(state, palette), RoundedCornerShape(6.dp)),
    )
}

fun attendanceColor(state: AttendanceState, palette: TumPalette): Color = when (state) {
    AttendanceState.MUST_ATTEND -> palette.green
    AttendanceState.CHOOSE_TO_ATTEND -> palette.blue
    AttendanceState.STOP_ATTENDING -> palette.gray
}
