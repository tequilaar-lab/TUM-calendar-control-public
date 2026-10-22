/*
 * Aufgabe: Gewählte Glasstufe privat auf dem Gerät speichern und laden.
 * Ein-/Ausgabe: GlassLevel <-> eigener Schlüssel „glass“ in der Datei der Darstellungspräferenzen.
 * Nebenwirkungen: Nur app-private SharedPreferences.
 * Nicht zuständig: Farbschema und Hell/Dunkel (AppearancePreferences), Zeichnen oder Geräteunterstützung.
 */
package com.tequila.tumcalendarcontrol.ui.design

import android.content.Context

/**
 * Bewusst getrennt von AppearanceSettings: Der Zustand speichert jene bei jeder Änderung als Ganzes;
 * ein zusätzliches Feld dort würde die Glaswahl mit dem Standardwert überschreiben.
 */
class GlassPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    fun load(): GlassLevel = GlassLevel.entries.firstOrNull { it.name == preferences.getString(KEY, null) } ?: GlassLevel.STANDARD

    fun save(level: GlassLevel) {
        preferences.edit().putString(KEY, level.name).apply()
    }

    private companion object {
        const val KEY = "glass"
    }
}
