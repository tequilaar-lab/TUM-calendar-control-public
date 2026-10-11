/*
 * Aufgabe: Optionales Ausgabezeitfenster streng prüfen und Überschneidungen ermitteln.
 * Ein-/Ausgabe: Optionale YYYY-MM-DD-Grenzen und Terminzeiten -> validiertes Zeitfenster und Überschneidung.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Netzwerkabruf, Konfigurationsspeicherung, Kursauswahl, Teilnahmeregeln oder Kalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.i18n.message

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object SourceDateTextParser {
    fun parseOrNull(value: String): LocalDate? = runCatching {
        LocalDate.parse(value.trim().normalizeCommonDateCharacters())
    }.getOrNull()

    private fun String.normalizeCommonDateCharacters(): String = buildString(length) {
        this@normalizeCommonDateCharacters.forEach { character ->
            append(when (character) {
                '－', '–', '—', '−' -> '-'
                in '０'..'９' -> ('0'.code + character.code - '０'.code).toChar()
                else -> character
            })
        }
    }
}

/** Beide Grenzen optional und lokal einschließlich; überlappende Termine behalten. */
data class SourceOutputDateWindow(val startsOn: LocalDate? = null, val endsOn: LocalDate? = null) {
    init { require(startsOn == null || endsOn == null || startsOn <= endsOn) { message(127) } }

    fun includes(startsAt: Instant, endsAt: Instant, zoneId: ZoneId): Boolean {
        val lowerBound = startsOn?.atStartOfDay(zoneId)?.toInstant()
        val upperExclusive = endsOn?.plusDays(1)?.atStartOfDay(zoneId)?.toInstant()
        return (lowerBound == null || endsAt > lowerBound) && (upperExclusive == null || startsAt < upperExclusive)
    }

    companion object {
        fun parseOrNull(startText: String, endText: String): SourceOutputDateWindow? {
            val start = startText.takeIf(String::isNotBlank)?.let(SourceDateTextParser::parseOrNull) ?: if (startText.isBlank()) null else return null
            val end = endText.takeIf(String::isNotBlank)?.let(SourceDateTextParser::parseOrNull) ?: if (endText.isBlank()) null else return null
            return runCatching { SourceOutputDateWindow(start, end) }.getOrNull()
        }
    }
}
