/*
 * 职责：为来源课程的单选或多选提供三态及生效范围编辑表单。
 * 输入／输出：输入 CalendarControlState 中的已选来源 UID 和待确认值；输出明确的本机规则编辑请求。
 * 外部副作用：无；确认仅更新内存规则，持久化由界面根部的私有偏好层负责。
 * 不负责：读取 iCal、展示 UID、选择真实日期实例、申请权限或写入任何日历。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SourceAttendanceScreen(state: CalendarControlState) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("为 ${state.selectedSourceCourseUids.size} 门来源课程设置状态", style = MiuixTheme.textStyles.title3)
        Text("新课程默认蓝色；规则只保存 UID、状态与范围，不保存来源标题或地点。", color = LocalTumPalette.current.onSurfaceVariant)
        AttendanceState.entries.forEach { option -> SourceAttendanceOption(option, state) }
        Text("生效范围", style = MiuixTheme.textStyles.title3)
        AttendanceEditScope.entries.forEach { scope -> SourceAttendanceScopeOption(scope, state) }
        if (state.pendingSourceAttendanceScope == AttendanceEditScope.SPECIFIED_AND_FUTURE) {
            TextField(
                value = state.pendingSourceEffectiveDate,
                onValueChange = { state.pendingSourceEffectiveDate = it },
                modifier = Modifier.fillMaxWidth(),
                label = "指定日期（YYYY-MM-DD）",
            )
        }
        state.feedback?.let { Text(it, color = LocalTumPalette.current.onSurfaceVariant) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = "取消",
                onClick = state::handleBack,
                modifier = Modifier.width(112.dp),
            )
            Button(onClick = state::confirmSourceAttendance, modifier = Modifier.weight(1f)) { Text("仅保存本机规则") }
        }
    }
}

/** 三态卡片仅改变待确认值；确认前不更新规则资料库。 */
@Composable
private fun SourceAttendanceOption(option: AttendanceState, state: CalendarControlState) {
    val selected = option == state.pendingSourceAttendance
    Card(
        modifier = Modifier.fillMaxWidth().clickable { state.pendingSourceAttendance = option },
        insideMargin = androidx.compose.foundation.layout.PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = { state.pendingSourceAttendance = option })
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(option.label, style = MiuixTheme.textStyles.title3)
                Text("${option.colorLabel}色 · ${option.description}", color = LocalTumPalette.current.onSurfaceVariant)
            }
        }
    }
}

/** 范围卡片只改变待确认范围；灰色规则由解析器保证不追溯历史。 */
@Composable
private fun SourceAttendanceScopeOption(scope: AttendanceEditScope, state: CalendarControlState) {
    val selected = scope == state.pendingSourceAttendanceScope
    Card(
        modifier = Modifier.fillMaxWidth().clickable { state.pendingSourceAttendanceScope = scope },
        insideMargin = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = { state.pendingSourceAttendanceScope = scope })
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(scope.label, style = MiuixTheme.textStyles.body1)
                Text(scope.description, color = LocalTumPalette.current.onSurfaceVariant)
            }
        }
    }
}
