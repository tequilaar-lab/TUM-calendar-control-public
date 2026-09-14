/*
 * 职责：提供三态出席选择与四种统一生效范围。
 * 输入／输出：输入当前选课与待确认状态；输出用户选择并在确认后交给上层状态机。
 * 外部副作用：无，不直接写入日历、数据库或网络。
 * 不负责：真实来源实例选择器；当前合成原型只保存用户选择，不写入日历。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AttendanceScreen(state: CalendarControlState) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("为“${state.selectedCourse.title}”选择状态", style = MiuixTheme.textStyles.title3)
        AttendanceOption(AttendanceState.MUST_ATTEND, state)
        AttendanceOption(AttendanceState.CHOOSE_TO_ATTEND, state)
        AttendanceOption(AttendanceState.STOP_ATTENDING, state)
        Text("生效范围", style = MiuixTheme.textStyles.title3)
        AttendanceEditScope.entries.forEach { scope -> AttendanceScopeOption(scope, state) }
        if (state.pendingAttendanceScope == AttendanceEditScope.SPECIFIED_AND_FUTURE) TextField(
            value = state.pendingEffectiveDate,
            onValueChange = { state.pendingEffectiveDate = it },
            modifier = Modifier.fillMaxWidth(),
            label = "指定日期（YYYY-MM-DD；合成预览）",
        )
        state.feedback?.let { message ->
            Text(message, color = LocalTumPalette.current.onSurfaceVariant)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = "取消",
                onClick = { state.open(AppScreen.DETAIL) },
                modifier = Modifier.width(112.dp),
            )
            Button(
                onClick = state::confirmAttendance,
                modifier = Modifier.width(220.dp),
            ) { Text("确认") }
        }
    }
}

@Composable
private fun AttendanceScopeOption(scope: AttendanceEditScope, state: CalendarControlState) {
    val selected = scope == state.pendingAttendanceScope
    Card(
        modifier = Modifier.fillMaxWidth().clickable { state.pendingAttendanceScope = scope },
        insideMargin = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = { state.pendingAttendanceScope = scope })
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(scope.label, style = MiuixTheme.textStyles.body1)
                Text(scope.description, color = LocalTumPalette.current.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AttendanceOption(option: AttendanceState, state: CalendarControlState) {
    val palette = LocalTumPalette.current
    val selected = option == state.pendingAttendance
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { state.pendingAttendance = option },
        insideMargin = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = { state.pendingAttendance = option })
            Spacer(Modifier.padding(4.dp))
            Column(modifier = Modifier.padding(start = 4.dp, end = 8.dp)) {
                Text(option.label, style = MiuixTheme.textStyles.title3)
                Text("${option.colorLabel}色 · ${option.description}", color = palette.onSurfaceVariant)
            }
            StatusDot(option)
        }
    }
}
