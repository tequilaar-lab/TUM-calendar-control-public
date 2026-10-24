/*
 * Aufgabe: Laufende Ausgabe beobachtbar und anhaltbar machen: Fortschritt je Ziel melden, Anhaltewunsch entgegennehmen.
 * Ein-/Ausgabe: Ausführer meldet (Ziel, erledigt, gesamt); Oberfläche setzt den Anhaltewunsch.
 * Nebenwirkungen: Keine eigenen; ruft nur den übergebenen Rückruf auf (im Ausgabefaden, nicht im UI-Faden).
 * Nicht zuständig: Schreiben, Prüfpunkte, Drosselung oder Darstellung des Fortschritts.
 */
package com.tequila.tumcalendarcontrol.sync

import java.util.concurrent.atomic.AtomicBoolean

/** Stand einer Ausgabe: erledigte von gesamt Terminen des aktuellen Ziels; Ziel x von y. */
data class WriteProgress(
    val target: OutputTarget,
    val done: Int,
    val total: Int,
    val targetIndex: Int,
    val targetCount: Int,
)

/**
 * Anhalten wirkt nur zwischen zwei Terminen: ein begonnener Termin wird fertig geschrieben und als Prüfpunkt gespeichert.
 * Bereits geschriebene Termine bleiben im Kalender; eine erneute Ausgabe erkennt sie als unverändert.
 */
class WriteControl(private val onProgress: (WriteProgress) -> Unit = {}) {
    private val stop = AtomicBoolean(false)
    @Volatile internal var targetIndex = 0
    @Volatile internal var targetCount = 1

    val stopRequested: Boolean get() = stop.get()

    fun requestStop() { stop.set(true) }

    internal fun report(target: OutputTarget, done: Int, total: Int) = onProgress(WriteProgress(target, done, total, targetIndex, targetCount))
}
