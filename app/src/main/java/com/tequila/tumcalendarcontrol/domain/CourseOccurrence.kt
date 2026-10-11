/*
 * Aufgabe: Expandierten Einzeltermin der Kalenderquelle darstellen.
 * Ein-/Ausgabe: Quell-UID und Originalinstanz -> stabile Einzelterminidentität für Regeln/Ausgabe.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: ICS-Parsing, Ableitung von Instanzidentitäten oder Deduplizierung anhand von Titeln.
 */
package com.tequila.tumcalendarcontrol.domain

import java.time.LocalDateTime

data class CourseOccurrence(
    val sourceUid: String,
    val instanceKey: String,
    val title: String,
    val start: LocalDateTime,
)
