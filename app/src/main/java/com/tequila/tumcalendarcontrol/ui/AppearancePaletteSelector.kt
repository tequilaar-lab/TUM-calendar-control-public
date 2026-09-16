/*
 * 职责：展示深浅色各自的有限配色选择与色样。
 * 输入／输出：输入外观状态，输出用户选择；标签不单凭颜色表达。
 * 外部副作用：无，保存由根协调层处理。
 * 不负责：改变系统主题、出席颜色、日历内容或壁纸取色。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AppearancePaletteSelector(state: CalendarControlState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("浅色配色", style = MiuixTheme.textStyles.title3)
        Text("两种模式分别保存配色，不改变课程的出席颜色。", color = LocalTumPalette.current.onSurfaceVariant)
        LightPaletteStyle.entries.forEach { style ->
            PaletteChoice(style.label, appearancePalette(false, style, state.darkPaletteStyle), state.lightPaletteStyle == style) { state.lightPaletteStyle = style }
        }
        Text("深色配色", style = MiuixTheme.textStyles.title3)
        DarkPaletteStyle.entries.forEach { style ->
            PaletteChoice(style.label, appearancePalette(true, state.lightPaletteStyle, style), state.darkPaletteStyle == style) { state.darkPaletteStyle = style }
        }
    }
}

@Composable
private fun PaletteChoice(label: String, palette: TumPalette, selected: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), insideMargin = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioButton(selected = selected, onClick = onClick)
            Box(Modifier.size(28.dp).background(palette.surface, CircleShape).border(1.dp, LocalTumPalette.current.outline, CircleShape))
            Text(label, modifier = Modifier.weight(1f))
            if (selected) Text("已选择", color = LocalTumPalette.current.primary)
        }
    }
}
