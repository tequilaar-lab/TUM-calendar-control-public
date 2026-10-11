/*
 * Aufgabe: Gruppenregeln speichern und sicher auf neue Quell-UIDs projizieren.
 * Ein-/Ausgabe: Gruppenschlüssel und Zeitlinie -> Anfangsregeln für neue Quell-UIDs.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Titelerkennung von Zielterminen, Zusammenlegen von Instanzidentitäten, Quellenzugriffe oder Synchronisierung.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/** Gruppenschlüssel ist irreversibler Hash des vollständigen Originaltitels; Typen/Gruppen erben nicht voneinander. */
data class GroupAttendanceRule(
    val groupKey: String,
    val defaultState: AttendanceState? = null,
    val futureStateOverrides: Map<LocalDate, AttendanceState> = emptyMap(),
    val futureInstantOverrides: Map<Instant, AttendanceState> = emptyMap(),
)

object SourceCourseGroupKey {
    fun fromTitle(title: String): String {
        val normalized = title.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
        return MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}

object GroupAttendanceRuleResolver {
    /** Vorhandene UID-Regeln haben Vorrang; Gruppenregeln initialisieren nur neue UIDs ohne eigene Regel. */
    fun materialize(ledger: RuleLedger, occurrences: List<SourceCalendarOccurrence>): List<SyncRule> {
        val existing = ledger.rules.associateBy(SyncRule::sourceUid)
        val groups = ledger.groupRules.associateBy(GroupAttendanceRule::groupKey)
        val generated = occurrences.groupBy(SourceCalendarOccurrence::sourceUid).mapNotNull { (uid, items) ->
            if (uid in existing) return@mapNotNull null
            val group = groups[SourceCourseGroupKey.fromTitle(items.first().title)] ?: return@mapNotNull null
            SyncRule(
                sourceUid = uid,
                hasConfirmedFixplatz = true,
                defaultState = group.defaultState ?: AttendanceState.CHOOSE_TO_ATTEND,
                futureStateOverrides = group.futureStateOverrides,
                futureInstantOverrides = group.futureInstantOverrides,
            )
        }
        return ledger.rules + generated
    }
}

object GroupAttendanceRuleEditor {
    fun apply(
        rules: List<GroupAttendanceRule>,
        titles: Set<String>,
        command: RuleEditCommand,
        startsByTitle: Map<String, Instant> = emptyMap(),
    ): List<GroupAttendanceRule> {
        if (command is RuleEditCommand.Instances || command is RuleEditCommand.InstancesBySource) return rules
        val keys = titles.associateWith(SourceCourseGroupKey::fromTitle)
        val current = rules.associateBy(GroupAttendanceRule::groupKey).toMutableMap()
        keys.forEach { (title, key) ->
            val rule = current[key] ?: GroupAttendanceRule(key)
            current[key] = when (command) {
                is RuleEditCommand.WholeCourse -> rule.copy(
                    defaultState = command.state,
                    futureStateOverrides = emptyMap(),
                    futureInstantOverrides = emptyMap(),
                )
                is RuleEditCommand.FromDate -> rule.copy(
                    futureStateOverrides = rule.futureStateOverrides + (command.effectiveFrom to command.state),
                )
                is RuleEditCommand.FromOccurrence -> startsByTitle[title]?.let { start ->
                    rule.copy(futureInstantOverrides = rule.futureInstantOverrides + (start to command.state))
                } ?: rule
                is RuleEditCommand.Instances,
                is RuleEditCommand.InstancesBySource,
                -> rule
            }
        }
        return current.values.sortedBy(GroupAttendanceRule::groupKey)
    }
}
