/*
 * Aufgabe: Ausgabedifferenzen über UID und Originalinstanz berechnen.
 * Ein-/Ausgabe: UID/Originalinstanz, Solltermine und Zuordnungen -> stabile Ausgabedifferenz.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Identität anhand instabiler Felder wie Titel, echtes Löschen/Schreiben oder Kontozugriffe.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.i18n.message

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.Instant
import java.security.MessageDigest

/** Bei sichtbaren Feld-/Stiländerungen erhöhen; vorhandene Zuordnungen aktualisieren statt löschen/neu erstellen. */
private const val EVENT_OUTPUT_FORMAT_REVISION = "2026-09-status-colors-v1"

data class ManagedEventIdentity(val sourceUid: String, val instanceKey: String)

/** Unsichtbaren stabilen Zielinstanzschlüssel erzeugen; Quellidentität nicht in Titel/Ort/Beschreibung. Nur Wiederherstellung bei Zuordnungsverlust; primär UID plus Instanz. */
fun ManagedEventIdentity.opaqueKey(): String = MessageDigest.getInstance("SHA-256")
    .digest("$sourceUid\u0000$instanceKey".toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }

/**
 * Sollzustand eines App-Termins. reminderMinutes: null = Erinnerungen nicht verwaltet (unverändertes Verhalten, auch vom
 * Nutzer im Kalender ergänzte Erinnerungen bleiben unberührt und zählen nicht als Abweichung); NO_REMINDER = verwaltet,
 * keine Erinnerung der App; N > 0 = genau eine Erinnerung N Minuten vor Beginn.
 */
data class DesiredCalendarEvent(
    val identity: ManagedEventIdentity,
    val title: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val attendanceState: AttendanceState,
    val location: String = "",
    val reminderMinutes: Int? = null,
)

/** Verwaltet, aber ohne Erinnerung (nach dem Ausschalten: früher geschriebene Erinnerungen entfernen). */
const val NO_REMINDER = -1

/**
 * Gelesenen Istwert vergleichbar zum Sollwert machen: nicht verwaltet -> Erinnerung ignorieren; verwaltet -> fehlende
 * Erinnerung als NO_REMINDER.
 */
fun DesiredCalendarEvent.comparableTo(desired: DesiredCalendarEvent): DesiredCalendarEvent =
    copy(reminderMinutes = if (desired.reminderMinutes == null) null else reminderMinutes ?: NO_REMINDER)

data class ManagedEventMapping(
    val identity: ManagedEventIdentity,
    val targetEventId: String,
    val fingerprint: String,
)

enum class SyncChangeType(val label: String) {
    CREATE(message(130)), UPDATE(message(131)), REMOVE(message(132)), KEEP(message(133)),
}

data class SyncChange(
    val type: SyncChangeType,
    val identity: ManagedEventIdentity,
    val targetEventId: String? = null,
)

class SyncDiffPlanner {
    /** Normales Schreiben ergänzt/aktualisiert aktuelle Auswahl; abgewählte alte Kurse niemals löschen. Löschungen ausschließlich im separaten ausdrücklichen OwnedCalendarSyncExecutor.rollback-Pfad. */
    fun plan(desired: List<DesiredCalendarEvent>, existing: List<ManagedEventMapping>): List<SyncChange> {
        val existingByIdentity = existing.associateBy(ManagedEventMapping::identity)
        val desiredByIdentity = desired.associateBy(DesiredCalendarEvent::identity)
        // Zweite Schutzschicht: pro Identität nur eine Aktion, auch ohne vorherige Deduplizierung.
        val upserts = desiredByIdentity.values.sortedBy { it.startsAt }.map { event ->
            val existingMapping = existingByIdentity[event.identity]
            when {
                existingMapping == null -> SyncChange(SyncChangeType.CREATE, event.identity)
                existingMapping.fingerprint == event.fingerprint() ->
                    SyncChange(SyncChangeType.KEEP, event.identity, existingMapping.targetEventId)
                else -> SyncChange(SyncChangeType.UPDATE, event.identity, existingMapping.targetEventId)
            }
        }
        return upserts
    }

    /**
     * Hash darf Anzeigefelder enthalten, ist aber keine Synchronisierungsidentität; diese bleibt UID plus Instanz.
     * Erinnerung nur, wenn verwaltet: ohne Verwaltung bleibt der Hash genau wie vor dieser Funktion (keine Massenänderung).
     */
    fun DesiredCalendarEvent.fingerprint(): String = (listOf(
        EVENT_OUTPUT_FORMAT_REVISION,
        title,
        startsAt.toString(),
        endsAt.toString(),
        attendanceState.name,
        location,
    ) + listOfNotNull(reminderMinutes?.let { "reminder=$it" })).joinToString("|")
}
