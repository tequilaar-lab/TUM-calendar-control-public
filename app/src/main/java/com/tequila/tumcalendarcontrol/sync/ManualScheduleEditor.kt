/*
 * Aufgabe: Eigene Campustermine mit stabiler manueller Identität erstellen, ersetzen oder entfernen.
 * Ein-/Ausgabe: Lokaler Terminbefehl -> neuer Regelbestand mit stabilen manuellen IDs.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Quellen-Parsing, Calendar Provider, Google-Ausgabe oder Zusammenlegen anhand von Titeln.
 */
package com.tequila.tumcalendarcontrol.sync

class ManualScheduleEditor {
    fun upsert(existing: List<ManualScheduleItem>, item: ManualScheduleItem): List<ManualScheduleItem> =
        (existing.filterNot { it.id == item.id } + item).sortedWith(compareBy(ManualScheduleItem::startsAt).thenBy(ManualScheduleItem::id))

    fun remove(existing: List<ManualScheduleItem>, itemId: String): List<ManualScheduleItem> = existing.filterNot { it.id == itemId }
}
