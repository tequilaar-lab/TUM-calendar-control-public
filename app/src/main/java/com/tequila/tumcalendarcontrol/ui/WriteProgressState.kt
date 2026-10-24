/*
 * Aufgabe: Fortschritt und Anhaltewunsch der laufenden Kalenderausgabe für die Oberfläche halten.
 * Ein-/Ausgabe: begin() -> WriteControl für den Ausgabefaden; dessen Meldungen -> beobachtbarer Fortschritt; requestStop().
 * Nebenwirkungen: Keine externen; Meldungen werden gedrosselt über den übergebenen post-Aufruf in den UI-Faden gereicht.
 * Nicht zuständig: Schreiben, Prüfpunkte, Ergebnisübernahme (CalendarControlState) oder Darstellung.
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tequila.tumcalendarcontrol.sync.WriteControl
import com.tequila.tumcalendarcontrol.sync.WriteProgress

internal class WriteProgressState {
    /** Letzte übernommene Meldung; null vor dem ersten geschriebenen Termin. */
    var progress by mutableStateOf<WriteProgress?>(null)
        private set
    var active by mutableStateOf(false)
        private set
    /** Anhalten angefordert; die Ausgabe beendet den laufenden Termin und meldet dann ihr Ergebnis. */
    var stopping by mutableStateOf(false)
        private set
    private var control: WriteControl? = null

    /** Anteil des aktuellen Ziels (0..1) für die Füllung der Hauptaktion; null ohne Meldung. */
    val fraction: Float? get() = progress?.let { if (it.total == 0) 1f else it.done.toFloat() / it.total }

    /**
     * Neue Ausgabe beginnen. Meldungen kommen im Ausgabefaden; höchstens alle 100 ms (die letzte eines Ziels immer)
     * werden sie über post in den UI-Faden gegeben. Verspätete Meldungen einer beendeten Ausgabe werden verworfen.
     */
    fun begin(post: (() -> Unit) -> Unit): WriteControl {
        var last = 0L
        lateinit var created: WriteControl
        created = WriteControl { reported ->
            val now = System.nanoTime()
            if (reported.done == reported.total || now - last >= 100_000_000L) {
                last = now
                post { if (control === created) progress = reported }
            }
        }
        control = created
        progress = null
        stopping = false
        active = true
        return created
    }

    fun requestStop() {
        val current = control ?: return
        current.requestStop()
        stopping = true
    }

    fun finish() {
        control = null
        progress = null
        stopping = false
        active = false
    }
}
