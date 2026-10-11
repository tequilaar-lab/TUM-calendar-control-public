/*
 * Aufgabe: UID-Regeln für Einzeltermin, ganzen Kurs oder ab Datum gesammelt bearbeiten.
 * Ein-/Ausgabe: UID-Regeln, Anker und Bereich -> atomar bearbeiteter Regelbestand.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Auswahloberfläche, Datumssteuerung, Quellen-Parsing, Persistenz oder Kalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.LocalDate

sealed interface RuleEditCommand {
    val sourceUids: Set<String>
    val state: AttendanceState

    data class WholeCourse(
        override val sourceUids: Set<String>,
        override val state: AttendanceState,
    ) : RuleEditCommand

    data class FromDate(
        override val sourceUids: Set<String>,
        val effectiveFrom: LocalDate,
        override val state: AttendanceState,
    ) : RuleEditCommand

    data class Instances(
        override val sourceUids: Set<String>,
        val instanceKeys: Set<String>,
        override val state: AttendanceState,
    ) : RuleEditCommand

    /** Jeder Kurs hat eigenen Beginn für „diesen Termin“; frühere Termine am selben Tag unverändert. */
    data class FromOccurrence(
        val startsBySource: Map<String, java.time.Instant>,
        override val state: AttendanceState,
    ) : RuleEditCommand {
        override val sourceUids: Set<String> get() = startsBySource.keys
    }

    /** Einzeltermin-Mehrfachwahl überträgt pro UID den eigenen Instanzschlüssel; keine Verwechslung zwischen Kursen. */
    data class InstancesBySource(
        override val sourceUids: Set<String>,
        val instanceKeysBySourceUid: Map<String, Set<String>>,
        override val state: AttendanceState,
    ) : RuleEditCommand
}

object RuleLedgerEditor {
    /** Nur angegebene UID bearbeiten; Quellenberechtigung ist keine Regelbearbeitungssperre mehr. */
    fun apply(rules: List<SyncRule>, command: RuleEditCommand): List<SyncRule> = rules.map { rule ->
        if (rule.sourceUid !in command.sourceUids) rule else when (command) {
            is RuleEditCommand.WholeCourse -> rule.copy(
                defaultState = command.state,
                stopAttendingFrom = null,
                futureStateOverrides = emptyMap(),
                instanceOverrides = emptyMap(),
                futureInstantOverrides = emptyMap(),
            )
            is RuleEditCommand.FromDate -> rule.copy(
                futureStateOverrides = rule.futureStateOverrides + (command.effectiveFrom to command.state),
            )
            is RuleEditCommand.FromOccurrence -> rule.copy(
                futureInstantOverrides = rule.futureInstantOverrides + (command.startsBySource.getValue(rule.sourceUid) to command.state),
            )
            is RuleEditCommand.Instances -> rule.copy(
                instanceOverrides = rule.instanceOverrides + command.instanceKeys.associateWith { command.state },
            )
            is RuleEditCommand.InstancesBySource -> rule.copy(
                instanceOverrides = rule.instanceOverrides + command.instanceKeysBySourceUid
                    .getOrElse(rule.sourceUid) { emptySet() }
                    .associateWith { command.state },
            )
        }
    }
}
