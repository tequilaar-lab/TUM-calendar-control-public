/*
 * 职责：提供只含浅色模式的 TUM 蓝 Compose-Miuix 色彩令牌。
 * 输入／输出：无业务输入；向所有子界面提供统一主题。
 * 外部副作用：无。
 * 不负责：跟随系统动态配色、深色模式或更改系统主题。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

object TumColors {
    val primary = Color(0xFF0065BD)
    val primaryContainer = Color(0xFFD9E9FF)
    val secondary = Color(0xFF4F6480)
    val secondaryContainer = Color(0xFFDBE8F8)
    val surface = Color(0xFFF9F9FF)
    val surfaceContainer = Color(0xFFF0F4FA)
    val surfaceHighest = Color(0xFFE3EAF3)
    val onSurface = Color(0xFF17202A)
    val onSurfaceVariant = Color(0xFF46515F)
    val outline = Color(0xFF737D8B)
    val green = Color(0xFF2E7D32)
    val blue = Color(0xFF1565C0)
    val gray = Color(0xFF6C737F)
}

@Composable
fun TumCalendarTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme().copy(
        primary = TumColors.primary,
        primaryVariant = TumColors.primaryContainer,
        secondary = TumColors.secondary,
        secondaryContainer = TumColors.secondaryContainer,
        background = TumColors.surface,
        surface = TumColors.surface,
        surfaceContainer = TumColors.surfaceContainer,
        surfaceContainerHighest = TumColors.surfaceHighest,
        onSurface = TumColors.onSurface,
        onSurfaceSecondary = TumColors.onSurfaceVariant,
        outline = TumColors.outline,
    )
    MiuixTheme(colors) { content() }
}
