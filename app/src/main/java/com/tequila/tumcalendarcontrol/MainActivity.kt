/*
 * Aufgabe: Compose-Miuix-App über die Android-Activity starten.
 * Ein-/Ausgabe: Android-Lebenszyklus -> interaktive lokale Compose-Oberfläche.
 * Nebenwirkungen: Android-Oberfläche starten; bestätigte Operationen an CalendarControlApp delegieren.
 * Nicht zuständig: Persistenz, Netzwerk-, Konto-, iCal- oder Calendar-Provider-Zugriffe.
 */
package com.tequila.tumcalendarcontrol

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
}
