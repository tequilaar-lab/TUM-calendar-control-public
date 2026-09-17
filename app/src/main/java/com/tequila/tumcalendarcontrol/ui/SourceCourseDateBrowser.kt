/*
 * 职责：以只读月格浏览来源日程，并在点选日期后提供该日的具体课次选择入口。
 * 输入／输出：输入运行内来源实例、当前输出范围和课次选择集；输出点按选择意图。
 * 外部副作用：无；不保存月视图、来源内容或用户选择。
 * 不负责：日历 Provider、重复规则展开、范围保存或同步候选计算。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceOutputDateWindow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val BrowserZone = ZoneId.of("Europe/Berlin")
private val MonthTitle = DateTimeFormatter.ofPattern("yyyy年M月")

@Composable
fun SourceCourseDateBrowser(state: CalendarControlState, outputWindow: SourceOutputDateWindow, modifier: Modifier = Modifier, bottomClearance: androidx.compose.ui.unit.Dp = 16.dp) {
    val visibleOccurrences = state.currentSourceOccurrences.filter {
        !it.isCancelled && outputWindow.includes(it.startsAt, it.endsAt, BrowserZone)
    }
    val initialMonth = visibleOccurrences.minByOrNull(SourceCalendarOccurrence::startsAt)
        ?.startsAt?.atZone(BrowserZone)?.let(YearMonth::from)
        ?: YearMonth.now(BrowserZone)
    var monthText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialMonth.toString()) }
    var dateText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val month = YearMonth.parse(monthText)
    val chosenDate = dateText.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
    val byDate = visibleOccurrences.groupBy { it.startsAt.atZone(BrowserZone).toLocalDate() }
    val selectedDay = chosenDate
    val occurrencesOnDate = selectedDay?.let(byDate::get).orEmpty()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = bottomClearance),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { InformationCard("按日期找课次", "带圆点的日期有可选择的具体上课时间。") }
        item { MonthNavigation(month, onPrevious = { monthText = month.minusMonths(1).toString() }, onNext = { monthText = month.plusMonths(1).toString() }) }
        item { SourceMonthGrid(month, byDate, chosenDate, onDateSelected = { dateText = it.toString() }) }
        if (selectedDay == null) {
            item { InformationCard("选择日期", "点按带圆点的日期，查看并选择当天课次。") }
        } else {
            item { Text("${selectedDay.monthValue}月${selectedDay.dayOfMonth}日 · ${occurrencesOnDate.size} 个课次", style = MiuixTheme.textStyles.title3) }
            if (occurrencesOnDate.isEmpty()) item { InformationCard("当天没有课次", "切换月份或选择其他带圆点的日期。") }
            items(occurrencesOnDate, key = { "${it.sourceUid}|${it.instanceKey}" }) { occurrence ->
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(10.dp)) {
                    SourceOccurrenceChoiceRow(
                        occurrence = occurrence,
                        selected = occurrence.identity in state.selectedSourceOccurrences,
                        onToggle = { state.toggleSourceOccurrenceSelection(occurrence.identity) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthNavigation(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onPrevious, modifier = Modifier.weight(1f)) { Text("上月") }
        Text(month.format(MonthTitle), style = MiuixTheme.textStyles.title3, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        Button(onClick = onNext, modifier = Modifier.weight(1f)) { Text("下月") }
    }
}

@Composable
private fun SourceMonthGrid(
    month: YearMonth,
    byDate: Map<LocalDate, List<SourceCalendarOccurrence>>,
    chosenDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
) {
    val firstOffset = month.atDay(1).dayOfWeek.mondayOffset()
    val cells = List(firstOffset) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    val rows = cells.chunked(7)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) { listOf("一", "二", "三", "四", "五", "六", "日").forEach { day -> Text(day, modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.body2) } }
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { day ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (day != null) SourceDayCell(day, byDate[day].orEmpty().size, day == chosenDate, onDateSelected)
                    }
                }
                repeat(7 - row.size) { Box(Modifier.weight(1f).size(38.dp)) }
            }
        }
    }
}

@Composable
private fun SourceDayCell(day: LocalDate, occurrenceCount: Int, selected: Boolean, onDateSelected: (LocalDate) -> Unit) {
    val palette = LocalTumPalette.current
    val background = if (selected) palette.primaryContainer else palette.surfaceContainer
    Column(
        modifier = Modifier
            .size(38.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(background)
            .clickable { onDateSelected(day) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(day.dayOfMonth.toString(), style = MiuixTheme.textStyles.body2)
        if (occurrenceCount > 0) Text("●", color = palette.primary, style = MiuixTheme.textStyles.body2)
    }
}

private fun DayOfWeek.mondayOffset(): Int = value - DayOfWeek.MONDAY.value
