/*
 * 职责：为选中的具体课次提供出席方式、收藏与生效范围编辑表单。
 * 输入／输出：输入 CalendarControlState 中的已选课次和待确认值；输出明确的本机设置请求。
 * 外部副作用：无；确认交给状态层先写入应用私有存储，成功后才回显。
 * 不负责：读取 iCal、展示内部身份、申请权限或写入任何日历。
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
        Text("为 ${state.selectedSourceOccurrences.size} 个课次设置出席和收藏", style = MiuixTheme.textStyles.title3)
        Text(state.currentSelectionSummary(state.selectedSourceOccurrences))
        Text("“只改本次”只改勾选课次；其他范围包含已保存数据中原始课名、类型和组别相同的课次，不合并不同类型。保存后加入待写入选择；写入仍受时间范围限制，尚未写日历。", color = LocalTumPalette.current.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { state.individualAttendanceMode = false }, modifier = Modifier.weight(1f)) { Text(if (!state.individualAttendanceMode) "✓ 统一设置" else "统一设置") }
            Button(onClick = { state.individualAttendanceMode = true }, modifier = Modifier.weight(1f)) { Text(if (state.individualAttendanceMode) "✓ 按课程分别设置" else "按课程分别设置") }
        }
        if (state.individualAttendanceMode) IndividualSourceAttendance(state) else {
        Text("出席方式", style = MiuixTheme.textStyles.title3)
        AttendanceState.entries.forEach { option -> SourceAttendanceOption(option, state) }
        Text("收藏", style = MiuixTheme.textStyles.title3)
        SourceFavoriteAction.entries.forEach { action -> SourceFavoriteActionOption(action, state) }
        }
        Text("生效范围", style = MiuixTheme.textStyles.title3)
        AttendanceEditScope.entries.forEach { scope -> SourceAttendanceScopeOption(scope, state) }
        if (state.pendingSourceAttendanceScope == AttendanceEditScope.SPECIFIED_AND_FUTURE) {
            TextField(
                value = state.pendingSourceEffectiveDate,
                onValueChange = { state.pendingSourceEffectiveDate = it },
                modifier = Modifier.fillMaxWidth(),
                label = "指定从这天起（YYYY-MM-DD）",
            )
        }
        state.feedback?.let { Text(it, color = LocalTumPalette.current.onSurfaceVariant) }
        Text("所选范围覆盖 ${state.sourceAttendanceAffectedCount?.toString() ?: "待填写有效日期"} 个已保存课次；分别设置中保持原样的课程不会扩大选择。收藏按涉及的来源课程生效。", color = LocalTumPalette.current.onSurfaceVariant)
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
            Button(onClick = state::confirmSourceAttendance, modifier = Modifier.weight(1f)) { Text("保存出席和收藏") }
        }
    }
}

/** 收藏操作是可选批量操作；默认保持每门课已有状态，避免意外取消收藏。 */
@Composable
private fun SourceFavoriteActionOption(action: SourceFavoriteAction, state: CalendarControlState) {
    val selected = action == state.pendingSourceFavoriteAction
    Card(
        modifier = Modifier.fillMaxWidth().clickable { state.pendingSourceFavoriteAction = action },
        insideMargin = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = { state.pendingSourceFavoriteAction = action })
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(action.label, style = MiuixTheme.textStyles.body1)
                Text(action.description, color = LocalTumPalette.current.onSurfaceVariant)
            }
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
