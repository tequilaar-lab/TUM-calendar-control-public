/*
 * Aufgabe: Wählbare Startsymbole (fünf Aktivitätsaliasse) beschreiben und das aktive Symbol lesen bzw. wechseln.
 * Ein-/Ausgabe: Kontext und gewähltes Symbol -> Aktivierungszustand der Aliasse im PackageManager.
 * Nebenwirkungen: Ändert nur den Aktivierungszustand der app-eigenen Aliasse (ohne die App zu beenden);
 *   der Startbildschirm aktualisiert das Symbol selbst, je nach Gerät mit kurzer Verzögerung.
 * Nicht zuständig: Darstellung der Auswahl (AppIconSelector), Benachrichtigungssymbole oder Systemeinstellungen.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import com.tequila.tumcalendarcontrol.R

/** Ein Startsymbol: Alias im Manifest, Name und Ebenen für die Vorschau. Reihenfolge = Anzeige; erstes ist Standard. */
enum class AppIconStyle(val alias: String, val label: String, @param:DrawableRes val background: Int, @param:DrawableRes val foreground: Int) {
    GLASS(".LauncherGlass", message(734), R.drawable.ic_launcher_glass_background, R.drawable.ic_launcher_glass_foreground),
    PAPER(".LauncherPaper", message(735), R.drawable.ic_launcher_paper_background, R.drawable.ic_launcher_paper_foreground),
    NIGHT(".LauncherNight", message(736), R.drawable.ic_launcher_night_background, R.drawable.ic_launcher_night_foreground),
    GRID(".LauncherGrid", message(737), R.drawable.ic_launcher_grid_background, R.drawable.ic_launcher_grid_foreground),
    ORBIT(".LauncherOrbit", message(738), R.drawable.ic_launcher_orbit_background, R.drawable.ic_launcher_orbit_foreground),
}

internal object AppIconSwitcher {
    private fun component(context: Context, style: AppIconStyle) = ComponentName(context.packageName, "com.tequila.tumcalendarcontrol" + style.alias)

    /** Aktiver Alias; „Standard“ bedeutet Manifestwert (nur GLASS ist dort aktiviert). */
    fun current(context: Context): AppIconStyle = AppIconStyle.entries.firstOrNull { style ->
        when (context.packageManager.getComponentEnabledSetting(component(context, style))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> style == AppIconStyle.GLASS
            else -> false
        }
    } ?: AppIconStyle.GLASS

    /** Erst neuen Alias aktivieren, dann die übrigen deaktivieren: nie ohne Starteintrag, App läuft weiter. */
    fun select(context: Context, chosen: AppIconStyle) {
        val manager = context.packageManager
        manager.setComponentEnabledSetting(component(context, chosen), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        AppIconStyle.entries.filter { it != chosen }.forEach {
            manager.setComponentEnabledSetting(component(context, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
