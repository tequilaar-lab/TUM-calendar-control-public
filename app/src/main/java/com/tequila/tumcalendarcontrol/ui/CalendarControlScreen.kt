/*
 * 职责：以颜色和说明呈现离线规则解析后的合成课程实例。
 * 输入／输出：输入 PreviewEvent 列表；输出为只读 Compose 界面。
 * 外部副作用：无，不保存编辑、不联网也不访问日历 Provider。
 * 不负责：规则计算、课程筛选或实际同步。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.domain.PreviewEvent
import java.time.format.DateTimeFormatter

@Composable
fun CalendarControlScreen(events: List<PreviewEvent>) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("TUM 日历控制", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "离线规则原型：仅合成数据，尚未连接 TUM、Google 或荣耀日历。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("确认 Fixplatz 后的实例预览", fontWeight = FontWeight.SemiBold)
                events.forEach { event -> PreviewEventCard(event) }
            }
        }
    }
}

@Composable
private fun PreviewEventCard(event: PreviewEvent) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(
                modifier = Modifier
                    .size(14.dp)
                    .background(stateColor(event.state), RoundedCornerShape(50)),
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column {
                Text(event.title, fontWeight = FontWeight.SemiBold)
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
