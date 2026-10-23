/*
 * Aufgabe: Gemeinsames Kursverzeichnis für Übersicht, Suche und Favoriten: nummerierte Kurse; schmal mit Aufklappen,
 *   breit links Verzeichnis und rechts aktueller Kurs.
 * Ein-/Ausgabe: Kursliste der Seite, Kopfinhalt und Typnamen -> Compose-Seite; Antippen eines Termins öffnet die Details.
 * Nebenwirkungen: Keine externen Aktionen; nur bestehende Navigationsaufrufe.
 * Nicht zuständig: Seitenkopf-Inhalte (CourseBrowsePages), Termindarstellung (OverviewSessions), Filterlogik oder Kursidentität.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.domain.browseKey

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.ui.design.DesignLine
import com.tequila.tumcalendarcontrol.ui.design.DesignNote
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors
import top.yukonga.miuix.kmp.basic.Icon

/** Ab dieser Breite Verzeichnis und Detail nebeneinander statt ein gestreckter Telefonaufbau. */
private val WideOverviewBreakpoint = 700.dp

/**
 * Verzeichnisseite: header erhält die Gruppen (z. B. für Zähler), indexTitle beschriftet die Liste,
 * showRefresh zeigt den Einstieg zum erneuten Lesen am Listenende (nur Übersicht).
 * Seitenzustand bleibt je Route getrennt, da der Rahmen jede Route in einem eigenen SaveableStateProvider zeigt.
 */
@Composable
internal fun CourseIndexPage(
    state: CalendarControlState,
    courses: List<com.tequila.tumcalendarcontrol.domain.Course>,
    indexTitle: String,
    showRefresh: Boolean,
    header: @Composable (List<CourseDisplayGroup>) -> Unit,
) {
    val typeNames = LocalCourseTypeNames.current.names
    val groups = remember(courses, typeNames) { CourseDisplayGroupModel.groups(courses, typeNames) }
    // Seitenzustand: aktueller Kurs (breit) und aufgeklappte Kurse (schmal); kein Einfluss auf Kursdaten.
    var currentKey by rememberSaveable { mutableStateOf<String?>(null) }
    var openKeys by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    val current = groups.firstOrNull { it.key == currentKey } ?: groups.firstOrNull()
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        val wide = maxWidth >= WideOverviewBreakpoint
        Column(Modifier.fillMaxSize()) {
            header(groups)
            if (groups.isEmpty()) {
                DesignNote(tr(message(364)), tr(message(365)), Modifier.padding(top = 12.dp))
            } else if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    OverviewIndex(state, groups, indexTitle, showRefresh, Modifier.weight(.40f).fillMaxHeight(), selected = { it.key == current?.key }, expanded = { false }) {
                        currentKey = it.key
                    }
                    Box(Modifier.padding(top = 12.dp, bottom = 24.dp).width(1.dp).fillMaxHeight().background(LocalDesignColors.current.ink.copy(alpha = .10f)))
                    current?.let { OverviewDetailPane(state, it, groups.indexOf(it), groups.size, Modifier.weight(.60f).fillMaxHeight()) }
                }
            } else {
                OverviewIndex(state, groups, indexTitle, showRefresh, Modifier.fillMaxSize(), selected = { it.key in openKeys }, expanded = { it.key in openKeys }) {
                    openKeys = if (it.key in openKeys) openKeys - it.key else openKeys + it.key
                }
            }
        }
    }
}

/** Nummeriertes Verzeichnis; dieselbe Liste dient schmal mit Aufklappen und breit als Auswahl für das Detail. */
@Composable
private fun OverviewIndex(
    state: CalendarControlState,
    groups: List<CourseDisplayGroup>,
    indexTitle: String,
    showRefresh: Boolean,
    modifier: Modifier,
    selected: (CourseDisplayGroup) -> Boolean,
    expanded: (CourseDisplayGroup) -> Boolean,
    onCourse: (CourseDisplayGroup) -> Unit,
) {
    val colors = LocalDesignColors.current
    LazyColumn(modifier.testTag("course-browser-list"), contentPadding = PaddingValues(bottom = FloatingNavigationContentClearance)) {
        item {
            DesignLine()
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                DesignText(tr(indexTitle), 11, muted = true, tracking = 1f)
                if (state.sourceCatalogLoaded) DesignText(tr(message(695)), 10, muted = true)
            }
        }
        itemsIndexed(groups, key = { _, group -> group.key }) { index, group ->
            Column {
                OverviewIndexRow(index, group, selected(group), expanded(group)) { onCourse(group) }
                AnimatedVisibility(
                    visible = expanded(group),
                    enter = expandVertically(tween(260)) + fadeIn(tween(180)),
                    exit = shrinkVertically(tween(220)) + fadeOut(tween(140)),
                ) {
                    Column(Modifier.padding(start = 40.dp, top = 8.dp, bottom = 16.dp)) {
                        OverviewCourseSessions(group, compact = true, onOpenCourse = { state.openCourse(it.browseKey) })
                    }
                }
                DesignLine()
            }
        }
        if (showRefresh) item {
            Spacer(Modifier.height(22.dp))
            DesignText(
                tr(message(700)) + "  ↗", 12, color = colors.accent,
                modifier = Modifier.clickable { state.openSourceRange() }.padding(vertical = 10.dp, horizontal = 4.dp),
            )
        }
    }
}

/** Zeile: Markierungsleiste, Nummer, Kursname, Typen und Terminzahl; Pfeil zeigt geschlossen nach links, offen nach unten. */
@Composable
private fun OverviewIndexRow(index: Int, group: CourseDisplayGroup, selected: Boolean, expanded: Boolean, onClick: () -> Unit) {
    val colors = LocalDesignColors.current
    val rotation by animateFloatAsState(if (expanded) -90f else 0f, label = "overview-arrow")
    val favorite = group.courses.any { it.isFavorite }
    Row(
        Modifier.fillMaxWidth().background(if (selected) colors.wash else colors.paper).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(2.dp).height(54.dp).background(if (selected) colors.accent else colors.paper))
        DesignText("%02d".format(index + 1), 11, muted = true, mono = true, modifier = Modifier.padding(start = 10.dp, end = 14.dp))
        Column(Modifier.weight(1f).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DesignText(tr(group.displayTitle), 15, weight = FontWeight.Medium)
            // map ist inline und erlaubt tr(); joinToString-Lambdas wären nicht @Composable.
            val types = group.teachingTypes.map { tr(it.label) }.joinToString(" · ")
            DesignText(types + "     /     " + tr(message(372, group.courses.size)) + if (favorite) "     ★" else "", 11, muted = true)
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = tr(message(702, group.displayTitle)),
            tint = colors.muted, modifier = Modifier.padding(horizontal = 6.dp).size(20.dp).rotate(rotation),
        )
    }
}

/** Breite Ansicht rechts: Position im Verzeichnis, Kursname in Serifenschrift und Termine des Kurses. */
@Composable
private fun OverviewDetailPane(state: CalendarControlState, group: CourseDisplayGroup, index: Int, total: Int, modifier: Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(bottom = FloatingNavigationContentClearance).testTag("overview-detail"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        DesignLine()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DesignText(tr(message(696)), 11, muted = true, tracking = 1f)
            DesignText("%02d / %02d".format(index + 1, total), 11, muted = true, mono = true)
        }
        DesignText(tr(group.displayTitle), 32, serif = true, modifier = Modifier.testTag("overview-detail-title"))
        OverviewCourseSessions(group, compact = false, onOpenCourse = { state.openCourse(it.browseKey) })
    }
}
