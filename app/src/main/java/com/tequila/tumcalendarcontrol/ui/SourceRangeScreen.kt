/*
 * 职责：编辑 iCal 来源输出范围并明确区分“配置范围”与“已读数据覆盖期”。
 * 输入／输出：输入当前私有配置及只读来源摘要；输出经状态机校验后的本机范围配置或读取意图。
 * 外部副作用：日期选择器仅由系统显示；保存通过根层加密偏好持久化，读取必须再次由用户确认。
 * 不负责：iCal 网络请求、来源解析、Google 授权或任何日历写入。
 */
package com.tequila.tumcalendarcontrol.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
    var startDate by remember(configuration.sourceRangeStartDate) { mutableStateOf(configuration.sourceRangeStartDate) }
    var endDate by remember(configuration.sourceRangeEndDate) { mutableStateOf(configuration.sourceRangeEndDate) }
    var validation by remember { mutableStateOf<List<String>>(emptyList()) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InformationCard("同步时间范围", "它决定哪些来源实例会进入后续预览和输出；不会改变订阅地址，也不会过滤手动校园事项。")
        val context = LocalContext.current
        DateRangeField("开始日期", startDate, "不限", onClick = { showDatePicker(context, startDate) { startDate = it } })
        DateRangeField("结束日期", endDate, "不限", onClick = { showDatePicker(context, endDate) { endDate = it } })
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
        ) { Text("保存范围") }
        validation.forEach { Text(it, color = LocalTumPalette.current.onSurfaceVariant) }
        ReadCoverageCard(state)
        if (state.sourceReloadConfirmationPending) {
            InformationCard("重新读取会替换当前目录", "当前已选择的课程将被清空。已保存的出席规则不变；是否继续？")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { state.resolveSourceReloadConfirmation(false) }, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = { state.resolveSourceReloadConfirmation(true) }, modifier = Modifier.weight(1f)) { Text("继续只读读取") }
            }
        } else {
            Button(
                onClick = state::requestSourceReadForCurrentRange,
                enabled = !state.sourceReadInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.sourceReadInProgress) "正在只读读取 iCal…" else "按此范围读取 iCal") }
        }
        InformationCard("下一步", "读取完成后进入“选择来源课程”。只有生成预览并再次确认后，才可能写入已开启的目标日历。")
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
            Text("选择")
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
        Button(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text("清除范围（读取全部）") }
    }
}

@Composable
private fun ReadCoverageCard(state: CalendarControlState) {
    val preview = state.sourcePreview
    val coverage = if (preview == null) "尚未读取。它会在你主动读取后显示，不是可编辑范围。" else {
        "${preview.firstManageableStart.toLocalDateText()} 至 ${preview.lastManageableEnd.toLocalDateText()}；共 ${preview.manageableOccurrenceCount} 条可管理实例。"
    }
    InformationCard("来源数据覆盖期（只读）", coverage)
}

private fun Instant?.toLocalDateText(): String = this?.atZone(BerlinZone)?.toLocalDate()?.format(DateText) ?: "无"

private fun showDatePicker(context: android.content.Context, it: String, onPicked: (String) -> Unit) {
    val initial = runCatching { LocalDate.parse(it) }.getOrElse { LocalDate.now(BerlinZone) }
    DatePickerDialog(
        context,
        { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day).format(DateText)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}
