/*
 * 职责：以版本化、确定性的文本格式编解码非敏感规则资料库，供私有偏好存储使用。
 * 输入／输出：输入 RuleLedger 或其文本；输出可恢复资料库，损坏数据安全退回空资料库。
 * 外部副作用：无。
 * 不负责：加密连接凭据、解析真实日历、迁移 OAuth 令牌或写入任何外部服务。
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.util.Base64

object RuleLedgerCodec {
    private const val VERSION = "v1"

    fun encode(ledger: RuleLedger): String = buildList {
        add(VERSION)
        ledger.rules.sortedBy(SyncRule::sourceUid).forEach { rule ->
            add(listOf("r", pack(rule.sourceUid), rule.hasConfirmedFixplatz, rule.defaultState.name, rule.stopAttendingFrom ?: "").joinToString("|"))
            rule.futureStateOverrides.toSortedMap().forEach { (date, state) -> add(listOf("f", pack(rule.sourceUid), date, state.name).joinToString("|")) }
            rule.instanceOverrides.toSortedMap().forEach { (key, state) -> add(listOf("i", pack(rule.sourceUid), pack(key), state.name).joinToString("|")) }
        }
        ledger.manualEvents.sortedBy(ManualScheduleItem::id).forEach { event ->
            add(listOf("m", pack(event.id), pack(event.title), event.startsAt, event.endsAt, event.attendanceState.name).joinToString("|"))
        }
        ledger.mappings.sortedWith(compareBy(StoredEventMapping::target, { it.identity.sourceUid }, { it.identity.instanceKey })).forEach { mapping ->
            add(listOf("p", mapping.target.name, pack(mapping.identity.sourceUid), pack(mapping.identity.instanceKey), pack(mapping.targetEventId), pack(mapping.fingerprint)).joinToString("|"))
        }
        ledger.lastSummary?.let { summary -> add(listOf("s", summary.completedAt, summary.createdCount, summary.updatedCount, summary.removedCount, summary.skippedCount, pack(summary.failureSummary.orEmpty())).joinToString("|")) }
    }.joinToString("\n")

    fun decode(text: String?): RuleLedger = runCatching {
        val lines = text?.lineSequence()?.filter(String::isNotBlank)?.toList().orEmpty()
        require(lines.firstOrNull() == VERSION)
        val rules = linkedMapOf<String, SyncRule>()
        val manualEvents = mutableListOf<ManualScheduleItem>()
        val mappings = mutableListOf<StoredEventMapping>()
        var summary: SyncRunSummary? = null
        lines.drop(1).forEach { line ->
            val fields = line.split('|')
            when (fields.first()) {
                "r" -> rules[unpack(fields[1])] = SyncRule(unpack(fields[1]), fields[2].toBooleanStrict(), AttendanceState.valueOf(fields[3]), fields[4].ifBlank { null }?.let(LocalDate::parse))
                "f" -> { val uid = unpack(fields[1]); rules[uid] = rules.getValue(uid).copy(futureStateOverrides = rules.getValue(uid).futureStateOverrides + (LocalDate.parse(fields[2]) to AttendanceState.valueOf(fields[3]))) }
                "i" -> { val uid = unpack(fields[1]); rules[uid] = rules.getValue(uid).copy(instanceOverrides = rules.getValue(uid).instanceOverrides + (unpack(fields[2]) to AttendanceState.valueOf(fields[3]))) }
                "m" -> manualEvents += ManualScheduleItem(unpack(fields[1]), unpack(fields[2]), Instant.parse(fields[3]), Instant.parse(fields[4]), AttendanceState.valueOf(fields[5]))
                "p" -> mappings += StoredEventMapping(OutputTarget.valueOf(fields[1]), ManagedEventIdentity(unpack(fields[2]), unpack(fields[3])), unpack(fields[4]), unpack(fields[5]))
                "s" -> summary = SyncRunSummary(Instant.parse(fields[1]), fields[2].toInt(), fields[3].toInt(), fields[4].toInt(), fields[5].toInt(), unpack(fields[6]).ifBlank { null })
                else -> error("未知资料库记录")
            }
        }
        RuleLedger(rules.values.toList(), manualEvents, mappings, summary)
    }.getOrElse { RuleLedger() }

    private fun pack(value: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    private fun unpack(value: String): String = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
}
