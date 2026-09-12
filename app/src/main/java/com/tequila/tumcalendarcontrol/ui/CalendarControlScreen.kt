/*
 * 职责：以 MIUIX 主题与组件呈现离线规则解析后的合成课程实例。
 * 输入／输出：输入 PreviewEvent 列表；输出为只读 Compose 界面。
 * 外部副作用：无，不保存编辑、不联网也不访问日历 Provider。
 * 不负责：规则计算、课程筛选或实际同步。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.domain.PreviewEvent
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun CalendarControlScreen(events: List<PreviewEvent>) {
    val themeController = remember { ThemeController(ColorSchemeMode.System) }
    MiuixTheme(controller = themeController) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = "TUM日历",
                    largeTitle = "TUM 日历控制",
                    subtitle = "离线原型 · 仅合成数据",
                )
            },
        ) { innerPadding ->
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    end = 20.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("确认 Fixplatz 后的实例预览", style = MiuixTheme.textStyles.title3)
                }
                items(events, key = { it.dateTime }) { event -> PreviewEventCard(event) }
            }
        }
    }
}

@Composable
private fun PreviewEventCard(event: PreviewEvent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(
                modifier = Modifier
                    .size(14.dp)
                    .background(stateColor(event.state), RoundedCornerShape(50)),
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column {
                Text(event.title, style = MiuixTheme.textStyles.title3)
                Text(event.dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                Text(event.state.label, color = stateColor(event.state))
            }
        }
    }
}

private fun stateColor(state: AttendanceState): Color = when (state) {
    AttendanceState.MUST_ATTEND -> Color(0xFF2E7D32)
    AttendanceState.CHOOSE_TO_ATTEND -> Color(0xFF1565C0)
    AttendanceState.STOP_ATTENDING -> Color(0xFF757575)
}
