/*
 * Aufgabe: Atomare Persistenzgrenze für Regeln, Favoriten und lokale Kursänderungen definieren.
 * Ein-/Ausgabe: Nächster Regelbestand und Anzeigeinformationen -> tatsächlich gespeicherter Bestand oder null.
 * Nebenwirkungen: Schnittstelle selbst rein; Android-Implementierung nur private Präferenzen.
 * Nicht zuständig: Netzwerk, Kalenderausgabe, Quellen-Parsing oder Bestätigung gefährlicher Aktionen.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.domain.Course
import com.tequila.tumcalendarcontrol.sync.RuleLedger

fun interface UserDataPersistence {
    fun commit(ledger: RuleLedger, courses: List<Course>): RuleLedger?

    companion object {
        /** Reine Zustandstests verwenden standardmäßig Speicher, ohne Android-Speichereffekte. */
        val InMemory = UserDataPersistence { ledger, _ -> ledger }
    }
}
