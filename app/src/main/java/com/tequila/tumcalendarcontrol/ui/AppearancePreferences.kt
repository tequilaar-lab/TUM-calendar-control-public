/*
 * Aufgabe: Helle/dunkle Darstellungspräferenzen definieren und privat speichern.
 * Ein-/Ausgabe: Darstellungswahl -> getrennt gespeicherte helle/dunkle Präferenzen.
 * Nebenwirkungen: Nur private Darstellungspräferenzen.
 * Nicht zuständig: Kursfarben, Kalenderausgabe, Konten oder Systemdarstellung.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import android.content.Context

enum class LightPaletteStyle(val label: String) { COOL_WHITE(message(189)), WARM_WHITE(message(190)) }
enum class DarkPaletteStyle(val label: String) { NEUTRAL_BLACK(message(191)), DEEP_BLUE(message(192)) }

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
