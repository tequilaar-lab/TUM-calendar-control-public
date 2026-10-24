/*
 * Aufgabe: Aktionen des CalendarControlState zur Sicherung der Teilnahmeregeln und eigenen Termine (Export-/Importabsicht,
 *   Entwurf, Bestätigung; aus CalendarControlState ausgelagert, Verhalten unverändert).
 * Ein-/Ausgabe: Nutzerabsichten und SAF-Ergebnisse -> Entwurf, Regelbestand, Rückmeldungen.
 * Nebenwirkungen: Nur über commitUserData (private Präferenzen); Dateizugriff führt CalendarControlApp aus.
 * Nicht zuständig: Kodierung der Sicherung (RuleLedgerBackupCodec), Dateiauswahl oder Darstellung.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.sync.RuleLedgerBackupCodec

/** SAF-Ausgabe erst nach Dateiwahl; hier nur Oberflächenabsicht. */
fun CalendarControlState.requestRuleBackupExport() { ruleBackupExportRequested = true }

fun CalendarControlState.consumeRuleBackupExportRequest() { ruleBackupExportRequested = false }

fun CalendarControlState.resolveRuleBackupExport(saved: Boolean) { feedback = if (saved) message(265) else message(266) }

/** SAF-Inhalt nur als zu bestätigender Entwurf im Speicher; Regelbestand vor Bestätigung unverändert. */
fun CalendarControlState.requestRuleBackupImport() { ruleBackupImportRequested = true }

fun CalendarControlState.consumeRuleBackupImportRequest() { ruleBackupImportRequested = false }

fun CalendarControlState.resolveRuleBackupImport(text: String?) {
    pendingRuleBackup = text?.let(RuleLedgerBackupCodec::decode)
    feedback = if (pendingRuleBackup == null) message(267) else message(268)
}

fun CalendarControlState.cancelRuleBackupImport() { pendingRuleBackup = null }

fun CalendarControlState.confirmRuleBackupImport() {
    val backup = pendingRuleBackup ?: return
    val nextLedger = ruleLedger.copy(rules = backup.rules, manualEvents = backup.manualEvents, groupRules = backup.groupRules)
    val nextCourses = projectedCourses(nextLedger)
    if (!commitUserData(nextLedger, nextCourses)) return
    invalidateWritePreview()
    pendingRuleBackup = null
    feedback = message(269)
}
