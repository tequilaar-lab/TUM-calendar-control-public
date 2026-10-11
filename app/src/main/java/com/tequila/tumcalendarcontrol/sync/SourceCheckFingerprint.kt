/*
 * Aufgabe: Stabilen Änderungsfingerabdruck ohne Speicherung sichtbarer Quellinhalte erzeugen.
 * Ein-/Ausgabe: Aktuelle Quelltermine -> irreversibler Änderungsfingerabdruck.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Fingerabdruckspeicherung, Terminanzeige, iCal-Lesen oder Teilnahmeentscheidungen.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.i18n.message

import java.security.MessageDigest

object SourceCheckFingerprint {
    /** Felder nur für aktuelle Speicherberechnung; finalen Hash mit Keystore verschlüsseln, niemals Klartexttermine speichern. */
    fun from(occurrences: List<SourceCalendarOccurrence>): String {
        val canonical = occurrences.sortedWith(compareBy(SourceCalendarOccurrence::sourceUid, SourceCalendarOccurrence::instanceKey)).joinToString("\n") {
            listOf(it.sourceUid, it.instanceKey, it.title, it.location, it.startsAt, it.endsAt, it.isCancelled)
                .joinToString("|") { field -> "${(field.toString().length)}:${(field)}" }
        }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
