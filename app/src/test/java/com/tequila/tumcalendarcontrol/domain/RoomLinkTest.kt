/*
 * Aufgabe: Raumlink prüfen: Erkennung der Raumnummer in typischen Ortsangaben, Suche als Ausweichlösung, leerer Ort.
 * Ein-/Ausgabe: Synthetische Ortstexte -> erwartete Raumnummern und Adressen.
 * Nebenwirkungen: Keine; kein Netzwerk.
 * Nicht zuständig: Öffnen im Browser oder Existenz des Raums.
 */
package com.tequila.tumcalendarcontrol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomLinkTest {
    @Test fun `Raumnummer in Klammern und frei stehend`() {
        assertEquals("0000.01.101", RoomLink.roomCode("Synthetik Hörsaal (0000.01.101)"))
        assertEquals("1234.EG.012A", RoomLink.roomCode("1234.eg.012a, Seminarraum"))
        assertEquals("9999.U1.020", RoomLink.roomCode("Raum 9999.U1.020"))
        assertEquals("https://nav.tum.de/room/0000.01.101", RoomLink.url("Synthetik Hörsaal (0000.01.101)"))
    }

    @Test fun `ohne Raumnummer Suche mit kodiertem Text`() {
        assertNull(RoomLink.roomCode("Synthetik Hörsaal 1"))
        assertNull("Datum ist keine Raumnummer", RoomLink.roomCode("2026.10.05"))
        assertNull("Teil einer längeren Zahl", RoomLink.roomCode("10000.01.101"))
        assertEquals("https://nav.tum.de/search?q=Synthetik%20H%C3%B6rsaal%201", RoomLink.url("  Synthetik Hörsaal 1 "))
    }

    @Test fun `leerer Ort ergibt keinen Link`() {
        assertNull(RoomLink.url(""))
        assertNull(RoomLink.url("   "))
    }
}
