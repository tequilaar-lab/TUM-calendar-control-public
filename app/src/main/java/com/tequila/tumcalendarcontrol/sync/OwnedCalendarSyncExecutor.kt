/*
 * Aufgabe: Bestätigte Differenz ausschließlich in eigenen Kalendern ausführen und stabile Zuordnungen pflegen.
 * Ein-/Ausgabe: Bestätigte Differenz, eigenes Ziel und Zuordnungen -> aktualisierte Zuordnungen/Ergebnisse.
 * Nebenwirkungen: Nur bestätigte Aktionen im verifiziert eigenen Zielkalender.
 * Nicht zuständig: Private iCal-Beschaffung, OAuth, Zielkalendererstellung, Bestätigungsoberfläche oder Titelerkennung.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState

import com.tequila.tumcalendarcontrol.i18n.message

data class OwnedCalendarRef(val target: OutputTarget, val calendarId: String, val ownershipMarker: String)

/** Termin extern gelöscht; Ausführung darf sicher zur Wiederherstellung wechseln. */
class ManagedEventMissingException : IllegalStateException()

interface OwnedCalendarGateway {
    fun verifyOwnership(calendar: OwnedCalendarRef): Boolean
    fun findEventIdsByIdentity(calendar: OwnedCalendarRef, identity: ManagedEventIdentity): List<String>
    /** Nur ausdrücklich vollständige Bereinigung; liefert App-markierte Termin-IDs, niemals Inhalte. */
    fun findAllManagedEventIds(calendar: OwnedCalendarRef): List<String>
    /** Nur identitätsgeprüfte App-Felder lesen; unbekanntes Format ergibt null, niemals lokalen Fingerabdruck als Istwert ausgeben. */
    fun readEvent(calendar: OwnedCalendarRef, eventId: String, identity: ManagedEventIdentity): DesiredCalendarEvent? = null
    fun readFingerprint(calendar: OwnedCalendarRef, eventId: String, identity: ManagedEventIdentity): String? =
        readEvent(calendar, eventId, identity)?.let { SyncDiffPlanner().run { it.fingerprint() } }
    fun create(calendar: OwnedCalendarRef, event: DesiredCalendarEvent): String
    fun update(calendar: OwnedCalendarRef, eventId: String, event: DesiredCalendarEvent)
    /** Löschergebnis beschreibt nur diese Anfrage; keine Inhalte/IDs an Oberfläche oder Datenbank. */
    fun remove(calendar: OwnedCalendarRef, eventId: String): ManagedEventRemovalOutcome
    /**
     * Ein Lesedurchlauf über alle App-markierten Termine des eigenen Ziels (Identitätsschlüssel -> Einträge), damit die
     * Ausgabe nicht je Termin suchen und lesen muss. null = nicht unterstützt; dann gelten die Einzelabfragen.
     */
    fun snapshotManagedEvents(calendar: OwnedCalendarRef): Map<String, List<ManagedEventSnapshot>>? = null
}

/** Gelesener Zustand eines App-markierten Termins; content=null bei unbekanntem Format (gilt dann als geändert). */
data class ManagedEventSnapshot(val eventId: String, val content: ManagedEventContent?)

/** Vergleichbare Inhalte ohne Identität; Identität kommt aus dem Schlüssel des Durchlaufs. */
data class ManagedEventContent(val title: String, val startsAt: java.time.Instant, val endsAt: java.time.Instant, val state: AttendanceState, val location: String) {
    fun toEvent(identity: ManagedEventIdentity) = DesiredCalendarEvent(identity, title, startsAt, endsAt, state, location)
}

/** 404/410 bedeutet vorherige Abwesenheit, nicht eine erfolgreiche Löschung dieses Aufrufs. */
enum class ManagedEventRemovalOutcome { REMOVED_NOW, ALREADY_ABSENT }

/** Bereinigungsnachweis je Ziel ohne private Daten; remaining=null bedeutet gescheiterte Nachprüfung. */
data class ManagedCleanupSummary(
    val mappedCandidateCount: Int,
    val recoveredCandidateCount: Int,
    val requestedCount: Int,
    val removedNowCount: Int,
    val alreadyAbsentCount: Int,
    val failedCount: Int,
    val remainingManagedCount: Int?,
)

/** Bereinigte Fehlerklassen ermöglichen sichere Wiederaufnahme; keine HTTP-Antworten, Token oder Konten speichern. */
enum class TargetFailureCode {
    GOOGLE_AUTHORIZATION_EXPIRED,
}

data class TargetSyncResult(
    val target: OutputTarget,
    val mappings: List<StoredEventMapping>,
    val changes: List<SyncChange>,
    val failureSummary: String? = null,
    val failureCode: TargetFailureCode? = null,
    /** Alter App-markierter Termin ohne Zuordnung ausdrücklich gelöscht; Inhalt und Identität nicht speichern. */
    val recoveredRemovalCount: Int = 0,
    /** Nur separater Bereinigungspfad; Zielzählwerte ohne Termin-IDs oder Inhalte. */
    val cleanupSummary: ManagedCleanupSummary? = null,
    val restoredMissingCount: Int = 0,
    val failedCount: Int = 0,
    /** Wegen Anhaltens nicht bearbeitete Termine; eine erneute Ausgabe setzt dort fort. */
    val skippedCount: Int = 0,
)

/** Bereinigungsbereich separat wählen; normale Ausgabe erzeugt ihn niemals. */
sealed interface ManagedEventCleanupScope {
    data object AllManagedEvents : ManagedEventCleanupScope
    data class SelectedIdentities(
        val identities: Set<ManagedEventIdentity>,
        val byTarget: Map<OutputTarget, Set<ManagedEventIdentity>> = emptyMap(),
    ) : ManagedEventCleanupScope {
        fun forTarget(target: OutputTarget): Set<ManagedEventIdentity> = if (byTarget.isEmpty()) identities else byTarget[target].orEmpty()
    }
}

/** Bei Eigentumsfehler null Schreibaktionen; Zuordnung ausschließlich nach Ziel, UID und Originalinstanz. */
class OwnedCalendarSyncExecutor(private val planner: SyncDiffPlanner = SyncDiffPlanner()) {
    fun execute(
        calendar: OwnedCalendarRef,
        desired: List<DesiredCalendarEvent>,
        storedMappings: List<StoredEventMapping>,
        gateway: OwnedCalendarGateway,
        control: WriteControl = WriteControl(),
        onCheckpoint: (List<StoredEventMapping>) -> Unit = {},
    ): TargetSyncResult {
        if (!gateway.verifyOwnership(calendar)) return TargetSyncResult(calendar.target, storedMappings, emptyList(), message(98), failedCount = desired.size)
        val targetMappings = storedMappings.filter { it.target == calendar.target }
        val otherMappings = storedMappings.filterNot { it.target == calendar.target }
        val next = targetMappings.associateBy { it.identity }.toMutableMap()
        val completed = mutableListOf<SyncChange>()
        val failures = mutableListOf<Throwable>()
        var restored = 0
        // Jede Bestätigung prüft echte App-Identität im Ziel; alte Zuordnung beweist kein Vorhandensein.
        // Schnellweg: einmal den ganzen Zielbestand lesen (zu Beginn dieser Ausgabe) statt je Termin suchen und lesen.
        val snapshot = runCatching { gateway.snapshotManagedEvents(calendar) }.getOrNull()
        val unique = desired.distinctBy { it.identity }
        var skipped = 0
        for ((index, event) in unique.withIndex()) {
            // Anhalten nur zwischen zwei Terminen: Begonnenes wird fertig und als Prüfpunkt gesichert; Rest gilt als nicht bearbeitet.
            if (control.stopRequested) { skipped = unique.size - index; break }
            try {
                val found = snapshot?.let { it[event.identity.opaqueKey()].orEmpty() }
                val ids = found?.map(ManagedEventSnapshot::eventId) ?: gateway.findEventIdsByIdentity(calendar, event.identity)
                check(ids.size <= 1) { message(99) }
                var type = SyncChangeType.UPDATE
                val id = ids.singleOrNull()?.let { existingId ->
                    try {
                        val actual = if (found != null) found.single().content?.let { planner.run { it.toEvent(event.identity).fingerprint() } }
                        else gateway.readFingerprint(calendar, existingId, event.identity)
                        if (actual == planner.run { event.fingerprint() }) type = SyncChangeType.KEEP
                        else gateway.update(calendar, existingId, event)
                        existingId
                    } catch (_: ManagedEventMissingException) {
                        type = SyncChangeType.CREATE
                        gateway.create(calendar, event)
                    }
                } ?: run { type = SyncChangeType.CREATE; gateway.create(calendar, event) }
                next[event.identity] = StoredEventMapping(calendar.target, event.identity, id, planner.run { event.fingerprint() }, calendar.calendarId, event)
                completed += SyncChange(type, event.identity, id)
                if (type == SyncChangeType.CREATE && targetMappings.any { it.identity == event.identity }) restored += 1
                onCheckpoint(otherMappings + next.values)
            } catch (error: Exception) {
                failures += error
            }
            control.report(calendar.target, index + 1, unique.size)
        }
        return TargetSyncResult(
            calendar.target, otherMappings + next.values, completed,
            failures.firstOrNull()?.googleSafeSummary(message(100, (calendar.target.safeLabel()), (completed.size), (failures.size))),
            failures.firstNotNullOfOrNull { it.targetFailureCode() },
            restoredMissingCount = restored,
            failedCount = failures.size,
            skippedCount = skipped,
        )
    }

    /** Nur separater Bereinigungspfad. Vollständig: auch alte App-markierte Termine ohne Zuordnung entfernen. Präzise: nur gewählte UID/Originalinstanz prüfen. Nie nach Titel, Zeit oder Ort raten. */
    fun rollback(
        calendar: OwnedCalendarRef,
        storedMappings: List<StoredEventMapping>,
        gateway: OwnedCalendarGateway,
        scope: ManagedEventCleanupScope = ManagedEventCleanupScope.AllManagedEvents,
    ): TargetSyncResult {
        if (!gateway.verifyOwnership(calendar)) return TargetSyncResult(calendar.target, storedMappings, emptyList(), message(101))
        val targetMappings = storedMappings.filter { it.target == calendar.target }
        val otherMappings = storedMappings.filterNot { it.target == calendar.target }
        val selectedMappings = when (scope) {
            ManagedEventCleanupScope.AllManagedEvents -> targetMappings
            is ManagedEventCleanupScope.SelectedIdentities -> targetMappings.filter { it.identity in scope.forTarget(calendar.target) }
        }
        val recoveredIds = when (scope) {
            ManagedEventCleanupScope.AllManagedEvents -> gateway.findAllManagedEventIds(calendar)
            is ManagedEventCleanupScope.SelectedIdentities -> scope.forTarget(calendar.target).flatMap { gateway.findEventIdsByIdentity(calendar, it) }
        }
        // Veraltete Zuordnung erlaubt kein Löschen; nur jetzt im eigenen Ziel gefundene App-markierte Termine löschen.
        val mappedIds = selectedMappings.map(StoredEventMapping::targetEventId)
        val idsToRemove = recoveredIds.distinct()
        var removedNowCount = 0
        var alreadyAbsentCount = mappedIds.distinct().count { it !in recoveredIds }
        val failedIds = mutableSetOf<String>()
        idsToRemove.forEach { id ->
            when (runCatching { gateway.remove(calendar, id) }.getOrElse { failedIds += id; null }) {
                ManagedEventRemovalOutcome.REMOVED_NOW -> removedNowCount += 1
                ManagedEventRemovalOutcome.ALREADY_ABSENT -> alreadyAbsentCount += 1
                null -> Unit
            }
        }
        val remainingIds = runCatching {
            when (scope) {
                ManagedEventCleanupScope.AllManagedEvents -> gateway.findAllManagedEventIds(calendar)
                is ManagedEventCleanupScope.SelectedIdentities -> scope.forTarget(calendar.target).flatMap { gateway.findEventIdsByIdentity(calendar, it) }
            }.toSet()
        }.getOrNull()
        val completedMappedIds = selectedMappings.map(StoredEventMapping::targetEventId).filter { id ->
            id !in failedIds && (remainingIds == null || id !in remainingIds)
        }.toSet()
        val changes = selectedMappings
            .filter { it.targetEventId in completedMappedIds }
            .map { SyncChange(SyncChangeType.REMOVE, it.identity, it.targetEventId) }
        val retainedMappings = targetMappings.filterNot { mapping ->
            mapping in selectedMappings && mapping.targetEventId in completedMappedIds
        }
        val summary = ManagedCleanupSummary(
            mappedCandidateCount = mappedIds.size,
            recoveredCandidateCount = recoveredIds.toSet().count { it !in mappedIds },
            requestedCount = (mappedIds + recoveredIds).distinct().size,
            removedNowCount = removedNowCount,
            alreadyAbsentCount = alreadyAbsentCount,
            failedCount = failedIds.size,
            remainingManagedCount = remainingIds?.size,
        )
        val verificationFailed = remainingIds == null
        val failureSummary = when {
            failedIds.isNotEmpty() -> message(102, (calendar.target.safeLabel()), (failedIds.size))
            verificationFailed -> message(103, (calendar.target.safeLabel()))
            remainingIds.isNotEmpty() -> message(104, (calendar.target.safeLabel()), (remainingIds.size))
            else -> null
        }
        return TargetSyncResult(
            target = calendar.target,
            mappings = otherMappings + retainedMappings,
            changes = changes,
            failureSummary = failureSummary,
            recoveredRemovalCount = recoveredIds.toSet().count { it !in mappedIds && it !in failedIds && (remainingIds == null || it !in remainingIds) },
            cleanupSummary = summary,
        )
    }
}

private fun OutputTarget.safeLabel(): String = when (this) {
    OutputTarget.LOCAL_CALENDAR -> message(89)
    OutputTarget.GOOGLE_CALENDAR -> message(90)
}

/** Speicher-Gateway nur für Unit-Tests; simuliert Schreiben, keinen echten Calendar Provider. */
class FakeOwnedCalendarGateway(
    private val ownedCalendarIds: Set<String>,
    private val recoveredIds: Map<ManagedEventIdentity, List<String>> = emptyMap(),
    private val allManagedIds: List<String> = emptyList(),
    private val removalOutcomes: Map<String, ManagedEventRemovalOutcome> = emptyMap(),
    private val failedRemovalIds: Set<String> = emptySet(),
    private val retainedAfterRemovalIds: Set<String> = emptySet(),
) : OwnedCalendarGateway {
    val operations = mutableListOf<String>()
    private var nextId = 1
    private val currentManagedIds = allManagedIds.toMutableSet()
    private val currentRecoveredIds = recoveredIds.mapValues { (_, ids) -> ids.toMutableSet() }.toMutableMap()
    private val generated = mutableMapOf<Pair<String, ManagedEventIdentity>, String>()
    private val eventValues = mutableMapOf<Pair<String, String>, DesiredCalendarEvent>()
    override fun verifyOwnership(calendar: OwnedCalendarRef): Boolean = calendar.calendarId in ownedCalendarIds && calendar.ownershipMarker == "tum-calendar-control"
    override fun findEventIdsByIdentity(calendar: OwnedCalendarRef, identity: ManagedEventIdentity): List<String> = (currentRecoveredIds[identity].orEmpty() + listOfNotNull(generated[calendar.calendarId to identity])).distinct()
    override fun findAllManagedEventIds(calendar: OwnedCalendarRef): List<String> = (currentManagedIds + generated.filterKeys { it.first == calendar.calendarId }.values).distinct()
    override fun create(calendar: OwnedCalendarRef, event: DesiredCalendarEvent): String = "${calendar.target.name.lowercase()}-${nextId++}".also { operations += "create:$it"; generated[calendar.calendarId to event.identity] = it; eventValues[calendar.calendarId to it] = event }
    override fun update(calendar: OwnedCalendarRef, eventId: String, event: DesiredCalendarEvent) { operations += "update:$eventId"; eventValues[calendar.calendarId to eventId] = event }
    override fun readFingerprint(calendar: OwnedCalendarRef, eventId: String, identity: ManagedEventIdentity): String? = eventValues[calendar.calendarId to eventId]?.let { SyncDiffPlanner().run { it.fingerprint() } }
    override fun remove(calendar: OwnedCalendarRef, eventId: String): ManagedEventRemovalOutcome {
        operations += "remove:$eventId"
        check(eventId !in failedRemovalIds) { message(105) }
        val outcome = removalOutcomes[eventId] ?: ManagedEventRemovalOutcome.REMOVED_NOW
        if (outcome == ManagedEventRemovalOutcome.REMOVED_NOW && eventId !in retainedAfterRemovalIds) {
            currentManagedIds.remove(eventId)
            currentRecoveredIds.values.forEach { ids -> ids.remove(eventId) }
            generated.entries.removeAll { it.key.first == calendar.calendarId && it.value == eventId }
        }
        return outcome
    }
}
