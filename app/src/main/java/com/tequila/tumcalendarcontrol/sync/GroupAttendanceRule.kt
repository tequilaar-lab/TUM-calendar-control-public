/*
 * 职责：保存课程组级出席意图，并把它安全投影到后来出现的新来源 UID。
 * 输入／输出：输入不含标题正文的组键、时间线与来源课次；输出 UID 级有效规则。
 * 外部副作用：无。
 * 不负责：用标题识别目标日历事件、合并课次身份、读写来源或执行同步。
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/** 组键是完整原始标题的不可逆摘要；类型和组别不同的课程不会互相继承。 */
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
    /** 已存在的 UID 规则优先；组规则只为后来新增且尚无独立规则的 UID 提供初值。 */
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
