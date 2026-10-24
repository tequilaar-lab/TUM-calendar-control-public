/*
 * Aufgabe: Compose-Miuix-App über die Android-Activity starten.
 * Ein-/Ausgabe: Android-Lebenszyklus -> interaktive lokale Compose-Oberfläche.
 * Nebenwirkungen: Android-Oberfläche starten, höchste Bildwiederholrate anfordern; bestätigte Operationen an CalendarControlApp delegieren;
 *   Einstiegsziele aus Hinweisen/Widgets/Verknüpfungen (nur Navigation) weiterreichen; Verknüpfungen beim Start
 *   veröffentlichen; Widgets beim Verlassen neu zeichnen.
 * Nicht zuständig: Persistenz, Netzwerk-, Konto-, iCal- oder Calendar-Provider-Zugriffe.
 */
package com.tequila.tumcalendarcontrol

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tequila.tumcalendarcontrol.ui.AppEntry
import com.tequila.tumcalendarcontrol.ui.AppEntryIntents
import com.tequila.tumcalendarcontrol.ui.CalendarControlApp
import com.tequila.tumcalendarcontrol.shortcuts.AppShortcuts
import com.tequila.tumcalendarcontrol.widget.ScheduleWidgets

class MainActivity : ComponentActivity() {
    /** Noch nicht angewandtes Einstiegsziel; nach Neuaufbau (savedInstanceState) nicht erneut anwenden. */
    private var pendingEntry by mutableStateOf<AppEntry?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) pendingEntry = AppEntryIntents.parse(intent)
        AppShortcuts.publish(this)
        setContent {
            CalendarControlApp(entry = pendingEntry, onEntryHandled = { pendingEntry = null })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        AppEntryIntents.parse(intent)?.let { pendingEntry = it }
    }

    /** Beim Verlassen Widgets neu zeichnen (z. B. geänderte Einstellungen oder Zuordnungen); kein Netzwerk, kein Kalender. */
    override fun onStop() {
        super.onStop()
        ScheduleWidgets.refreshAll(this)
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
