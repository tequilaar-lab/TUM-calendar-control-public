/*
 * 职责：验证资料库编解码、批量规则作用域和未来状态时间线的离线行为。
 * 输入／输出：输入合成规则与编辑命令；输出确定性规则及解析状态断言。
 * 外部副作用：无。
 * 不负责：Android 偏好、网络、真实日历或账户验证。
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleLedgerEditorTest {
    private val firstCourse = SyncRule("synthetic-a", true, AttendanceState.CHOOSE_TO_ATTEND)
    private val secondCourse = SyncRule("synthetic-b", false, AttendanceState.MUST_ATTEND)

    @Test fun `从日期起批量编辑不改写此前实例`() {
        val updated = RuleLedgerEditor.apply(listOf(firstCourse, secondCourse), RuleEditCommand.FromDate(setOf("synthetic-a", "synthetic-b"), LocalDate.parse("2026-10-10"), AttendanceState.STOP_ATTENDING))
        val rule = updated.first()
        assertEquals(AttendanceState.CHOOSE_TO_ATTEND, SyncRuleResolver.resolve(rule, occurrence("2026-10-03T08:00:00Z"), ZoneOffset.UTC))
        assertEquals(AttendanceState.STOP_ATTENDING, SyncRuleResolver.resolve(rule, occurrence("2026-10-10T08:00:00Z"), ZoneOffset.UTC))
        assertEquals(AttendanceState.STOP_ATTENDING, SyncRuleResolver.resolve(updated.last(), occurrence("2026-10-10T08:00:00Z").copy(sourceUid = "synthetic-b"), ZoneOffset.UTC))
    }

    @Test fun `整课与单次编辑可以批量应用于全部来源课程`() {
        val all = RuleLedgerEditor.apply(listOf(firstCourse, secondCourse), RuleEditCommand.WholeCourse(setOf("synthetic-a", "synthetic-b"), AttendanceState.MUST_ATTEND))
        val one = RuleLedgerEditor.apply(all, RuleEditCommand.Instances(setOf("synthetic-a"), setOf("2026-10-17T08:00:00Z"), AttendanceState.STOP_ATTENDING))
        assertEquals(AttendanceState.MUST_ATTEND, one.first().defaultState)
        assertEquals(AttendanceState.STOP_ATTENDING, one.first().instanceOverrides.getValue("2026-10-17T08:00:00Z"))
        assertEquals(AttendanceState.MUST_ATTEND, one.last().defaultState)
    }

    @Test fun `多选仅本次按来源 UID 隔离实例键`() {
        val updated = RuleLedgerEditor.apply(
            listOf(firstCourse, secondCourse),
            RuleEditCommand.InstancesBySource(
                sourceUids = setOf("synthetic-a", "synthetic-b"),
                instanceKeysBySourceUid = mapOf("synthetic-a" to setOf("a-instance"), "synthetic-b" to setOf("b-instance")),
                state = AttendanceState.STOP_ATTENDING,
            ),
        )
        assertEquals(AttendanceState.STOP_ATTENDING, updated.first().instanceOverrides["a-instance"])
        assertEquals(null, updated.first().instanceOverrides["b-instance"])
        assertEquals(AttendanceState.STOP_ATTENDING, updated.last().instanceOverrides["b-instance"])
        assertEquals(null, updated.last().instanceOverrides["a-instance"])
    }

    @Test fun `资料库可往返特殊字符 映射和脱敏摘要 损坏数据安全清空`() {
        val ledger = RuleLedger(
            rules = listOf(firstCourse.copy(instanceOverrides = mapOf("k|中文" to AttendanceState.MUST_ATTEND))),
            manualEvents = listOf(ManualScheduleItem("manual-1", "合成 | 事项", Instant.parse("2026-10-01T08:00:00Z"), Instant.parse("2026-10-01T09:00:00Z"), AttendanceState.CHOOSE_TO_ATTEND)),
            mappings = listOf(StoredEventMapping(OutputTarget.LOCAL_CALENDAR, ManagedEventIdentity("synthetic-a", "k|中文"), "event-1", "fingerprint")),
            lastSummary = SyncRunSummary(Instant.parse("2026-10-01T10:00:00Z"), 1, 2, 3, 4, "仅合成失败摘要", "Google 日历：已删除 3 条，复核剩余 0 条"),
            groupRules = listOf(GroupAttendanceRule("synthetic-group", AttendanceState.MUST_ATTEND, futureStateOverrides = mapOf(LocalDate.parse("2026-11-01") to AttendanceState.STOP_ATTENDING))),
        )
        assertEquals(ledger, RuleLedgerCodec.decode(RuleLedgerCodec.encode(ledger)))
        assertEquals("uid", RuleLedgerCodec.decode("v1\nr|dWlk|true|MUST_ATTEND|").rules.single().sourceUid)
        assertEquals(RuleLedger(), RuleLedgerCodec.decode("not-a-ledger"))
    }

    private fun occurrence(start: String) = SourceCalendarOccurrence("synthetic-a", start, "合成课程", Instant.parse(start), Instant.parse(start).plusSeconds(3600), false)
}
