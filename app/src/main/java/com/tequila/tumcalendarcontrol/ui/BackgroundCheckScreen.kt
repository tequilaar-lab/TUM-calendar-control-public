/*
 * 职责：让用户管理默认关闭的 iCal 后台只读检查频率和可见状态。
 * 输入／输出：输入本机检查设置／状态；输出明确的排程或取消意图。
 * 外部副作用：无；实际排程、网络读取和通知均由根界面与 Worker 在用户开启后执行。
 * 不负责：显示来源正文、自动同步、日历写入、Google 授权或范围配置。
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
import com.tequila.tumcalendarcontrol.sync.SourceCheckCadence
import com.tequila.tumcalendarcontrol.sync.SourceCheckResult
import com.tequila.tumcalendarcontrol.sync.SourceCheckStatus
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BackgroundCheckScreen(state: CalendarControlState) {
    val settings = state.sourceCheckSettings
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InformationCard("后台 iCal 检查", "默认关闭。开启后仅在网络可用时读取并比较脱敏摘要；不会创建、更新、删除或撤销任何日历事项。系统省电模式可能延后检查。")
        InformationCard("当前频率", settings.cadence.label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SourceCheckCadence.entries.forEach { cadence ->
                Button(
                    onClick = { state.updateSourceCheck(settings.enabled, cadence) },
                    modifier = Modifier.weight(1f),
                ) { Text(if (cadence == settings.cadence) "✓ ${cadence.label}" else cadence.label) }
            }
        }
        Button(
            onClick = { state.updateSourceCheck(!settings.enabled) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (settings.enabled) "关闭后台检查" else "开启只读后台检查") }
        InformationCard("最近检查", state.sourceCheckStatus.toUserSummary())
        InformationCard("通知与隐私", "检测到可能变化时只发送通用提醒，不包含课程名称、地点、链接或日程内容。拒绝通知权限不影响应用内状态查看。")
        state.feedback?.let { Text(it, color = LocalTumPalette.current.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp)) }
    }
}

private fun SourceCheckStatus.toUserSummary(): String {
    val time = checkedAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) ?: "尚未检查"
    val resultText = when (result) {
        SourceCheckResult.NEVER -> "尚未执行"
        SourceCheckResult.INITIAL -> "已建立首次只读摘要"
        SourceCheckResult.UNCHANGED -> "未发现变化"
        SourceCheckResult.CHANGED -> "检测到可能变化；请打开应用手动读取并预览"
        SourceCheckResult.FAILED -> "检查失败；系统会按策略稍后重试"
        SourceCheckResult.NEEDS_CONFIGURATION -> "未配置私有 iCal，未读取"
    }
    return "$time · $resultText"
}
