/*
 * 职责：提供浅色、深色和跟随系统的 TUM 蓝 Compose-Miuix 色彩令牌。
 * 输入／输出：输入主题模式与系统明暗状态；向所有子界面提供统一主题和辅助色板。
 * 外部副作用：无。
 * 不负责：壁纸动态取色、持久化主题偏好或更改系统主题。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

data class TumPalette(
    val primary: Color,
    val primaryContainer: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val surface: Color,
    val surfaceContainer: Color,
    val surfaceHighest: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val green: Color,
    val blue: Color,
    val gray: Color,
)

object TumPalettes {
    val light = TumPalette(
        primary = Color(0xFF0065BD),
        primaryContainer = Color(0xFFD9E9FF),
        secondary = Color(0xFF4F6480),
        secondaryContainer = Color(0xFFDBE8F8),
        surface = Color(0xFFF9F9FF),
        surfaceContainer = Color(0xFFF0F4FA),
        surfaceHighest = Color(0xFFE3EAF3),
        onSurface = Color(0xFF17202A),
        onSurfaceVariant = Color(0xFF46515F),
        outline = Color(0xFF737D8B),
        green = Color(0xFF6DD875),
        blue = Color(0xFF78B7FF),
        gray = Color(0xFFB8C0CC),
    )
    val dark = TumPalette(
        // Campus App 参考的中性近黑层级；TUM 蓝只承担强调与选中状态，不给整页染蓝灰。
        primary = Color(0xFF75B8E2),
        primaryContainer = Color(0xFF005F96),
        secondary = Color(0xFFC7C7C7),
        secondaryContainer = Color(0xFF303030),
        surface = Color(0xFF1B1B1B),
        surfaceContainer = Color(0xFF242424),
        surfaceHighest = Color(0xFF303030),
        onSurface = Color(0xFFF2F2F2),
        onSurfaceVariant = Color(0xFFC9C9C9),
        outline = Color(0xFF8F8F8F),
        green = Color(0xFF6DD875),
        blue = Color(0xFF78B7FF),
        gray = Color(0xFFB8C0CC),
    )
}
/** 保持历史深蓝原值；三种出席语义色不随外观改变。 */
val DeepBluePalette = TumPalettes.dark.copy(
    primary = Color(0xFFA8C8FF), primaryContainer = Color(0xFF004A85),
    secondary = Color(0xFFB7C8E1), secondaryContainer = Color(0xFF344A63),
    surface = Color(0xFF10151C), surfaceContainer = Color(0xFF1A212B), surfaceHighest = Color(0xFF27313E),
    onSurface = Color(0xFFE1E7EF), onSurfaceVariant = Color(0xFFC4CBD5), outline = Color(0xFF8E99A8),
)
val WarmWhitePalette = TumPalettes.light.copy(
    surface = Color(0xFFFFFBF4), surfaceContainer = Color(0xFFF5EFE4), surfaceHighest = Color(0xFFEAE2D5),
    onSurface = Color(0xFF25231F), onSurfaceVariant = Color(0xFF585249), outline = Color(0xFF81786A),
)

fun appearancePalette(isDark: Boolean, light: LightPaletteStyle, dark: DarkPaletteStyle): TumPalette =
    if (isDark) {
        if (dark == DarkPaletteStyle.DEEP_BLUE) DeepBluePalette else TumPalettes.dark
    } else {
        if (light == LightPaletteStyle.WARM_WHITE) WarmWhitePalette else TumPalettes.light
    }

val LocalTumPalette = staticCompositionLocalOf { TumPalettes.light }

/** 将本机保存的主题偏好解析为实际明暗值，供 Compose 与平台日期弹窗共用。 */
fun ThemeMode.usesDarkTheme(systemIsDark: Boolean): Boolean = when (this) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> systemIsDark
}

@Composable
fun TumCalendarTheme(mode: ThemeMode, lightStyle: LightPaletteStyle = LightPaletteStyle.COOL_WHITE, darkStyle: DarkPaletteStyle = DarkPaletteStyle.NEUTRAL_BLACK, content: @Composable () -> Unit) {
    val usesDarkTheme = mode.usesDarkTheme(isSystemInDarkTheme())
    val palette = appearancePalette(usesDarkTheme, lightStyle, darkStyle)
    val colors = (if (usesDarkTheme) darkColorScheme() else lightColorScheme()).copy(
        primary = palette.primary,
        primaryVariant = palette.primaryContainer,
        secondary = palette.secondary,
        secondaryContainer = palette.secondaryContainer,
        background = palette.surface,
        surface = palette.surface,
        surfaceContainer = palette.surfaceContainer,
        surfaceContainerHighest = palette.surfaceHighest,
        onSurface = palette.onSurface,
        onSurfaceSecondary = palette.onSurfaceVariant,
        outline = palette.outline,
    )
    CompositionLocalProvider(LocalTumPalette provides palette) {
        MiuixTheme(colors) { content() }
    }
}
