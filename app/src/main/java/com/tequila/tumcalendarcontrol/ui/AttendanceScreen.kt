/*
 * 职责：提供三项互斥的出席状态选择和同一行的取消／确认操作。
 * 输入／输出：输入当前选课与待确认状态；输出用户选择并在确认后交给上层状态机。
 * 外部副作用：无，不直接写入日历、数据库或网络。
 * 不负责：真实日期选择器；灰色状态的具体实例截点将在持久化规则阶段实现。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AttendanceScreen(state: CalendarControlState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("为“${state.selectedCourse.title}”选择状态", style = MiuixTheme.textStyles.title3)
        AttendanceOption(AttendanceState.MUST_ATTEND, state)
        AttendanceOption(AttendanceState.CHOOSE_TO_ATTEND, state)
        AttendanceOption(AttendanceState.STOP_ATTENDING, state)
        if (state.pendingAttendance == AttendanceState.STOP_ATTENDING) {
            InformationCard("生效范围", "从下一个合成实例起生效；过去实例保持原状")
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
private fun AttendanceOption(option: AttendanceState, state: CalendarControlState) {
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
                Text("${option.colorLabel}色 · ${option.description}", color = TumColors.onSurfaceVariant)
            }
            StatusDot(option)
        }
    }
}
