/*
 * 职责：编辑 iCal 来源输出范围并明确区分“配置范围”与“已读数据覆盖期”。
 * 输入／输出：输入当前私有配置及只读来源摘要；输出经状态机校验后的本机范围配置或读取意图。
 * 外部副作用：日期选择器仅由系统显示；保存通过根层加密偏好持久化，读取必须再次由用户确认。
 * 不负责：iCal 网络请求、来源解析、Google 授权或任何日历写入。
 */
package com.tequila.tumcalendarcontrol.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.R
import com.tequila.tumcalendarcontrol.sync.SourceOutputDateWindow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val DateText = DateTimeFormatter.ISO_LOCAL_DATE
private val BerlinZone = ZoneId.of("Europe/Berlin")

@Composable
fun SourceRangeScreen(state: CalendarControlState) {
    val configuration = state.privateSyncConfiguration
    val usesDarkTheme = state.themeMode.usesDarkTheme(isSystemInDarkTheme())
    var startDate by remember(configuration.sourceRangeStartDate) { mutableStateOf(configuration.sourceRangeStartDate) }
    var endDate by remember(configuration.sourceRangeEndDate) { mutableStateOf(configuration.sourceRangeEndDate) }
    var validation by remember { mutableStateOf<List<String>>(emptyList()) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InformationCard("选择读取时间", "选择要读取的课程日程时间。留空会读取链接中可用的全部时间；不会改动课程地址或手动事项。")
        val context = LocalContext.current
        DateRangeField("开始日期", startDate, "不限", onClick = { showDatePicker(context, startDate, usesDarkTheme) { startDate = it } })
        DateRangeField("结束日期", endDate, "不限", onClick = { showDatePicker(context, endDate, usesDarkTheme) { endDate = it } })
        QuickRangeButtons(
            onFromToday = { startDate = LocalDate.now(BerlinZone).format(DateText); endDate = "" },
            onNextNinetyDays = {
                val today = LocalDate.now(BerlinZone)
                startDate = today.format(DateText)
                endDate = today.plusDays(89).format(DateText)
            },
            onClear = { startDate = ""; endDate = "" },
        )
        Button(
            onClick = {
                validation = state.saveSourceOutputRange(startDate, endDate)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存时间范围") }
        validation.forEach { Text(it, color = LocalTumPalette.current.onSurfaceVariant) }
        ReadCoverageCard(state)
        if (state.sourceReloadConfirmationPending) {
            SourceReloadConfirmationCard(state)
        } else {
            Button(
                onClick = state::requestSourceReadForCurrentRange,
                enabled = !state.sourceReadInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.sourceReadInProgress) "正在读取课程…" else "读取课程") }
        }
        InformationCard("下一步：选择课程", "读取后先选择要管理的课程，再设置出席和收藏。写入前会显示本机和 Google 日历将受影响的数量；确认后才会写入。")
    }
}

/** 重读确认只保存新的只读课程版本；旧版本保留一个可供用户切换，继续不是日历同步。 */
@Composable
private fun SourceReloadConfirmationCard(state: CalendarControlState) {
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("重新读取课程", style = MiuixTheme.textStyles.title3)
            Text("会保存为最新课程版本并清空本次选择；较早一次成功读取会保留，之后可自行切换。已保存的出席和收藏不变；是否继续？", color = LocalTumPalette.current.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { state.resolveSourceReloadConfirmation(false) }, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = { state.resolveSourceReloadConfirmation(true) }, modifier = Modifier.weight(1f)) { Text("重新读取") }
            }
        }
    }
}

@Composable
private fun DateRangeField(label: String, value: String, emptyLabel: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        insideMargin = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MiuixTheme.textStyles.body1)
                Text(if (value.isBlank()) emptyLabel else value, color = LocalTumPalette.current.onSurfaceVariant)
            }
            Text("选择日期")
        }
    }
}

@Composable
private fun QuickRangeButtons(onFromToday: () -> Unit, onNextNinetyDays: () -> Unit, onClear: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("快捷范围", style = MiuixTheme.textStyles.title3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onFromToday, modifier = Modifier.weight(1f)) { Text("从今天起") }
            Button(onClick = onNextNinetyDays, modifier = Modifier.weight(1f)) { Text("未来 90 天") }
        }
        Button(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text("不限制时间（读取全部）") }
    }
}

@Composable
private fun ReadCoverageCard(state: CalendarControlState) {
    val preview = state.sourcePreview
    val coverage = if (preview == null) "还没有读取课程。读取后会在这里显示可用的时间。" else {
        "${preview.firstManageableStart.toLocalDateText()} 至 ${preview.lastManageableEnd.toLocalDateText()}；共 ${preview.manageableOccurrenceCount} 条课程日程。"
    }
    InformationCard("已读取的课程时间", coverage)
}

private fun Instant?.toLocalDateText(): String = this?.atZone(BerlinZone)?.toLocalDate()?.format(DateText) ?: "无"

/** 平台日期选择器沿用应用已解析的明暗模式，避免 Compose 深色页弹出浅色系统对话框。 */
private fun showDatePicker(context: android.content.Context, it: String, usesDarkTheme: Boolean, onPicked: (String) -> Unit) {
    val initial = runCatching { LocalDate.parse(it) }.getOrElse { LocalDate.now(BerlinZone) }
    DatePickerDialog(
        context,
        if (usesDarkTheme) R.style.Theme_TumCalendarControl_DatePicker_Dark else R.style.Theme_TumCalendarControl_DatePicker_Light,
        { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day).format(DateText)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}
