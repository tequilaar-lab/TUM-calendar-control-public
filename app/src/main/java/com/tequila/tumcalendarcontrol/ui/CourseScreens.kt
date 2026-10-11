/*
 * Aufgabe: Übersicht, Details, lokale Bearbeitung, Suche und Favoriten mit gemeinsamen Listen rendern.
 * Ein-/Ausgabe: CalendarControlState-Projektion und Rückrufe -> scrollbare Kursseiten.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Seitenrouting, Teilnahmeregelbearbeitung, Einstellungs-/Ausgabeseiten oder Android-Koordination.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.domain.browseKey
import com.tequila.tumcalendarcontrol.domain.TumEventStatus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.domain.Course
import com.tequila.tumcalendarcontrol.domain.CourseLocalOverride
import com.tequila.tumcalendarcontrol.domain.CourseMeetingTime
import com.tequila.tumcalendarcontrol.domain.CourseWeekday
import com.tequila.tumcalendarcontrol.domain.displayLocation
import com.tequila.tumcalendarcontrol.domain.displaySchedule
import com.tequila.tumcalendarcontrol.domain.displayTitle
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun OverviewScreen(state: CalendarControlState) {
    CourseList(
        courses = state.displayedCourses,
        heading = if (state.sourceCatalogLoaded) message(333) else message(334),
        browserStateKey = "overview",
        onCourseClick = state::openCourse,
    )
}

@Composable
internal fun DetailScreen(state: CalendarControlState) {
    val palette = LocalTumPalette.current
    val course = state.selectedCourse
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Quellstatus bleibt fachlich unverändert; nur seine Erklärung wird lokalisiert.
        val statusLabel = when (course.sourceStatus) {
            TumEventStatus.UNKNOWN -> message(10)
            TumEventStatus.FIX -> message(680)
            TumEventStatus.PLANNED -> message(681)
            TumEventStatus.MOVED -> message(682)
            TumEventStatus.CANCELLED -> message(683)
            TumEventStatus.REJECTED -> message(684)
            TumEventStatus.DELETED -> message(685)
        }
        InformationCard(course.displayTitle, message(148, course.displaySchedule, course.displayLocation, statusLabel))
        InformationCard(message(335), if (course.localOverride == CourseLocalOverride()) message(336) else message(337))
        Button(onClick = state::openCourseEdit, modifier = Modifier.fillMaxWidth()) { Text(tr(message(338))) }
        InformationCard(
            message(339, (course.attendance.label)),
            message(199, (course.attendance.colorLabel), (course.attendance.description)),
        )
        InformationCard(message(340), message(341))
        Button(onClick = state::openAttendance, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Add, tr(message(340)))
            Text(tr(message(340)), modifier = Modifier.padding(start = 8.dp))
        }
        Button(
            onClick = { state.toggleFavorite(course.id) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(if (course.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, tr(message(342)))
            Text(tr(if (course.isFavorite) message(229) else message(156)), modifier = Modifier.padding(start = 8.dp))
        }
        state.feedback?.let { Text(tr(it), color = palette.onSurfaceVariant) }
    }
}

/** Nur lokale Änderungen bearbeiten; leere Werte übernehmen Quelle, kein Rückschreiben an TUM. */
@Composable
internal fun CourseEditScreen(state: CalendarControlState) {
    val course = state.selectedCourse
    var title by androidx.compose.runtime.saveable.rememberSaveable(course.id, course.instanceKey, course.localOverride) { mutableStateOf(course.localOverride.title.orEmpty()) }
    var location by androidx.compose.runtime.saveable.rememberSaveable(course.id, course.instanceKey, course.localOverride) { mutableStateOf(course.localOverride.location.orEmpty()) }
    val sourceStart = course.startsAt?.atZone(java.time.ZoneId.of("Europe/Berlin"))
    val sourceEnd = course.endsAt?.atZone(java.time.ZoneId.of("Europe/Berlin"))
    val seedWeekday = course.localOverride.meetingTime?.weekday ?: sourceStart?.let { CourseWeekday.entries[it.dayOfWeek.value - 1] } ?: CourseWeekday.MONDAY
    val seedStartHour = (course.localOverride.meetingTime?.startHour ?: sourceStart?.hour ?: 10).toString()
    val seedStartMinute = (course.localOverride.meetingTime?.startMinute ?: sourceStart?.minute ?: 0).toString()
    val seedEndHour = (course.localOverride.meetingTime?.endHour ?: sourceEnd?.hour ?: 12).toString()
    val seedEndMinute = (course.localOverride.meetingTime?.endMinute ?: sourceEnd?.minute ?: 0).toString()
    var weekday by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf(seedWeekday) }
    var startHour by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf(seedStartHour) }
    var startMinute by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf(seedStartMinute) }
    var endHour by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf(seedEndHour) }
    var endMinute by androidx.compose.runtime.saveable.rememberSaveable(course) { mutableStateOf(seedEndMinute) }
    var validation by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InformationCard(message(343), message(344))
        TextField(title, { title = it }, Modifier.fillMaxWidth(), label = tr(message(345, (course.title))))
        TextField(location, { location = it }, Modifier.fillMaxWidth(), label = tr(message(346, (course.location))))
        Text(tr(message(347)), style = MiuixTheme.textStyles.title3)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CourseWeekday.entries.forEach { day ->
                Text(
                    tr(day.label),
                    modifier = Modifier.clickable { weekday = day }.padding(6.dp),
                    color = if (weekday == day) LocalTumPalette.current.primary else LocalTumPalette.current.onSurfaceVariant,
                )
            }
        }
        Text(tr(message(348)), style = MiuixTheme.textStyles.title3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(startHour, { startHour = it }, Modifier.weight(1f), label = tr(message(349)))
            TextField(startMinute, { startMinute = it }, Modifier.weight(1f), label = tr(message(350)))
            TextField(endHour, { endHour = it }, Modifier.weight(1f), label = tr(message(351)))
            TextField(endMinute, { endMinute = it }, Modifier.weight(1f), label = tr(message(352)))
        }
        validation?.let { Text(tr(it), color = LocalTumPalette.current.onSurfaceVariant) }
        Button(
            onClick = {
                val timeChanged = weekday != seedWeekday || startHour != seedStartHour || startMinute != seedStartMinute || endHour != seedEndHour || endMinute != seedEndMinute
                val time = if (!timeChanged) course.localOverride.meetingTime else runCatching {
                    CourseMeetingTime(weekday, startHour.toInt(), startMinute.toInt(), endHour.toInt(), endMinute.toInt())
                }.getOrElse {
                    validation = message(353)
                    return@Button
                }
                state.updateSelectedCourseOverride(
                    CourseLocalOverride(title.ifBlank { null }, location = location.ifBlank { null }, meetingTime = time),
                )
                state.handleBack()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(tr(message(354))) }
    }
}

@Composable
internal fun SearchScreen(state: CalendarControlState) {
    AdaptiveCourseBrowser(
        courses = state.searchedCourses,
        heading = if (state.searchQuery.isBlank()) message(355) else message(356),
        browserStateKey = "search",
        onCourseClick = state::openCourse,
        leadingItems = {
            item {
            TextField(
                value = state.searchQuery,
                onValueChange = { state.searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = tr(message(155)),
            )
            }
        },
    )
}

@Composable
internal fun FavoritesScreen(state: CalendarControlState) {
    AdaptiveCourseBrowser(
        courses = state.favoriteCourses,
        heading = message(357),
        browserStateKey = "favorites",
        onCourseClick = state::openCourse,
        leadingItems = { item { InformationCard(message(358), message(359)) } },
    )
}

@Composable
private fun CourseList(courses: List<Course>, heading: String, browserStateKey: String, onCourseClick: (String) -> Unit) {
    AdaptiveCourseBrowser(courses, heading, browserStateKey, onCourseClick)
}

/** Breite Ansicht mit Liste und Zusatzbereich; schmale Ansicht als einzelne scrollbare Liste. */
@Composable
private fun AdaptiveCourseBrowser(
    courses: List<Course>,
    heading: String,
    browserStateKey: String,
    onCourseClick: (String) -> Unit,
    leadingItems: LazyListScope.() -> Unit = {},
) {
    var expandedGroupKeys by androidx.compose.runtime.saveable.rememberSaveable(browserStateKey) { mutableStateOf<Set<String>>(emptySet()) }
    var expandedTeachingTypeKeys by androidx.compose.runtime.saveable.rememberSaveable(browserStateKey) { mutableStateOf<Set<String>>(emptySet()) }
    val typeNames = LocalCourseTypeNames.current.names
    val groups = remember(courses, typeNames) { CourseDisplayGroupModel.groups(courses, typeNames) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val listContent: @Composable (Modifier) -> Unit = { modifier ->
            LazyColumn(
                modifier = modifier.testTag("course-browser-list"),
                contentPadding = PaddingValues(bottom = FloatingNavigationContentClearance),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                leadingItems()
                courseListItems(
                    groups = groups,
                    heading = heading,
                    expandedGroupKeys = expandedGroupKeys,
                    expandedTeachingTypeKeys = expandedTeachingTypeKeys,
                    onCourseClick = onCourseClick,
                    onGroupExpandedChange = { key ->
                        expandedGroupKeys = expandedGroupKeys.toggleCourseDisplayGroup(key)
                    },
                    onTeachingTypeExpandedChange = { key ->
                        expandedTeachingTypeKeys = expandedTeachingTypeKeys.toggleCourseTeachingTypeGroup(key)
                    },
                )
            }
        }
        if (maxWidth < 720.dp) {
            listContent(Modifier.fillMaxSize())
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listContent(Modifier.weight(0.62f).fillMaxHeight())
                CourseBrowserSupportPane(
                    groups = groups,
                    modifier = Modifier.weight(0.38f).fillMaxHeight(),
                    expandedGroupKeys = expandedGroupKeys,
                    expandedTeachingTypeKeys = expandedTeachingTypeKeys,
                    onGroupExpandedChange = { key -> expandedGroupKeys = expandedGroupKeys.toggleCourseDisplayGroup(key) },
                    onTeachingTypeExpandedChange = { key -> expandedTeachingTypeKeys = expandedTeachingTypeKeys.toggleCourseTeachingTypeGroup(key) },
                )
            }
        }
    }
}

@Composable
private fun CourseBrowserSupportPane(
    groups: List<CourseDisplayGroup>,
    modifier: Modifier,
    expandedGroupKeys: Set<String>,
    expandedTeachingTypeKeys: Set<String>,
    onGroupExpandedChange: (String) -> Unit,
    onTeachingTypeExpandedChange: (String) -> Unit,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(bottom = FloatingNavigationContentClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InformationCard(message(360), message(361))
        groups.forEach { group ->
            CourseDirectoryGroup(
                group = group,
                expanded = group.key in expandedGroupKeys,
                expandedTeachingTypeKeys = expandedTeachingTypeKeys,
                onGroupExpandedChange = onGroupExpandedChange,
                onTeachingTypeExpandedChange = onTeachingTypeExpandedChange,
            )
        }
        InformationCard(message(362), message(363))
    }
}

/** Gemeinsame Listenelemente für Übersicht, Suche und Favoriten; keine verschachtelte LazyColumn. */
private fun LazyListScope.courseListItems(
    groups: List<CourseDisplayGroup>,
    heading: String,
    expandedGroupKeys: Set<String>,
    expandedTeachingTypeKeys: Set<String>,
    onCourseClick: (String) -> Unit,
    onGroupExpandedChange: (String) -> Unit,
    onTeachingTypeExpandedChange: (String) -> Unit,
) {
    item { Text(tr(heading), style = MiuixTheme.textStyles.title3) }
    if (groups.isEmpty()) {
        item { InformationCard(message(364), message(365)) }
    } else {
        groups.forEach { group ->
            item(key = "course-group:${group.key}") {
                CourseGroupCard(
                    group = group,
                    expanded = group.key in expandedGroupKeys,
                    onClick = { onGroupExpandedChange(group.key) },
                )
            }
            item(key = "course-group-rows:${group.key}") {
                AnimatedVisibility(
                    visible = group.key in expandedGroupKeys,
                    enter = expandVertically(animationSpec = tween(260)) + fadeIn(animationSpec = tween(180)),
                    exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(140)),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        group.teachingTypes.forEach { typeGroup ->
                            CourseTeachingTypeCard(
                                typeGroup = typeGroup,
                                expanded = typeGroup.key in expandedTeachingTypeKeys,
                                onClick = { onTeachingTypeExpandedChange(typeGroup.key) },
                            )
                            AnimatedVisibility(
                                visible = typeGroup.key in expandedTeachingTypeKeys,
                                enter = expandVertically(animationSpec = tween(230)) + fadeIn(animationSpec = tween(160)),
                                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(130)),
                            ) {
                                Column(
                                    modifier = Modifier.padding(start = 18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    typeGroup.courses.forEach { course ->
                                        CourseRow(course, onClick = { onCourseClick(course.browseKey) }, condensed = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Gleiche Basisnamen visuell zusammenfassen; Aufklappen verändert nur Seitenzustand, keine Kursidentitäten. */
@Composable
private fun CourseGroupCard(group: CourseDisplayGroup, expanded: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    // Linkspfeil gegen Uhrzeigersinn um 90 Grad drehen, damit er beim Öffnen nach unten zeigt.
    val arrowRotation by animateFloatAsState(if (expanded) -90f else 0f, label = message(366))
    Card(
        modifier = Modifier.fillMaxWidth().clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        ),
        insideMargin = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr(group.displayTitle), style = MiuixTheme.textStyles.body1)
                Text(
                    tr(if (expanded) message(367, (group.teachingTypeCount), (group.courses.size)) else message(368, (group.teachingTypeCount), (group.courses.size))),
                    color = LocalTumPalette.current.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                tr(if (expanded) message(369) else message(370)),
                modifier = Modifier.rotate(arrowRotation),
            )
        }
    }
}

/** Zweite Ebene zeigt erkannte Lehrtypen; erst Aufklappen zeigt einzeln bedienbare Termine. */
@Composable
private fun CourseTeachingTypeCard(typeGroup: CourseTeachingTypeGroup, expanded: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val arrowRotation by animateFloatAsState(if (expanded) -90f else 0f, label = message(371))
    Card(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp).clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        ),
        insideMargin = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(tr("└"), color = LocalTumPalette.current.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
            Column(Modifier.weight(1f)) {
                Text(tr(typeGroup.label), style = MiuixTheme.textStyles.body2)
                Text(tr(message(372, (typeGroup.courses.size))), color = LocalTumPalette.current.onSurfaceVariant)
            }
            CourseTypeRenameButton(typeGroup)
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                tr(if (expanded) message(373, (typeGroup.label)) else message(374, (typeGroup.label))),
                modifier = Modifier.rotate(arrowRotation),
            )
        }
    }
}

internal fun directoryArrowRotation(expanded: Boolean): Float = if (expanded) 90f else 0f

/** Kompakter Baum rechts verwendet denselben Aufklappzustand; keine Kurskopie oder Datenänderung. */
@Composable
private fun CourseDirectoryGroup(
    group: CourseDisplayGroup,
    expanded: Boolean,
    expandedTeachingTypeKeys: Set<String>,
    onGroupExpandedChange: (String) -> Unit,
    onTeachingTypeExpandedChange: (String) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val arrowRotation by animateFloatAsState(directoryArrowRotation(expanded), label = message(375))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(interactionSource, indication = null) { onGroupExpandedChange(group.key) }.padding(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, tr(if (expanded) message(376) else message(377)), modifier = Modifier.rotate(arrowRotation))
            Text(tr(group.displayTitle), modifier = Modifier.padding(start = 8.dp).weight(1f), style = MiuixTheme.textStyles.body2)
            Text(tr(message(378, (group.courses.size))), color = LocalTumPalette.current.onSurfaceVariant)
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(modifier = Modifier.padding(start = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                group.teachingTypes.forEach { typeGroup ->
                    val typeExpanded = typeGroup.key in expandedTeachingTypeKeys
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(interactionSource, indication = null) { onTeachingTypeExpandedChange(typeGroup.key) }.padding(6.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        val typeRotation by animateFloatAsState(directoryArrowRotation(typeExpanded), label = message(379))
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, tr(if (typeExpanded) message(380) else message(381)), modifier = Modifier.rotate(typeRotation))
                        Text(tr(typeGroup.label), modifier = Modifier.padding(start = 8.dp).weight(1f))
                        Text(tr(message(378, (typeGroup.courses.size))), color = LocalTumPalette.current.onSurfaceVariant)
                    }
                    AnimatedVisibility(visible = typeExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(modifier = Modifier.padding(start = 22.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            typeGroup.courses.forEach { course ->
                                Text(tr(message(382, (course.displaySchedule))), color = LocalTumPalette.current.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
