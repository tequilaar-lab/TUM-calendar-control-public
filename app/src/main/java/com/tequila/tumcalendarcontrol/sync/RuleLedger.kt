/*
 * Aufgabe: Privaten Regelbestand, Ausgabezuordnungen, eigene Termine und bereinigte Ergebnisse definieren.
 * Ein-/Ausgabe: Regeln, Zuordnungen, eigene Termine und Ergebnisse -> privater unveränderlicher Regelbestand.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Zugangsdaten, iCal-Lesen, Android Calendar Provider oder Google-Ausgabe.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.Instant

data class RuleLedger(
    val rules: List<SyncRule> = emptyList(),
    val manualEvents: List<ManualScheduleItem> = emptyList(),
    val mappings: List<StoredEventMapping> = emptyList(),
    val lastSummary: SyncRunSummary? = null,
    /** Nur irreversibler Originaltitel-Gruppenschlüssel und Dreizustands-Zeitlinie; kein Titeltext. */
    val groupRules: List<GroupAttendanceRule> = emptyList(),
)

/** Eigene Campustermine haben generierte stabile IDs, getrennt von Quell-UIDs. */
data class ManualScheduleItem(
    val id: String,
    val title: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val attendanceState: AttendanceState,
)

enum class OutputTarget { LOCAL_CALENDAR, GOOGLE_CALENDAR }

/** Zuordnung speichert nur eigene Zieltermine, keine Kalenderinhalte oder Kontodaten. */
data class StoredEventMapping(
    val target: OutputTarget,
    val identity: ManagedEventIdentity,
    val targetEventId: String,
    val fingerprint: String,
    val calendarId: String? = null,
    /** Nur private lokale Datenbank für Kurs-/Datumsbereinigung nach Quellenverlust; nicht in Sicherung exportieren. */
    val writtenEvent: DesiredCalendarEvent? = null,
)

/** Fehler nur als bereinigte Anzeigezusammenfassung; keine Antworten, Konten oder Token. */
data class SyncRunSummary(
    val completedAt: Instant,
    val createdCount: Int,
    val updatedCount: Int,
    val removedCount: Int,
    val skippedCount: Int,
    val failureSummary: String? = null,
    /** Bereinigte Ergebnisse je Ziel, etwa Anzahl gelöscht und verbleibend. */
    val detailSummary: String? = null,
)
