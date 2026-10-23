/*
 * Aufgabe: Haptik-Einstellung (Ein/Aus, Stärke) privat auf dem Gerät speichern und laden.
 * Ein-/Ausgabe: HapticSettings <-> eigene Schlüssel „haptics_enabled“/„haptics_level“ in der Darstellungsdatei.
 * Nebenwirkungen: Nur app-private SharedPreferences.
 * Nicht zuständig: Auslösen der Vibration (DesignHaptics) oder übrige Darstellung.
 */
package com.tequila.tumcalendarcontrol.ui.design

import android.content.Context

/** Getrennt von AppearanceSettings (wird dort als Ganzes überschrieben), wie die Glasstufe. */
class HapticPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    fun load(): HapticSettings = HapticSettings(
        enabled = preferences.getBoolean(KEY_ENABLED, true),
        level = HapticLevel.entries.firstOrNull { it.name == preferences.getString(KEY_LEVEL, null) } ?: HapticLevel.STANDARD,
    )

    fun save(settings: HapticSettings) {
        preferences.edit().putBoolean(KEY_ENABLED, settings.enabled).putString(KEY_LEVEL, settings.level.name).apply()
    }

    private companion object {
        const val KEY_ENABLED = "haptics_enabled"
        const val KEY_LEVEL = "haptics_level"
    }
}
