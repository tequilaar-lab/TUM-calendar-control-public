/*
 * 职责：以用户选择的 SAF 文件导出、导入并二次确认替换本机规则与手动事项。
 * 输入／输出：输入当前规则资料和待确认备份；输出明确的文件选择／确认意图。
 * 外部副作用：无；文件读写由根层 Activity Result 在用户选择 URI 后完成。
 * 不负责：备份私有连接信息、替换事件映射、自动同步或改写任何日历。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun RuleBackupScreen(state: CalendarControlState) {
    val pending = state.pendingRuleBackup
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InformationCard("规则备份", "备份只包含三态规则和手动校园事项；不含私有 iCal 地址、Google、账户、令牌、目标日历、已写入事件映射或同步记录。")
        InformationCard("当前本机资料", "${state.ruleLedger.rules.size} 条来源规则 · ${state.ruleLedger.manualEvents.size} 条手动事项")
        Button(onClick = state::requestRuleBackupExport, modifier = Modifier.fillMaxWidth()) { Text("导出规则备份") }
        Button(onClick = state::requestRuleBackupImport, modifier = Modifier.fillMaxWidth()) { Text("从文件导入规则") }
        if (pending != null) {
            InformationCard("导入预览", "将替换为 ${pending.summary.ruleCount} 条来源规则与 ${pending.summary.manualEventCount} 条手动事项。当前设备的事件映射和同步摘要会保留；确认前不会改变任何内容。")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = state::cancelRuleBackupImport, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = state::confirmRuleBackupImport, modifier = Modifier.weight(1f)) { Text("确认替换本机规则") }
            }
        }
        state.feedback?.let { Text(it, color = LocalTumPalette.current.onSurfaceVariant) }
    }
}
