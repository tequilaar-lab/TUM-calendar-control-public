/*
 * Aufgabe: Nachrichtenherkunft und unveränderte Nutzereingaben überprüfen.
 * Ein-/Ausgabe: synthetische Strings und Sprachvorlagen -> JUnit-Ergebnisse.
 * Nebenwirkungen: nur Speicher; keine Android-Daten, Geräte oder Kalender.
 * Nicht zuständig: echte Quellen oder Netzwerkübertragung.
 */
package com.tequila.tumcalendarcontrol.i18n

import org.junit.Assert.*
import org.junit.Test

class AppMessageTest {
    private val german: (Int) -> String = { key -> when (key) {
        197 -> "Abbrechen"
        372 -> "{0} Termine"
        477 -> "Betroffene Ziele: {0}"
        677 -> "; "
        678 -> ", "
        else -> messageSources.getValue(key)
    } }

    @Test fun userTextEqualToAppTextIsNotTranslated() {
        val appOwned = message(197)
        val userInput = String(appOwned.toCharArray())
        assertEquals("Abbrechen", renderMessage(appOwned, german))
        assertEquals(userInput, renderMessage(userInput, german))
    }

    @Test fun nestedMessagesPreserveUserArgumentsAndPlaceholderCharacters() {
        val userTitle = "中文 {0} \$1"
        val nested = message(477, concatenateMessages(userTitle, ": ", message(372, 12)))
        assertEquals("Betroffene Ziele: 中文 {0} \$1: 12 Termine", renderMessage(nested, german))
        assertEquals("将影响：中文 {0} \$1: 12 个课次", nested)
    }

    @Test fun joinsKeepCanonicalValueAndLocalizeEveryPart() {
        val value = joinMessages(listOf(message(197), message(372, 2)))
        assertEquals("取消；2 个课次", value)
        assertEquals("Abbrechen; 2 Termine", renderMessage(value, german))
        assertEquals("", joinMessages(emptyList()))
    }

    @Test fun persistedOwnedSummaryRestoresWithoutChangingStoredValue() {
        val persisted = "将影响：取消"
        val restored = restoreOwnedMessage(persisted)
        assertEquals(persisted, restored)
        assertEquals("Betroffene Ziele: Abbrechen", renderMessage(restored, german))
    }

    @Test fun retainedMessagesSurviveUnrelatedMessageCreation() {
        val retained = message(372, 3)
        repeat(10_000) { message(372, it) }
        assertEquals("3 Termine", renderMessage(retained, german))
    }
}
