/*
 * Aufgabe: Regelbestand in versioniertem deterministischem Textformat für private Speicherung kodieren.
 * Ein-/Ausgabe: Regelbestand/versionierter Text -> deterministische Kodierung bzw. sicherer Wiederherstellungswert.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Verschlüsselung von Zugangsdaten, echte Kalender, OAuth-Tokenmigration oder externe Dienste.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.i18n.message

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.util.Base64

object RuleLedgerCodec {
    private const val VERSION = "v2"
    private val SUPPORTED_VERSIONS = setOf("v1", VERSION)

    fun encode(ledger: RuleLedger): String = buildList {
        add(VERSION)
        ledger.rules.sortedBy(SyncRule::sourceUid).forEach { rule ->
            add(listOf("r", pack(rule.sourceUid), rule.hasConfirmedFixplatz, rule.defaultState.name, rule.stopAttendingFrom ?: "").joinToString("|"))
            rule.futureStateOverrides.toSortedMap().forEach { (date, state) -> add(listOf("f", pack(rule.sourceUid), date, state.name).joinToString("|")) }
            rule.instanceOverrides.toSortedMap().forEach { (key, state) -> add(listOf("i", pack(rule.sourceUid), pack(key), state.name).joinToString("|")) }
            rule.futureInstantOverrides.toSortedMap().forEach { (time, state) -> add(listOf("t", pack(rule.sourceUid), time, state.name).joinToString("|")) }
        }
        ledger.groupRules.sortedBy(GroupAttendanceRule::groupKey).forEach { rule ->
            add(listOf("g", rule.groupKey, rule.defaultState?.name.orEmpty()).joinToString("|"))
            rule.futureStateOverrides.toSortedMap().forEach { (date, state) -> add(listOf("gf", rule.groupKey, date, state.name).joinToString("|")) }
            rule.futureInstantOverrides.toSortedMap().forEach { (time, state) -> add(listOf("gt", rule.groupKey, time, state.name).joinToString("|")) }
        }
        ledger.manualEvents.sortedBy(ManualScheduleItem::id).forEach { event ->
            add(listOf("m", pack(event.id), pack(event.title), event.startsAt, event.endsAt, event.attendanceState.name).joinToString("|"))
        }
        ledger.mappings.sortedWith(compareBy(StoredEventMapping::target, { it.identity.sourceUid }, { it.identity.instanceKey })).forEach { mapping ->
            val event = mapping.writtenEvent
            add(listOf("p", mapping.target.name, pack(mapping.identity.sourceUid), pack(mapping.identity.instanceKey), pack(mapping.targetEventId), pack(mapping.fingerprint), pack(mapping.calendarId.orEmpty()), pack(event?.title.orEmpty()), event?.startsAt ?: "", event?.endsAt ?: "", event?.attendanceState?.name ?: "", pack(event?.location.orEmpty())).joinToString("|"))
        }
        ledger.lastSummary?.let { summary -> add(listOf("s", summary.completedAt, summary.createdCount, summary.updatedCount, summary.removedCount, summary.skippedCount, pack(summary.failureSummary.orEmpty()), pack(summary.detailSummary.orEmpty())).joinToString("|")) }
    }.joinToString("\n")

    fun decode(text: String?): RuleLedger = decodeOrNull(text) ?: RuleLedger()

    /** Sicherungsimport unterscheidet gültige leere Datenbank von unlesbarer Datei; Präferenzen behalten sicheren leeren Standard. */
    fun decodeOrNull(text: String?): RuleLedger? = runCatching {
        val lines = text?.lineSequence()?.filter(String::isNotBlank)?.toList().orEmpty()
        require(lines.firstOrNull() in SUPPORTED_VERSIONS)
        val rules = linkedMapOf<String, SyncRule>()
        val groupRules = linkedMapOf<String, GroupAttendanceRule>()
        val manualEvents = mutableListOf<ManualScheduleItem>()
        val mappings = mutableListOf<StoredEventMapping>()
        var summary: SyncRunSummary? = null
        lines.drop(1).forEach { line ->
            val fields = line.split('|')
            when (fields.first()) {
                "r" -> rules[unpack(fields[1])] = SyncRule(unpack(fields[1]), fields[2].toBooleanStrict(), AttendanceState.valueOf(fields[3]), fields[4].ifBlank { null }?.let(LocalDate::parse))
                "f" -> { val uid = unpack(fields[1]); rules[uid] = rules.getValue(uid).copy(futureStateOverrides = rules.getValue(uid).futureStateOverrides + (LocalDate.parse(fields[2]) to AttendanceState.valueOf(fields[3]))) }
                "i" -> { val uid = unpack(fields[1]); rules[uid] = rules.getValue(uid).copy(instanceOverrides = rules.getValue(uid).instanceOverrides + (unpack(fields[2]) to AttendanceState.valueOf(fields[3]))) }
                "t" -> { val uid = unpack(fields[1]); rules[uid] = rules.getValue(uid).copy(futureInstantOverrides = rules.getValue(uid).futureInstantOverrides + (Instant.parse(fields[2]) to AttendanceState.valueOf(fields[3]))) }
                "g" -> groupRules[fields[1]] = GroupAttendanceRule(fields[1], fields.getOrNull(2)?.ifBlank { null }?.let(AttendanceState::valueOf))
                "gf" -> { val key = fields[1]; groupRules[key] = groupRules.getValue(key).copy(futureStateOverrides = groupRules.getValue(key).futureStateOverrides + (LocalDate.parse(fields[2]) to AttendanceState.valueOf(fields[3]))) }
                "gt" -> { val key = fields[1]; groupRules[key] = groupRules.getValue(key).copy(futureInstantOverrides = groupRules.getValue(key).futureInstantOverrides + (Instant.parse(fields[2]) to AttendanceState.valueOf(fields[3]))) }
                "m" -> manualEvents += ManualScheduleItem(unpack(fields[1]), unpack(fields[2]), Instant.parse(fields[3]), Instant.parse(fields[4]), AttendanceState.valueOf(fields[5]))
                "p" -> {
                    val identity = ManagedEventIdentity(unpack(fields[2]), unpack(fields[3]))
                    val event = if (!fields.getOrNull(8).isNullOrBlank()) DesiredCalendarEvent(identity, unpack(fields[7]), Instant.parse(fields[8]), Instant.parse(fields[9]), AttendanceState.valueOf(fields[10]), unpack(fields[11])) else null
                    mappings += StoredEventMapping(OutputTarget.valueOf(fields[1]), identity, unpack(fields[4]), unpack(fields[5]), fields.getOrNull(6)?.let(::unpack)?.ifBlank { null }, event)
                }
                "s" -> summary = SyncRunSummary(
                    Instant.parse(fields[1]),
                    fields[2].toInt(),
                    fields[3].toInt(),
                    fields[4].toInt(),
                    fields[5].toInt(),
                    unpack(fields[6]).ifBlank { null },
                    fields.getOrNull(7)?.let(::unpack)?.ifBlank { null },
                )
                else -> error(message(120))
            }
        }
        RuleLedger(rules.values.toList(), manualEvents, mappings, summary, groupRules.values.toList())
    }.getOrNull()

    private fun pack(value: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    private fun unpack(value: String): String = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
}
