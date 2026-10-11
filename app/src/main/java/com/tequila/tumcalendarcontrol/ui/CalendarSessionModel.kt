/*
 * Aufgabe: Sitzung und laufende Anfrageidentität bei Activity-Neuerstellung erhalten.
 * Ein-/Ausgabe: Activity-Neuerstellung -> dieselbe Sitzung und laufende Anfrageidentität.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Wiederherstellung nach Prozessende, OAuth, Quellenlesen oder Kalenderaktionen.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.lifecycle.ViewModel

class CalendarSessionModel : ViewModel() {
    var state: CalendarControlState? = null
    private val started = mutableSetOf<String>()

    /** Erneute Composition darf dieselbe laufende Ausgabe nicht nochmals starten. */
    fun claim(kind: String, token: Long): Boolean = started.add("${(kind)}:${(token)}")
}
