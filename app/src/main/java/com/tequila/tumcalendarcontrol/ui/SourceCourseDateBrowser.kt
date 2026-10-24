/*
 * Aufgabe: Quelltermine im lesenden Monatsraster anzeigen und Tagesauswahl anbieten (neues Erscheinungsbild).
 * Ein-/Ausgabe: Laufzeitinstanzen, Zeitraum und Auswahl -> Monatsraster und Tagesauswahlabsicht.
 * Nebenwirkungen: Keine externen Aktionen; Auswahl nur über bestehende Zustandsaufrufe.
 * Nicht zuständig: Calendar Provider, Wiederholungsauflösung, Bereichsspeicherung oder Kandidatenberechnung.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import com.tequila.tumcalendarcontrol.ui.design.hapticClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceOutputDateWindow
import com.tequila.tumcalendarcontrol.ui.design.DesignNote
import com.tequila.tumcalendarcontrol.ui.design.DesignRadii
import com.tequila.tumcalendarcontrol.ui.design.DesignSection
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val BrowserZone = ZoneId.of("Europe/Berlin")

/** Sichtbare Termine nach Tag sowie gespeicherter Monat und Tag (als Text, damit rememberSaveable genügt). */
internal class SourceDateBrowserModel(
    val byDate: Map<LocalDate, List<SourceCalendarOccurrence>>,
    private val monthText: MutableState<String>,
    private val dateText: MutableState<String>,
) {
    val month: YearMonth get() = YearMonth.parse(monthText.value)
    val chosenDate: LocalDate? get() = dateText.value.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
    fun shiftMonth(by: Long) { monthText.value = month.plusMonths(by).toString() }
    fun choose(day: LocalDate) { dateText.value = day.toString() }
}

/** Startmonat: erster sichtbarer Termin, sonst der aktuelle Monat. Abgesagte und Termine außerhalb des Zeitraums fehlen. */
@Composable
internal fun rememberSourceDateBrowser(state: CalendarControlState, outputWindow: SourceOutputDateWindow): SourceDateBrowserModel {
    val visible = state.currentSourceOccurrences.filter { !it.isCancelled && outputWindow.includes(it.startsAt, it.endsAt, BrowserZone) }
    val initialMonth = visible.minByOrNull(SourceCalendarOccurrence::startsAt)?.startsAt?.atZone(BrowserZone)?.let(YearMonth::from) ?: YearMonth.now(BrowserZone)
    val monthText = rememberSaveable { mutableStateOf(initialMonth.toString()) }
    val dateText = rememberSaveable { mutableStateOf("") }
    return SourceDateBrowserModel(visible.groupBy { it.startsAt.atZone(BrowserZone).toLocalDate() }, monthText, dateText)
}

/** Eigenständige Datumsansicht (z. B. für Bildtests); auf der Auswahlseite stehen dieselben Einträge in der Hauptliste. */
@Composable
fun SourceCourseDateBrowser(state: CalendarControlState, outputWindow: SourceOutputDateWindow, modifier: Modifier = Modifier, bottomClearance: Dp = 16.dp) {
    val model = rememberSourceDateBrowser(state, outputWindow)
    LazyColumn(modifier.padding(horizontal = 8.dp), contentPadding = PaddingValues(bottom = bottomClearance), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        sourceDateItems(state, model)
    }
}

/** Monatsnavigation, Raster und Termine des gewählten Tages als Listeneinträge. */
internal fun LazyListScope.sourceDateItems(state: CalendarControlState, model: SourceDateBrowserModel, onCurrent: (String) -> Unit = {}) {
    item(key = "date-hint") { DesignText(tr(message(597)), 12, Modifier.padding(horizontal = 4.dp), muted = true) }
    item(key = "date-month") { MonthNavigation(model.month, onPrevious = { model.shiftMonth(-1) }, onNext = { model.shiftMonth(1) }) }
    item(key = "date-grid") { SourceMonthGrid(model.month, model.byDate, model.chosenDate, model::choose) }
    val day = model.chosenDate
    if (day == null) {
        item(key = "date-none") { DesignNote(tr(message(475)), tr(message(598))) }
        return
    }
    val onDay = model.byDate[day].orEmpty()
    item(key = "date-title") { DesignSection(tr(message(599, day.monthValue, day.dayOfMonth, onDay.size)), Modifier.padding(start = 4.dp)) }
    if (onDay.isEmpty()) item(key = "date-empty") { DesignNote(tr(message(600)), tr(message(601))) }
    onDay.forEach { occurrence ->
        item(key = "date-occ:${occurrence.itemKey()}") {
            SourceOccurrenceChoiceRow(
                occurrence = occurrence,
                selected = occurrence.identity in state.selectedSourceOccurrences,
                onToggle = { onCurrent(SourceBulkSelection.keyOf(occurrence)); state.toggleSourceOccurrenceSelection(occurrence.identity) },
                changes = state.sourceChanges.kindsOf(occurrence.identity),
            )
        }
    }
}

/** Vorheriger/nächster Monat als Akzent-Textlinks (Tippfläche 48 dp), Monatsname mittig. */
@Composable
private fun MonthNavigation(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    val colors = LocalDesignColors.current
    val monthTitle = DateTimeFormatter.ofPattern(tr(message(595)), java.util.Locale.forLanguageTag(LocalAppLanguage.current.language.tag))
    val link = Modifier.heightIn(min = 48.dp).wrapContentHeight().padding(horizontal = 4.dp)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DesignText("‹  " + tr(message(602)), 13, Modifier.hapticClickable(onClick = onPrevious).then(link).testTag("date-previous"), color = colors.accent)
        DesignText(tr(month.format(monthTitle)), 18, Modifier.weight(1f), serif = true, align = TextAlign.Center)
        DesignText(tr(message(603)) + "  ›", 13, Modifier.hapticClickable(onClick = onNext).then(link).testTag("date-next"), color = colors.accent)
    }
}

/** Montag als erster Tag; gewählter Tag in Tinte, Tage mit Terminen mit Akzentpunkt. */
@Composable
private fun SourceMonthGrid(month: YearMonth, byDate: Map<LocalDate, List<SourceCalendarOccurrence>>, chosenDate: LocalDate?, onDateSelected: (LocalDate) -> Unit) {
    val firstOffset = month.atDay(1).dayOfWeek.mondayOffset()
    val cells = List(firstOffset) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf(604, 605, 606, 607, 608, 609, 610).forEach { key ->
                DesignText(tr(message(key)), 11, Modifier.weight(1f), muted = true, align = TextAlign.Center)
            }
        }
        cells.chunked(7).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { day ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (day != null) SourceDayCell(day, byDate[day].orEmpty().size, day == chosenDate, onDateSelected)
                    }
                }
                repeat(7 - row.size) { Box(Modifier.weight(1f).height(48.dp)) }
            }
        }
    }
}

@Composable
private fun SourceDayCell(day: LocalDate, occurrenceCount: Int, selected: Boolean, onDateSelected: (LocalDate) -> Unit) {
    val colors = LocalDesignColors.current
    val ink = if (selected) colors.paper else colors.ink
    Column(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(DesignRadii.Tile))
            .background(if (selected) colors.ink else Color.Transparent).hapticClickable { onDateSelected(day) }.testTag("date-day-${day.dayOfMonth}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DesignText(day.dayOfMonth.toString(), 14, color = ink, weight = if (selected) FontWeight.Medium else FontWeight.Normal)
        Box(
            Modifier.padding(top = 3.dp).size(4.dp).clip(RoundedCornerShape(2.dp))
                .background(if (occurrenceCount == 0) Color.Transparent else if (selected) colors.paper else colors.accent),
        )
    }
}

private fun DayOfWeek.mondayOffset(): Int = value - DayOfWeek.MONDAY.value
