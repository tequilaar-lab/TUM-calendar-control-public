/*
 * Aufgabe: Compose-Miuix-App über die Android-Activity starten.
 * Ein-/Ausgabe: Android-Lebenszyklus -> interaktive lokale Compose-Oberfläche.
 * Nebenwirkungen: Android-Oberfläche starten, höchste Bildwiederholrate anfordern; bestätigte Operationen an CalendarControlApp delegieren.
 * Nicht zuständig: Persistenz, Netzwerk-, Konto-, iCal- oder Calendar-Provider-Zugriffe.
 */
package com.tequila.tumcalendarcontrol

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tequila.tumcalendarcontrol.ui.CalendarControlApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalendarControlApp()
        }
    }

    override fun onResume() {
        super.onResume()
        requestHighestRefreshRate()
    }

    /**
     * Höchste Bildwiederholrate der aktuellen Auflösung anfordern (z. B. 120 Hz). Ohne Anfrage begrenzen manche
     * Systeme (MagicOS) Apps auf 60 Bilder. Nach jedem Zurückkehren neu wählen, da Innen- und Außendisplay eines
     * Faltgeräts eigene Modi haben; im Ruhezustand senkt das System die Rate weiterhin selbst.
     */
    private fun requestHighestRefreshRate() {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display ?: return else @Suppress("DEPRECATION") windowManager.defaultDisplay
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        if (window.attributes.preferredDisplayModeId != best.modeId) {
            window.attributes = window.attributes.also { it.preferredDisplayModeId = best.modeId }
        }
    }
}
