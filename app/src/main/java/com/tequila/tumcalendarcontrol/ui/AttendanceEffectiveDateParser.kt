/*
 * Aufgabe: Datumsentwürfe normalisieren und mit LocalDate streng prüfen.
 * Ein-/Ausgabe: Formulardatum -> streng validiertes LocalDate oder Fehler.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Datumsreihenfolge, Instanzauswahl, Regelspeicherung oder Synchronisierung.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.sync.SourceDateTextParser
import java.time.LocalDate

/** Nur Eingabemethoden-Unterschiede bei Zahlen/Bindestrichen normalisieren; LocalDate prüft Format und Datum streng. */
object AttendanceEffectiveDateParser { fun parseOrNull(value: String): LocalDate? = SourceDateTextParser.parseOrNull(value) }
