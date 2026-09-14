/*
 * 职责：展示当前本机规则计算出的双目标差异，并提供用户确认的授权、执行与撤销入口。
 * 输入／输出：输入 CalendarControlState 的纯预览摘要；输出明确的用户点击请求。
 * 外部副作用：无；实际日历访问由根界面收到确认请求后在后台处理。
 * 不负责：读取 iCal、OAuth 细节、Calendar Provider 调用或显示真实日程正文。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.sync.OutputTarget
import com.tequila.tumcalendarcontrol.sync.SyncChangeType
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun ManagedSyncPreviewScreen(state: CalendarControlState) {
    val preview = state.managedSyncPreview
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InformationCard("受控同步预览", "预览只比较本应用映射与当前候选；不会查询、创建或写入目标日历。")
        if (preview == null) {
            InformationCard("没有可执行的预览", "请返回设置，先读取来源并生成预览。")
            return
        }
        InformationCard("候选日程", "当前将输出 ${preview.desiredEventCount} 条候选。已选来源课程 ${state.managedSourceCandidateCount} 条；手动校园事项 ${state.managedManualCandidateCount} 条（不受 iCal 范围影响）。")
        preview.targetChanges.toSortedMap(compareBy(OutputTarget::name)).forEach { (target, changes) ->
            InformationCard(
                target.label(),
                "新增 ${changes.count { it.type == SyncChangeType.CREATE }} · 更新 ${changes.count { it.type == SyncChangeType.UPDATE }} · 移除 ${changes.count { it.type == SyncChangeType.REMOVE }} · 保持 ${changes.count { it.type == SyncChangeType.KEEP }}",
            )
        }
        if (state.syncDestinations.googleCalendarEnabled && !state.hasGoogleAuthorization) {
            Button(onClick = state::requestGoogleAuthorization, modifier = Modifier.fillMaxWidth()) { Text("先连接 Google 日历") }
        }
        Button(
            onClick = state::requestManagedSyncExecution,
            enabled = !state.managedSyncInProgress,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.managedSyncInProgress) "正在执行…" else "确认执行已预览的同步") }
        if (state.ruleLedger.mappings.isNotEmpty()) {
            Button(
                onClick = state::requestManagedRollback,
                enabled = !state.managedSyncInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("撤销本应用已映射的输出") }
        }
        state.ruleLedger.lastSummary?.let { summary ->
            InformationCard("上次执行摘要", "新增 ${summary.createdCount} · 更新 ${summary.updatedCount} · 移除 ${summary.removedCount}" + (summary.failureSummary?.let { " · $it" } ?: ""))
        }
        state.feedback?.let { Text(it, color = LocalTumPalette.current.onSurfaceVariant) }
    }
}

private fun OutputTarget.label(): String = when (this) {
    OutputTarget.LOCAL_CALENDAR -> "本机荣耀日历输出"
    OutputTarget.GOOGLE_CALENDAR -> "Google 日历镜像输出"
}
