/*
 * 职责：以用户选择的 SAF 文件导出、导入并二次确认替换本机规则与手动事项。
 * 输入／输出：输入当前规则资料和待确认备份；输出明确的文件选择／确认意图。
 * 外部副作用：无；文件读写由根层 Activity Result 在用户选择 URI 后完成。
 * 不负责：备份私有连接信息、替换事件映射、自动同步或改写任何日历。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun RuleBackupScreen(state: CalendarControlState) {
    val pending = state.pendingRuleBackup
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InformationCard("备份出席规则与校园事项", "包含 UID 级及课程组级出席规则、手动校园事项；不包含收藏、显示改名、课程地址、Google 登录、日历或已写入日程。")
        InformationCard("当前保存的内容", "${state.ruleLedger.rules.size} 条出席设置 · ${state.ruleLedger.manualEvents.size} 条校园事项")
        Button(onClick = state::requestRuleBackupExport, modifier = Modifier.fillMaxWidth()) { Text("导出出席规则与事项") }
        Button(onClick = state::requestRuleBackupImport, modifier = Modifier.fillMaxWidth()) { Text("从文件恢复出席规则与事项") }
        if (pending != null) {
            InformationCard("确认恢复出席规则与事项", "将替换为 ${pending.summary.ruleCount} 条 UID 规则、${pending.summary.groupRuleCount} 条课程组规则与 ${pending.summary.manualEventCount} 条校园事项。本机已写入日程不受影响。确认前不会更改任何内容。")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = state::cancelRuleBackupImport, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(onClick = state::confirmRuleBackupImport, modifier = Modifier.weight(1f)) { Text("确认恢复") }
            }
        }
        state.feedback?.let { Text(it, color = LocalTumPalette.current.onSurfaceVariant) }
    }
}
