/*
 * Aufgabe: Countdown für Prüfungen und Fristen berechnen: Zielzeit je Art, laufend/kommend, Restzeit in Tagen
 *   (Kalendertage), Stunden oder Minuten, Reihenfolge und angeheftetes Hauptziel.
 * Ein-/Ausgabe: Eigene Termine, Zeitpunkt, Zeitzone und Filter -> geordnete Countdown-Einträge.
 * Nebenwirkungen: Keine; reine Berechnung.
 * Nicht zuständig: Texte, Darstellung, Speicherung der Anheftung oder Kalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.sync

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Restzeit: ab 48 Stunden in Kalendertagen, darunter Stunden, unter einer Stunde Minuten; laufende Prüfung eigens. */
sealed interface CountdownRemaining {
    data class Days(val days: Long) : CountdownRemaining
    data class Hours(val hours: Long) : CountdownRemaining
    data class Minutes(val minutes: Long) : CountdownRemaining
    data object InProgress : CountdownRemaining
}

/** target: Prüfungsbeginn bzw. Fälligkeit (Ende der Frist). */
data class CountdownEntry(val item: ManualScheduleItem, val target: Instant, val remaining: CountdownRemaining)

object DeadlineCountdown {
    /** Zielzeit: Prüfung = Beginn, Frist = Fälligkeit; normale Termine haben keinen Countdown. */
    fun target(item: ManualScheduleItem): Instant? = when (item.kind) {
        ManualEventKind.EXAM -> item.startsAt
        ManualEventKind.DEADLINE -> item.endsAt
        ManualEventKind.NORMAL -> null
    }

    /**
     * Anstehende Prüfungen/Fristen: laufende Prüfungen zuerst, dann nach Zielzeit; vergangene fallen weg.
     * horizon begrenzt nach vorn (z. B. 30 Tage für die Startseite); kinds filtert die Arten.
     */
    fun upcoming(
        items: List<ManualScheduleItem>,
        now: Instant,
        zone: ZoneId,
        kinds: Set<ManualEventKind> = setOf(ManualEventKind.EXAM, ManualEventKind.DEADLINE),
        horizon: Duration? = null,
    ): List<CountdownEntry> = items
        .filter { it.kind in kinds }
        .mapNotNull { item ->
            val target = target(item) ?: return@mapNotNull null
            val running = item.kind == ManualEventKind.EXAM && item.startsAt <= now && now < item.endsAt
            when {
                running -> CountdownEntry(item, target, CountdownRemaining.InProgress)
                target <= now -> null
                horizon != null && target > now.plus(horizon) -> null
                else -> CountdownEntry(item, target, remaining(now, target, zone))
            }
        }
        .sortedWith(compareBy({ it.remaining != CountdownRemaining.InProgress }, CountdownEntry::target, { it.item.id }))

    fun remaining(now: Instant, target: Instant, zone: ZoneId): CountdownRemaining {
        val left = Duration.between(now, target)
        return when {
            left < Duration.ofHours(1) -> CountdownRemaining.Minutes(ceil(left.seconds, 60))
            left < Duration.ofHours(48) -> CountdownRemaining.Hours(ceil(left.seconds, 3_600))
            else -> CountdownRemaining.Days(ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), target.atZone(zone).toLocalDate()))
        }
    }

    /** Angeheftetes Hauptziel, solange es noch ansteht; sonst das nächste (Rückfall bei Ablauf oder Löschung). */
    fun featured(entries: List<CountdownEntry>, pinnedId: String?): CountdownEntry? =
        entries.firstOrNull { it.item.id == pinnedId } ?: entries.firstOrNull()

    private fun ceil(seconds: Long, unit: Long): Long = ((seconds + unit - 1) / unit).coerceAtLeast(1)
}
