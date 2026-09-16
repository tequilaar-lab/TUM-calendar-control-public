/*
 * 职责：展示本次写入的独立多选目标。
 * 输入／输出：当前确认状态；输出目标勾选意图。
 * 外部副作用：无。不负责权限申请、默认偏好或日历写入。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.sync.OutputTarget
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun WriteTargetSelector(state: CalendarControlState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("本次写入到哪里（可多选）")
        OutputTarget.entries.forEach { target ->
            val checked = target in state.currentWriteTargets
            Row(Modifier.fillMaxWidth().toggleable(
                value = checked, enabled = !state.managedSyncInProgress, role = Role.Checkbox,
                onValueChange = { state.toggleCurrentWriteTarget(target) },
            ).padding(12.dp)) {
                Text("${if (checked) "☑" else "☐"} ${if (target == OutputTarget.LOCAL_CALENDAR) "本机日历" else "Google 日历"}")
            }
        }
        Text("仅影响本次写入，不改变默认开关。双选会写入两本日历；荣耀若同时显示两本，可能看到两份。")
    }
}
