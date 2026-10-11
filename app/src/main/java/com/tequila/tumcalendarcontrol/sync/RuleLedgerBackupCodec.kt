/*
 * Aufgabe: Regelsicherung ohne Verbindungsdaten, Zielzuordnungen oder Synchronisierungsergebnisse kodieren.
 * Ein-/Ausgabe: Regelbestand/Sicherungstext -> Sicherung ohne Verbindungen, Zuordnungen oder Ergebnisse.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Datei-I/O, Ersetzen des aktuellen Bestands, Abonnementadressen, OAuth oder Kalenderänderungen.
 */
package com.tequila.tumcalendarcontrol.sync

data class RuleLedgerBackup(
    val rules: List<SyncRule>,
    val manualEvents: List<ManualScheduleItem>,
    val groupRules: List<GroupAttendanceRule> = emptyList(),
) {
    val summary: RuleLedgerBackupSummary get() = RuleLedgerBackupSummary(rules.size, groupRules.size, manualEvents.size)
}

data class RuleLedgerBackupSummary(val ruleCount: Int, val groupRuleCount: Int, val manualEventCount: Int)

object RuleLedgerBackupCodec {
    private const val HEADER = "tum-calendar-rule-backup-v2"
    private val SUPPORTED_HEADERS = setOf("tum-calendar-rule-backup-v1", HEADER)

    fun encode(ledger: RuleLedger): String = "$HEADER\n" + RuleLedgerCodec.encode(
        RuleLedger(rules = ledger.rules, manualEvents = ledger.manualEvents, groupRules = ledger.groupRules),
    )

    fun decode(text: String): RuleLedgerBackup? {
        val lines = text.lineSequence().toList()
        if (lines.firstOrNull() !in SUPPORTED_HEADERS) return null
        val decoded = RuleLedgerCodec.decodeOrNull(lines.drop(1).joinToString("\n")) ?: return null
        if (decoded.mappings.isNotEmpty() || decoded.lastSummary != null) return null
        return RuleLedgerBackup(decoded.rules, decoded.manualEvents, decoded.groupRules)
    }
}
