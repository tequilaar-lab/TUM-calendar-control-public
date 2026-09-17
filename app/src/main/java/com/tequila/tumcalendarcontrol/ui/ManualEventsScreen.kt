/*
 * 职责：录入和移除应用拥有的手动校园事项，供下一次写入日历时批量输出。
 * 输入／输出：输入用户填写的标题、日期、时分和三态；输出本机 RuleLedger 编辑请求。
 * 外部副作用：无；事项保存到应用私有规则资料库，不会立即写入日历。
 * 不负责：来源课程编辑、真实日期选择器、OAuth 或执行同步。
 */
package com.tequila.tumcalendarcontrol.ui

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
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField

@Composable
fun ManualEventsScreen(state: CalendarControlState) {
    var title by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var date by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(java.time.LocalDate.now().toString()) }
    var startHour by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("9") }
    var startMinute by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("0") }
    var endHour by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("10") }
    var endMinute by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("0") }
    var attendance by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(AttendanceState.CHOOSE_TO_ATTEND) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        InformationCard("添加校园事项", "可先添加多条事项。它们会和课程一起在下次写入日历时处理；现在不会写入。")
        TextField(title, { title = it }, Modifier.fillMaxWidth(), label = "事项名称")
        TextField(date, { date = it }, Modifier.fillMaxWidth(), label = "日期（YYYY-MM-DD）")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(startHour, { startHour = it }, Modifier.weight(1f), label = "开始时")
            TextField(startMinute, { startMinute = it }, Modifier.weight(1f), label = "开始分")
            TextField(endHour, { endHour = it }, Modifier.weight(1f), label = "结束时")
            TextField(endMinute, { endMinute = it }, Modifier.weight(1f), label = "结束分")
        }
        AttendanceState.entries.forEach { option ->
            Button(onClick = { attendance = option }, modifier = Modifier.fillMaxWidth()) {
                Text(if (attendance == option) "✓ ${option.label}（${option.colorLabel}）" else "${option.label}（${option.colorLabel}）")
            }
        }
        Button(
            onClick = {
                state.addManualEvent(title, date, startHour, startMinute, endHour, endMinute, attendance)
                if (state.feedback?.startsWith("校园事项已保存") == true) title = ""
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存事项到这台设备") }
        state.ruleLedger.manualEvents.forEach { item ->
            InformationCard("${item.title} · ${item.attendanceState.label}", "${item.startsAt} 至 ${item.endsAt}")
            Button(onClick = { state.removeManualEvent(item.id) }, modifier = Modifier.fillMaxWidth()) { Text("删除此事项") }
        }
        state.feedback?.let { Text(it, color = LocalTumPalette.current.onSurfaceVariant) }
    }
}
