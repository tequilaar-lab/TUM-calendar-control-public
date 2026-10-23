/*
 * Aufgabe: Inhalt eines aufgeklappten Kurses in der Übersicht: Lehrtyp-Umschalter mit Umbenennen und Terminliste.
 * Ein-/Ausgabe: Kursgruppe und Rückruf -> Termine (Datum, Zeit, Ort, Teilnahmestatus); Antippen öffnet den Termin.
 * Nebenwirkungen: Keine; Umbenennen läuft über den bestehenden CourseTypeRenameButton.
 * Nicht zuständig: Verzeichnisaufbau (CourseIndexPage), Teilnahmeänderung oder Kursidentität.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import com.tequila.tumcalendarcontrol.ui.design.hapticClickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.domain.Course
import com.tequila.tumcalendarcontrol.domain.displayLocation
import com.tequila.tumcalendarcontrol.domain.displaySchedule
import com.tequila.tumcalendarcontrol.ui.design.DesignColors
import com.tequila.tumcalendarcontrol.ui.design.DesignLine
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Zunächst nur die nächsten Termine; der Rest auf Wunsch, damit lange Kurse die Übersicht nicht dominieren. */
private const val InitialSessionCount = 3
private val Berlin: ZoneId = ZoneId.of("Europe/Berlin")
private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val MonthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

@Composable
internal fun OverviewCourseSessions(group: CourseDisplayGroup, compact: Boolean, onOpenCourse: (Course) -> Unit) {
    val colors = LocalDesignColors.current
    var activeTypeKey by rememberSaveable(group.key) { mutableStateOf(group.teachingTypes.first().key) }
    var showAll by rememberSaveable(group.key) { mutableStateOf(false) }
    val active = group.teachingTypes.firstOrNull { it.key == activeTypeKey } ?: group.teachingTypes.first()
    // Chronologisch; Termine ohne Quellbeginn (synthetische Beispiele) bleiben in ihrer Reihenfolge am Ende.
    val sessions = active.courses.sortedWith(compareBy(nullsLast()) { it.startsAt })
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            group.teachingTypes.forEach { type ->
                val isActive = type.key == active.key
                // Tippfläche 48 dp hoch; Beschriftung und Unterstrich mittig darin.
                Column(Modifier.hapticClickable { activeTypeKey = type.key; showAll = false }.heightIn(min = 48.dp), verticalArrangement = Arrangement.Center) {
                    DesignText(tr(type.label), 12, color = if (isActive) colors.accent else colors.muted, weight = if (isActive) FontWeight.Medium else FontWeight.Normal)
                    Box(Modifier.padding(top = 4.dp).width(48.dp).height(2.dp).background(if (isActive) colors.accent else Color.Transparent))
                }
            }
            // Umbenennen nur für den aktiven Typ; ändert ausschließlich die Anzeige, nie die Kursidentität.
            CourseTypeRenameButton(active)
        }
        val more = sessions.size > InitialSessionCount
        DesignText(tr(if (more && !showAll) message(697, sessions.size) else message(372, sessions.size)), 11, muted = true)
        sessions.take(if (showAll) sessions.size else InitialSessionCount).forEach { course ->
            OverviewSessionRow(course, compact, colors) { onOpenCourse(course) }
            DesignLine()
        }
        if (more) {
            DesignText(
                tr(if (showAll) message(699) else message(698, sessions.size)) + if (showAll) "  ↑" else "  ↓", 12, color = colors.accent,
                modifier = Modifier.hapticClickable { showAll = !showAll }.padding(vertical = 8.dp),
            )
        }
    }
}

/** Termin: Tag und Monat (wenn Quellbeginn bekannt), Zeit, Ort und farbiger Teilnahmestatus. */
@Composable
private fun OverviewSessionRow(course: Course, compact: Boolean, colors: DesignColors, onClick: () -> Unit) {
    val start = course.startsAt?.atZone(Berlin)
    // Lokale Zeitänderungen gelten vor der Quelle; dann die überarbeitete Zeitangabe statt der Quellzeit zeigen.
    val usesSourceTime = start != null && course.localOverride.meetingTime == null
    val time = if (usesSourceTime) {
        val end = course.endsAt?.atZone(Berlin)
        start!!.format(TimeFormat) + (end?.let { "–" + it.format(TimeFormat) } ?: "")
    } else course.displaySchedule
    Row(Modifier.fillMaxWidth().hapticClickable(onClick = onClick).padding(vertical = if (compact) 6.dp else 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        if (start != null) {
            Column(Modifier.width(44.dp)) {
                DesignText(start.dayOfMonth.toString().padStart(2, '0'), if (compact) 25 else 32, weight = FontWeight.Light)
                DesignText(start.format(MonthFormat).uppercase(Locale.ENGLISH), 10, muted = true, mono = true)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            DesignText(tr(time), if (compact) 16 else 21)
            DesignText(tr(course.displayLocation), 11, muted = true)
            DesignText(tr(course.attendance.label), 11, color = attendanceInk(course.attendance, colors))
        }
    }
}

/** Sichtbare Regeln: Grün verbindlich, Blau auswählen, Grau nicht teilnehmen. */
internal fun attendanceInk(state: AttendanceState, colors: DesignColors): Color = when (state) {
    AttendanceState.MUST_ATTEND -> colors.success
    AttendanceState.CHOOSE_TO_ATTEND -> colors.accent
    AttendanceState.STOP_ATTENDING -> colors.muted
}
