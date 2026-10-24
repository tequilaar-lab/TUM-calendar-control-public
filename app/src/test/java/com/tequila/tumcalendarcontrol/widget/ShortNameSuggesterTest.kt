/*
 * Aufgabe: Vorschläge für Kurznamen prüfen: Initialen mit Nummer, erstes Wort mit Nummer, gekürzter Titel; Klammern und
 *   Veranstaltungsarten entfernt, Füllwörter nicht in Initialen, Länge begrenzt, nichts identisch zum Titel.
 * Ein-/Ausgabe: synthetische Kurstitel -> Vorschlagslisten.
 * Nebenwirkungen: Keine.
 * Nicht zuständig: Speichern oder Oberfläche.
 */
package com.tequila.tumcalendarcontrol.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortNameSuggesterTest {
    @Test fun `Initialen, erstes Wort, gekürzter Titel`() {
        assertEquals(listOf("AI1", "Analysis 1", "Analysis 1 für"), ShortNameSuggester.suggest("Analysis 1 für Informatik [MA0901]"))
        assertEquals(listOf("SS", "Signal.", "Signalverarbeitung".take(16)).take(2), ShortNameSuggester.suggest("Signalverarbeitung und Systemtheorie").take(2))
    }

    @Test fun `Veranstaltungsart und Klammern entfernt`() {
        val suggestions = ShortNameSuggester.suggest("Übung Regelungstechnik (Gruppe 3)")
        assertEquals("Regelungstechnik", suggestions.first())
        assertTrue(suggestions.none { it.contains("Gruppe") || it.contains("Übung") })
    }

    @Test fun `Grenzen`() {
        assertEquals(emptyList<String>(), ShortNameSuggester.suggest("   "))
        assertTrue("kurzer Titel nicht wiederholt", "Physik" !in ShortNameSuggester.suggest("Physik"))
        ShortNameSuggester.suggest("Grundlagen der Elektrotechnik und Informationstechnik für Ingenieurwissenschaften 2").forEach {
            assertTrue(it, it.length <= CourseShortNames.MAX_LENGTH)
        }
        assertEquals("GEII2", ShortNameSuggester.suggest("Grundlagen der Elektrotechnik und Informationstechnik für Ingenieurwissenschaften 2").first())
    }
}
