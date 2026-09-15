/*
 * 职责：在写入前以简洁的日历影响摘要取得用户确认，并提供应用输出清除入口。
 * 输入／输出：输入 CalendarControlState 的内部差异摘要；输出明确的用户确认请求。
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
        InformationCard("确认写入日历", "下面是本次会写入的日程和影响数量。现在还没有改动任何日历。")
        if (preview == null) {
            InformationCard("清除本应用写入的日程", "这里只会清除由本应用创建的日程，不会改动你的其他日历或手动日程。")
            if (state.ruleLedger.mappings.isNotEmpty()) {
                Button(
                    onClick = state::requestManagedRollback,
                    enabled = !state.managedSyncInProgress,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.managedSyncInProgress) "正在清除…" else "清除本应用写入的日程") }
            } else InformationCard("没有可清除的日程", "当前没有由本应用写入的日程。")
            state.feedback?.let { Text(it, color = LocalTumPalette.current.onSurfaceVariant) }
            return
        }
        InformationCard("本次将写入", "共 ${preview.desiredEventCount} 条日程：课程 ${state.managedSourceCandidateCount} 条，校园事项 ${state.managedManualCandidateCount} 条。")
        preview.targetChanges.toSortedMap(compareBy(OutputTarget::name)).forEach { (target, changes) ->
            InformationCard(
                target.label(),
                "新建 ${changes.count { it.type == SyncChangeType.CREATE }} 条 · 更新 ${changes.count { it.type == SyncChangeType.UPDATE }} 条 · 清除 ${changes.count { it.type == SyncChangeType.REMOVE }} 条本应用旧日程",
            )
        }
        if (state.syncDestinations.googleCalendarEnabled && !state.hasGoogleAuthorization) {
            Button(onClick = state::requestGoogleAuthorization, modifier = Modifier.fillMaxWidth()) { Text("连接 Google 日历") }
        }
        Button(
            onClick = state::requestManagedSyncExecution,
            enabled = !state.managedSyncInProgress,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.managedSyncInProgress) "正在写入日历…" else "确认并写入日历") }
        if (state.ruleLedger.mappings.isNotEmpty()) {
            Button(
                onClick = state::requestManagedRollback,
                enabled = !state.managedSyncInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("清除本应用写入的日程") }
        }
        state.ruleLedger.lastSummary?.let { summary ->
            InformationCard("上次写入", "新建 ${summary.createdCount} · 更新 ${summary.updatedCount} · 清除 ${summary.removedCount}" + (summary.failureSummary?.let { " · $it" } ?: ""))
        }
        state.feedback?.let { Text(it, color = LocalTumPalette.current.onSurfaceVariant) }
    }
}

private fun OutputTarget.label(): String = when (this) {
    OutputTarget.LOCAL_CALENDAR -> "本机日历"
    OutputTarget.GOOGLE_CALENDAR -> "Google 日历"
}
