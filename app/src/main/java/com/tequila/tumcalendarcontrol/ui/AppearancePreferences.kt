/*
 * 职责：定义并保存明暗模式各自的配色偏好。
 * 输入／输出：输入 AppearanceSettings；输出本机偏好，未知值恢复默认。
 * 外部副作用：仅应用私有 SharedPreferences，不联网。
 * 不负责：课程颜色、日历写入、账户或系统外观修改。
 */
package com.tequila.tumcalendarcontrol.ui

import android.content.Context

enum class LightPaletteStyle(val label: String) { COOL_WHITE("清爽白"), WARM_WHITE("暖米白") }
enum class DarkPaletteStyle(val label: String) { NEUTRAL_BLACK("中性黑"), DEEP_BLUE("深蓝") }

data class AppearanceSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val light: LightPaletteStyle = LightPaletteStyle.COOL_WHITE,
    val dark: DarkPaletteStyle = DarkPaletteStyle.NEUTRAL_BLACK,
)

class AppearancePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    fun load() = AppearanceSettings(
        ThemeMode.entries.firstOrNull { it.name == preferences.getString("mode", null) } ?: ThemeMode.SYSTEM,
        LightPaletteStyle.entries.firstOrNull { it.name == preferences.getString("light", null) } ?: LightPaletteStyle.COOL_WHITE,
        DarkPaletteStyle.entries.firstOrNull { it.name == preferences.getString("dark", null) } ?: DarkPaletteStyle.NEUTRAL_BLACK,
    )
    fun save(settings: AppearanceSettings) {
        preferences.edit().putString("mode", settings.mode.name).putString("light", settings.light.name)
            .putString("dark", settings.dark.name).apply()
    }
}
