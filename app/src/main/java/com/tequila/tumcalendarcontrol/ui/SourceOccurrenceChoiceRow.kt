/*
 * 职责：以用户可理解的日期、时间和地点显示一个可独立选择的来源课次。
 * 输入／输出：输入运行内课次、是否选中与切换回调；输出单个课次选择意图。
 * 外部副作用：无；不保存来源正文，不读取 iCal，也不写入日历。
 * 不负责：课程分组、课次身份生成、规则编辑或同步候选计算。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val OccurrenceDisplayZone = ZoneId.of("Europe/Berlin")
private val OccurrenceStartDisplayTime = DateTimeFormatter.ofPattern("M月d日（E）HH:mm")
private val OccurrenceEndDisplayTime = DateTimeFormatter.ofPattern("HH:mm")

@androidx.compose.runtime.Composable
internal fun SourceOccurrenceChoiceRow(
    occurrence: SourceCalendarOccurrence,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onToggle)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(occurrence.title, style = MiuixTheme.textStyles.body1)
            Text(occurrence.toOccurrenceText(), color = LocalTumPalette.current.onSurfaceVariant)
            if (occurrence.location.isNotBlank()) Text("地点：${occurrence.location}", color = LocalTumPalette.current.onSurfaceVariant)
        }
    }
}

internal fun SourceCalendarOccurrence.toOccurrenceText(): String =
    "${OccurrenceStartDisplayTime.format(startsAt.atZone(OccurrenceDisplayZone))}–${OccurrenceEndDisplayTime.format(endsAt.atZone(OccurrenceDisplayZone))}"
