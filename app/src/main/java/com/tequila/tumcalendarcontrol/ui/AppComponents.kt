/*
 * 职责：提供页面复用的课程行、信息卡片和状态颜色部件。
 * 输入／输出：输入课程或文字数据与点击回调；输出 Compose-Miuix 视觉组件。
 * 外部副作用：仅在用户点击时调用上层回调。
 * 不负责：改变领域规则、保存数据或执行导航决策。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Star
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.domain.Course
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CourseRow(course: Course, onClick: () -> Unit) {
    val palette = LocalTumPalette.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        insideMargin = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(palette.primaryContainer, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Star, "课程") }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.padding(end = 8.dp)) {
                Text("${course.title} · ${course.attendance.label}", style = MiuixTheme.textStyles.title3)
                Text(
                    "${course.attendance.colorLabel} · 已获 Fixplatz" +
                        if (course.attendance == AttendanceState.STOP_ATTENDING) "；历史不改" else "",
                    color = palette.onSurfaceVariant,
                )
            }
            StatusDot(course.attendance)
            Spacer(Modifier.size(8.dp))
            Icon(Icons.Rounded.ChevronRight, "查看课程详情")
        }
    }
}

@Composable
fun InformationCard(title: String, body: String, modifier: Modifier = Modifier) {
    val palette = LocalTumPalette.current
    Card(modifier = modifier.fillMaxWidth(), insideMargin = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MiuixTheme.textStyles.title3)
            Text(body, color = palette.onSurfaceVariant)
        }
    }
}

@Composable
fun StatusDot(state: AttendanceState) {
    val palette = LocalTumPalette.current
    Box(
        Modifier
            .size(12.dp)
            .background(attendanceColor(state, palette), RoundedCornerShape(6.dp)),
    )
}

fun attendanceColor(state: AttendanceState, palette: TumPalette): Color = when (state) {
    AttendanceState.MUST_ATTEND -> palette.green
    AttendanceState.CHOOSE_TO_ATTEND -> palette.blue
    AttendanceState.STOP_ATTENDING -> palette.gray
}
