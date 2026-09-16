/*
 * 职责：以单次、整课或从指定日期起三种作用域批量编辑来源课程的本地规则。
 * 输入／输出：输入规则集合与明确编辑命令；输出更新后的规则集合，不改变输入对象。
 * 外部副作用：无。
 * 不负责：选择 UI、日期控件、来源解析、持久化或写入日历。
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

    /** 每门课的“本次”有自己的开始时刻；不影响同日更早课次。 */
    data class FromOccurrence(
        val startsBySource: Map<String, java.time.Instant>,
        override val state: AttendanceState,
    ) : RuleEditCommand {
        override val sourceUids: Set<String> get() = startsBySource.keys
    }

    /** 多选“仅本次”按 UID 各自携带实例键，绝不把 A 课实例误应用给 B 课。 */
    data class InstancesBySource(
        override val sourceUids: Set<String>,
        val instanceKeysBySourceUid: Map<String, Set<String>>,
        override val state: AttendanceState,
    ) : RuleEditCommand
}

object RuleLedgerEditor {
    /** 只编辑命令指定 UID；来源资格不再是规则编辑门槛。 */
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
