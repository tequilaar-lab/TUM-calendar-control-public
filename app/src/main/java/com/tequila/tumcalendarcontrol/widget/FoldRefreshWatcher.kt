/*
 * Aufgabe: Widgets beim Auf-/Zuklappen neu zeichnen. Gerätefund HONOR: Beide Bildschirme teilen dieselben Widget-Inhalte,
 *   der Startbildschirm meldet das Klappen nicht; ohne Neuzeichnen galten innen die Ränder des Außenbildschirms (Block
 *   niedriger als die Systemkarte, unterste Zeile abgeschnitten).
 * Ein-/Ausgabe: Konfigurationsänderung des App-Prozesses (kleinste Breite wechselt zwischen schmal und groß) -> refreshAll.
 * Nebenwirkungen: Ein Prozess-Rückruf (nur solange der Prozess lebt); kein Netzwerk, kein Kalender, keine Daten.
 * Nicht zuständig: Zeichnen selbst oder Auswahl der Ränder (SystemWidgetLook).
 */
package com.tequila.tumcalendarcontrol.widget

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import java.util.concurrent.atomic.AtomicBoolean

object FoldRefreshWatcher {
    private val registered = AtomicBoolean(false)
    @Volatile private var lastLarge: Boolean? = null

    /** Einmal je Prozess anmelden (idempotent); merkt sich den aktuellen Zustand. */
    fun watch(context: Context) {
        val app = context.applicationContext
        if (lastLarge == null) lastLarge = SystemWidgetLook.largeScreen(app)
        if (!registered.compareAndSet(false, true)) return
        app.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                if (changed(lastLarge, newConfig.smallestScreenWidthDp)) {
                    lastLarge = newConfig.smallestScreenWidthDp >= 600
                    ScheduleWidgets.refreshAll(app)
                }
            }

            @Deprecated("Erforderlich durch ComponentCallbacks; nichts zu tun.")
            override fun onLowMemory() = Unit
        })
    }

    /** Reiner Vergleich (testbar): Wechsel zwischen schmal (< 600 dp) und groß. */
    internal fun changed(lastLarge: Boolean?, smallestWidthDp: Int): Boolean = lastLarge != null && lastLarge != (smallestWidthDp >= 600)
}
