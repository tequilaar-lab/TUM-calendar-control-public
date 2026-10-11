/*
 * Aufgabe: Drei sichtbare Teilnahmefarben und Erklärungstexte definieren.
 * Ein-/Ausgabe: Regelauflösung -> stabile Statuswerte, Farben und Anzeigetexte.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Fixplatz-Entscheidungen, Datumsgrenzen oder Kalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.domain

import com.tequila.tumcalendarcontrol.i18n.message

enum class AttendanceState(
    val label: String,
    val colorLabel: String,
    val description: String,
) {
    MUST_ATTEND(message(1), message(2), message(3)),
    CHOOSE_TO_ATTEND(message(4), message(5), message(6)),
    STOP_ATTENDING(message(7), message(8), message(9)),
}
