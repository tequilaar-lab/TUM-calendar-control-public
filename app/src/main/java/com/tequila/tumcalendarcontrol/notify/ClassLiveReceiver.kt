/*
 * Aufgabe: Empfänger für den Wecker der Live-Kursbenachrichtigung sowie Zeit-/Zeitzonen-/Sprachwechsel.
 * Ein-/Ausgabe: Broadcast -> ClassLiveScheduler.refresh.
 * Nebenwirkungen: Nur über ClassLiveScheduler (Benachrichtigung, Wecker); nicht exportiert (nur eigene und Systemaufrufe).
 * Nicht zuständig: Planung oder Inhalt der Benachrichtigung.
 */
package com.tequila.tumcalendarcontrol.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ClassLiveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ClassLiveScheduler.ACTION_REFRESH, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED ->
                ClassLiveScheduler.refresh(context)
        }
    }
}
