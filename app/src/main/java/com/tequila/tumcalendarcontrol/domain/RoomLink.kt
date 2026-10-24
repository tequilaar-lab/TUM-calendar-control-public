/*
 * Aufgabe: Aus einer Ortsangabe einen Link auf den TUM-Raumfinder (nav.tum.de) bilden: Raumseite bei erkannter
 *   Raumnummer (Gebäude.Geschoss.Raum, z. B. 0000.00.000), sonst Suche nach dem Ortstext.
 * Ein-/Ausgabe: Ortstext -> Raumnummer bzw. https-Link oder null (leerer Ort).
 * Nebenwirkungen: Keine; der Link wird erst durch ein ausdrückliches Antippen im Browser geöffnet.
 * Nicht zuständig: Öffnen des Browsers, Prüfen, ob der Raum existiert, oder Schreiben in Kalendertermine.
 */
package com.tequila.tumcalendarcontrol.domain

import java.net.URLEncoder

object RoomLink {
    private const val BASE = "https://nav.tum.de"
    /** TUMonline-Raumschlüssel: 4-stelliges Gebäude, Geschoss (z. B. 01, EG, U1), 3-stelliger Raum mit optionalem Buchstaben. */
    private val RoomCode = Regex("""(?<![\w.])(\d{4})\.([0-9A-Z]{1,3})\.([0-9]{3}[A-Z]?)(?![\w.])""")

    fun roomCode(location: String): String? = RoomCode.find(location.uppercase())?.value

    /** Raumseite bei erkannter Nummer, sonst Suche; null ohne Ortstext. */
    fun url(location: String): String? {
        val text = location.trim()
        if (text.isEmpty()) return null
        return roomCode(text)?.let { "$BASE/room/$it" } ?: "$BASE/search?q=${URLEncoder.encode(text, Charsets.UTF_8.name()).replace("+", "%20")}"
    }
}
