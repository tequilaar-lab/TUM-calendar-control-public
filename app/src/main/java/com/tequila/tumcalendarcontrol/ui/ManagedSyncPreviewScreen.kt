/*
 * 职责：在写入前以简洁的日历影响摘要取得用户确认。
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
        InformationCard("确认写入日历", "以下为本机记录估算；确认后会核对目标日历，补写已缺失的日程。现在还没有改动任何日历。")
        if (preview == null) {
            InformationCard("没有待写入内容", "请返回设置页重新选择课程后再写入。清理已写入日程在设置页单独管理。")
            state.feedback?.let { Text(it, color = LocalTumPalette.current.onSurfaceVariant) }
            return
        }
        InformationCard("本次将写入", "共 ${preview.desiredEventCount} 条日程：课程 ${state.managedSourceCandidateCount} 条，校园事项 ${state.managedManualCandidateCount} 条。")
        if (state.managedCancelledMappedCount > 0) {
            InformationCard(
                "发现来源已取消的旧日程",
                "有 ${state.managedCancelledMappedCount} 条按目标计数的既有日程已被学校来源取消。普通写入不会把未选课程当作删除，因此本次不会自动清除；请在“清理已写入日程”中核对并确认。",
            )
            Button(onClick = state::openOutputCleanup, modifier = Modifier.fillMaxWidth()) { Text("核对并清理已取消日程") }
        }
        WriteTargetSelector(state)
        if (state.sourceCatalogLoaded) InformationCard("使用的课程数据", "${state.sourceSnapshotLabel} · 保存于 ${state.sourceSnapshotSavedAt}。不需要重新读取；需要新版时可返回设置更新。")
        preview.targetChanges.toSortedMap(compareBy(OutputTarget::name)).forEach { (target, changes) ->
            InformationCard(
                target.label(),
                "预计新建 ${changes.count { it.type == SyncChangeType.CREATE }} 条 · 核对并更新 ${changes.count { it.type != SyncChangeType.CREATE }} 条。${if (target in state.readyWriteTargets) "已就绪" else "尚未授权"}。未选中的旧课程会保留。",
            )
        }
        if (OutputTarget.GOOGLE_CALENDAR in state.currentWriteTargets && !state.hasGoogleAuthorization) {
            Button(onClick = state::requestGoogleAuthorization, modifier = Modifier.fillMaxWidth()) { Text("连接 Google 日历") }
        }
        if (OutputTarget.LOCAL_CALENDAR in state.currentWriteTargets && !state.localCalendarPermissionGranted) {
            Button(onClick = state::requestCurrentWriteLocalPermission, modifier = Modifier.fillMaxWidth()) { Text("允许访问本机日历") }
        }
        if (state.readyWriteTargets.isNotEmpty() && state.readyWriteTargets != preview.targetChanges.keys) {
            Button(onClick = state::requestReadyManagedSyncExecution, enabled = !state.managedSyncInProgress, modifier = Modifier.fillMaxWidth()) {
                Text("仅写入${state.readyWriteTargets.joinToString("、") { it.label() }}")
            }
        }
        Button(
            onClick = state::requestManagedSyncExecution,
            enabled = !state.managedSyncInProgress,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.managedSyncInProgress) "正在写入日历…" else "确认并写入日历") }
        state.ruleLedger.lastSummary?.let { summary ->
            InformationCard(
                "上次写入",
                (summary.detailSummary ?: "新建 ${summary.createdCount} · 更新 ${summary.updatedCount} · 清除 ${summary.removedCount}") +
                    (summary.failureSummary?.let { " · $it" } ?: ""),
            )
        }
        state.feedback?.let { Text(it, color = LocalTumPalette.current.onSurfaceVariant) }
    }
}

private fun OutputTarget.label(): String = when (this) {
    OutputTarget.LOCAL_CALENDAR -> "本机日历"
    OutputTarget.GOOGLE_CALENDAR -> "Google 日历"
}
