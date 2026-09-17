/*
 * 职责：提供按课程名分别设置的草稿与表单。
 * 输入／输出：已选课次与本机草稿；输出每组待保存的出席及收藏。
 * 外部副作用：无；未点保存不修改规则或收藏。
 * 不负责：日历写入、持久化、来源读取或以课程名替代同步身份。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.sync.SourceOccurrenceIdentity
import top.yukonga.miuix.kmp.basic.*

data class IndividualAttendanceDraft(val attendance: AttendanceState? = null, val favorite: SourceFavoriteAction = SourceFavoriteAction.KEEP)
data class IndividualAttendanceGroup(val key: String, val title: String, val selection: Set<SourceOccurrenceIdentity>)

@Composable
internal fun IndividualSourceAttendance(state: CalendarControlState) {
    Text("每门课分别设置；未修改的项目保持原样。下方生效范围对这些设置共同生效，收藏按所属课程保存。")
    state.individualAttendanceGroups.forEach { group ->
        val draft = state.individualAttendanceDrafts[group.key] ?: IndividualAttendanceDraft()
        var expanded by remember(group.key) { mutableStateOf(false) }
        Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(group.title)
                Text(state.currentSelectionSummary(group.selection))
                Text("${group.selection.size} 个已选课次 · ${draft.attendance?.label ?: "保持出席不变"} · ${draft.favorite.label}")
                Button(onClick = { expanded = !expanded }) { Text(if (expanded) "收起设置" else "单独设置") }
                if (expanded) {
                    (listOf<AttendanceState?>(null) + AttendanceState.entries).forEach { option ->
                        Row {
                            RadioButton(selected = option == draft.attendance, onClick = { state.individualAttendanceDrafts = state.individualAttendanceDrafts + (group.key to draft.copy(attendance = option)) })
                            Text(option?.label ?: "保持出席不变", Modifier.padding(start = 8.dp))
                        }
                    }
                    Text("收藏")
                    SourceFavoriteAction.entries.forEach { action ->
                        Row {
                            RadioButton(selected = action == draft.favorite, onClick = { state.individualAttendanceDrafts = state.individualAttendanceDrafts + (group.key to draft.copy(favorite = action)) })
                            Text(action.label, Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}
