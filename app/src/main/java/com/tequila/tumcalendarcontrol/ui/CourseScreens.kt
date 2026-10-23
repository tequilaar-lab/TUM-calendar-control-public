/*
 * Aufgabe: Kursseiten verdrahten: Übersicht, Suche und Favoriten (über CourseBrowsePages) sowie Details und lokale Bearbeitung.
 * Ein-/Ausgabe: CalendarControlState-Projektion und Rückrufe -> scrollbare Kursseiten im neuen Erscheinungsbild.
 * Nebenwirkungen: Keine externen Aktionen; nur bestehende Zustands- und Navigationsaufrufe.
 * Nicht zuständig: Seitenrouting, Teilnahmeregelbearbeitung, Einstellungs-/Ausgabeseiten oder Android-Koordination.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.domain.TumEventStatus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.domain.Course
import com.tequila.tumcalendarcontrol.domain.CourseLocalOverride
import com.tequila.tumcalendarcontrol.domain.CourseMeetingTime
import com.tequila.tumcalendarcontrol.domain.CourseWeekday
import com.tequila.tumcalendarcontrol.domain.displayLocation
import com.tequila.tumcalendarcontrol.domain.displaySchedule
import com.tequila.tumcalendarcontrol.domain.displayTitle
import com.tequila.tumcalendarcontrol.ui.design.DesignActionPage
import com.tequila.tumcalendarcontrol.ui.design.DesignCheck
import com.tequila.tumcalendarcontrol.ui.design.DesignChips
import com.tequila.tumcalendarcontrol.ui.design.DesignField
import com.tequila.tumcalendarcontrol.ui.design.DesignFrostCard
import com.tequila.tumcalendarcontrol.ui.design.DesignNote
import com.tequila.tumcalendarcontrol.ui.design.DesignRow
import com.tequila.tumcalendarcontrol.ui.design.DesignSection
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors

@Composable
internal fun OverviewScreen(state: CalendarControlState) = OverviewHome(state)

@Composable
internal fun SearchScreen(state: CalendarControlState) = SearchPage(state)

@Composable
internal fun FavoritesScreen(state: CalendarControlState) = FavoritesPage(state)

/** Quellstatus bleibt fachlich unverändert; nur seine Erklärung wird lokalisiert. */
private fun Course.statusLabel(): String = when (sourceStatus) {
    TumEventStatus.UNKNOWN -> message(10)
    TumEventStatus.FIX -> message(680)
    TumEventStatus.PLANNED -> message(681)
    TumEventStatus.MOVED -> message(682)
    TumEventStatus.CANCELLED -> message(683)
    TumEventStatus.REJECTED -> message(684)
    TumEventStatus.DELETED -> message(685)
}

/**
 * Details in Milchglaskarten: Kurs (Name, Zeit, Ort, Quellstatus), Teilnahme und Favorit, lokale Anzeigeänderung.
 * Die schwebende Hauptaktion öffnet die Teilnahmeänderung.
 */
@Composable
internal fun DetailScreen(state: CalendarControlState) {
    val course = state.selectedCourse
    DesignActionPage(tr(message(340)), onAction = state::openAttendance, actionTag = "detail-attendance", contentTag = "detail-content") {
        DesignFrostCard {
            DesignText(tr(course.displayTitle), 24, serif = true, modifier = Modifier.testTag("detail-title"))
            DesignText(tr(message(148, course.displaySchedule, course.displayLocation, course.statusLabel())), 13, muted = true)
        }
        DesignFrostCard {
            DesignRow(
                title = tr(message(339, course.attendance.label)),
                detail = tr(message(199, course.attendance.colorLabel, course.attendance.description)),
                tag = "detail-attendance-row",
                onClick = state::openAttendance,
            )
            DesignCheck(tr(message(703)), course.isFavorite, Modifier.testTag("detail-favorite")) { state.toggleFavorite(course.id) }
        }
        DesignFrostCard {
            DesignSection(tr(message(335)))
            DesignText(tr(if (course.localOverride == CourseLocalOverride()) message(336) else message(337)), 13, muted = true)
            DesignRow(tr(message(338)), tag = "detail-edit", onClick = state::openCourseEdit)
        }
        state.feedback?.let { DesignText(tr(it), 12, muted = true) }
    }
}

/** Nur lokale Änderungen bearbeiten; leere Werte übernehmen Quelle, kein Rückschreiben an TUM. */
@Composable
internal fun CourseEditScreen(state: CalendarControlState) {
    val course = state.selectedCourse
    var title by rememberSaveable(course.id, course.instanceKey, course.localOverride) { mutableStateOf(course.localOverride.title.orEmpty()) }
    var location by rememberSaveable(course.id, course.instanceKey, course.localOverride) { mutableStateOf(course.localOverride.location.orEmpty()) }
    val sourceStart = course.startsAt?.atZone(java.time.ZoneId.of("Europe/Berlin"))
    val sourceEnd = course.endsAt?.atZone(java.time.ZoneId.of("Europe/Berlin"))
    val seedWeekday = course.localOverride.meetingTime?.weekday ?: sourceStart?.let { CourseWeekday.entries[it.dayOfWeek.value - 1] } ?: CourseWeekday.MONDAY
    val seedStartHour = (course.localOverride.meetingTime?.startHour ?: sourceStart?.hour ?: 10).toString()
    val seedStartMinute = (course.localOverride.meetingTime?.startMinute ?: sourceStart?.minute ?: 0).toString()
    val seedEndHour = (course.localOverride.meetingTime?.endHour ?: sourceEnd?.hour ?: 12).toString()
    val seedEndMinute = (course.localOverride.meetingTime?.endMinute ?: sourceEnd?.minute ?: 0).toString()
    var weekday by rememberSaveable(course) { mutableStateOf(seedWeekday) }
    var startHour by rememberSaveable(course) { mutableStateOf(seedStartHour) }
    var startMinute by rememberSaveable(course) { mutableStateOf(seedStartMinute) }
    var endHour by rememberSaveable(course) { mutableStateOf(seedEndHour) }
    var endMinute by rememberSaveable(course) { mutableStateOf(seedEndMinute) }
    var validation by remember { mutableStateOf<String?>(null) }
    // Speichern: Zeit nur übernehmen, wenn geändert; ungültige Eingaben melden statt speichern.
    val save: () -> Unit = save@{
        val timeChanged = weekday != seedWeekday || startHour != seedStartHour || startMinute != seedStartMinute || endHour != seedEndHour || endMinute != seedEndMinute
        val time = if (!timeChanged) course.localOverride.meetingTime else runCatching {
            CourseMeetingTime(weekday, startHour.toInt(), startMinute.toInt(), endHour.toInt(), endMinute.toInt())
        }.getOrElse {
            validation = message(353)
            return@save
        }
        state.updateSelectedCourseOverride(CourseLocalOverride(title.ifBlank { null }, location = location.ifBlank { null }, meetingTime = time))
        state.handleBack()
    }
    DesignActionPage(tr(message(354)), onAction = save, actionTag = "edit-save", contentTag = "edit-content") {
        DesignNote(tr(message(343)), tr(message(344)))
        DesignField(tr(message(345, course.title)), title, { title = it }, Modifier.testTag("edit-title"))
        DesignField(tr(message(346, course.location)), location, { location = it }, Modifier.testTag("edit-location"))
        DesignSection(tr(message(347)))
        DesignChips(CourseWeekday.entries.map { tr(it.label) }, weekday.ordinal, Modifier.testTag("edit-weekday")) { weekday = CourseWeekday.entries[it] }
        DesignSection(tr(message(348)))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DesignField(tr(message(349)), startHour, { startHour = it }, Modifier.weight(1f))
            DesignField(tr(message(350)), startMinute, { startMinute = it }, Modifier.weight(1f))
            DesignField(tr(message(351)), endHour, { endHour = it }, Modifier.weight(1f))
            DesignField(tr(message(352)), endMinute, { endMinute = it }, Modifier.weight(1f))
        }
        validation?.let { DesignText(tr(it), 12, color = LocalDesignColors.current.danger, modifier = Modifier.testTag("edit-validation")) }
    }
}

/** Rechter Verzeichnispfeil: geschlossen nach rechts, offen nach unten (gemeinsame Regel, getestet). */
internal fun directoryArrowRotation(expanded: Boolean): Float = if (expanded) 90f else 0f
