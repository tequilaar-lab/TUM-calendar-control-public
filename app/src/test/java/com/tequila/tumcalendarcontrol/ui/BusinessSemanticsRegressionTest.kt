/*
 * 职责：验证审计发现的范围继承、收藏范围、取消提示、选择恢复和提交失败边界。
 * 输入／输出：仅合成来源、规则与内存持久化回调；输出状态断言。
 * 外部副作用：无；不访问网络、账户、系统日历或真实课程。
 * 不负责：真机 UI、Google/荣耀延迟和系统杀进程行为。
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.domain.TumEventStatus
import com.tequila.tumcalendarcontrol.sync.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class BusinessSemanticsRegressionTest {
    private val t1 = Instant.parse("2026-10-01T08:00:00Z")
    private val t2 = Instant.parse("2026-10-08T08:00:00Z")

    @Test fun `全部日程保存组规则 后来新增同组 UID 继承而不同类型不继承`() {
        val old = occurrence("old-uid", "old", "Synthetic Systems VI, Group 1", t1)
        val first = state(listOf(old))
        first.openSourceAttendanceEditor(setOf(old.identity))
        first.pendingSourceAttendance = AttendanceState.MUST_ATTEND
        first.pendingSourceAttendanceScope = AttendanceEditScope.ALL_OCCURRENCES
        first.confirmSourceAttendance()

        assertEquals(1, first.ruleLedger.groupRules.size)
        val newSameGroup = occurrence("new-uid", "new", old.title, t2)
        val otherType = occurrence("other-uid", "other", "Synthetic Systems UE, Group 1", t2)
        val restored = state(listOf(newSameGroup, otherType), first.ruleLedger)

        assertEquals(AttendanceState.MUST_ATTEND, restored.courses.single { it.id == "new-uid" }.attendance)
        assertEquals(AttendanceState.CHOOSE_TO_ATTEND, restored.courses.single { it.id == "other-uid" }.attendance)
    }

    @Test fun `只改收藏也严格使用所选全部日程范围`() {
        val items = listOf(
            occurrence("uid-a", "a", "Synthetic Course VI, Group 1", t1),
            occurrence("uid-b", "b", "Synthetic Course VI, Group 1", t2),
        )
        val state = state(items)
        state.openSourceAttendanceEditor(setOf(items.first().identity))
        state.pendingSourceAttendanceScope = AttendanceEditScope.ALL_OCCURRENCES
        state.pendingSourceFavoriteAction = SourceFavoriteAction.ADD
        state.confirmSourceAttendance()

        assertTrue(state.courses.all { it.isFavorite })
        assertTrue(state.ruleLedger.rules.isEmpty())
    }

    @Test fun `持久化拒绝时不改变正式规则和收藏且不显示成功`() {
        val item = occurrence("uid-a", "a", "Synthetic Course", t1)
        val state = state(listOf(item), persistence = UserDataPersistence { _, _ -> null })
        state.openSourceAttendanceEditor(setOf(item.identity))
        state.pendingSourceAttendance = AttendanceState.MUST_ATTEND
        state.pendingSourceFavoriteAction = SourceFavoriteAction.ADD
        state.confirmSourceAttendance()

        assertTrue(state.ruleLedger.rules.isEmpty())
        assertFalse(state.courses.single().isFavorite)
        assertTrue(state.feedback.orEmpty().startsWith("未能保存"))
    }

    @Test fun `用户编辑采用持久化层返回的最新映射`() {
        val item = occurrence("uid-a", "a", "Synthetic Course", t1)
        val latestMapping = StoredEventMapping(
            OutputTarget.LOCAL_CALENDAR,
            ManagedEventIdentity("uid-a", "a"),
            "event-a",
            "fingerprint",
        )
        val state = state(listOf(item), persistence = UserDataPersistence { ledger, _ ->
            ledger.copy(mappings = listOf(latestMapping))
        })
        state.openSourceAttendanceEditor(setOf(item.identity))
        state.pendingSourceFavoriteAction = SourceFavoriteAction.ADD
        state.confirmSourceAttendance()

        assertEquals(listOf(latestMapping), state.ruleLedger.mappings)
        assertTrue(state.courses.single().isFavorite)
    }

    @Test fun `来源取消映射会在预览中单独计数且不会进入普通候选`() {
        val cancelled = occurrence("uid-a", "a", "Synthetic Cancelled", t1, cancelled = true)
        val mapping = StoredEventMapping(OutputTarget.LOCAL_CALENDAR, ManagedEventIdentity("uid-a", "a"), "event-a", "old")
        val state = state(listOf(cancelled), RuleLedger(mappings = listOf(mapping)))
        state.setSyncDestinationEnabled(SyncDestination.LOCAL_CALENDAR, true)
        state.requestManagedSyncPreview()

        assertEquals(1, state.managedCancelledMappedCount)
        assertEquals(0, state.managedSourceCandidateCount)
        assertEquals(TumEventStatus.CANCELLED, state.courses.single().sourceStatus)
    }

    @Test fun `切换快照保留仍存在的精确选择并移除取消项`() {
        val keep = occurrence("uid-a", "a", "Keep", t1)
        val removed = occurrence("uid-b", "b", "Removed", t1)
        val initial = SavedSourceSnapshot(t1, 2, emptyMap(), listOf(keep, removed))
        val workflow = SourceReadWorkflowState(SavedSourceSnapshots(latest = initial))
        workflow.includeInSelection(setOf(keep.identity, removed.identity))
        val request = workflow.beginRead()
        val cancelled = removed.copy(isCancelled = true)
        val preview = preview(listOf(keep.copy(startsAt = t2, endsAt = t2.plusSeconds(3600)), cancelled))
        workflow.resolveRead(request.token, IcalReadOnlyImportResult.Success(preview))

        assertEquals(setOf(keep.identity), workflow.selectedOccurrences)
        assertEquals(1, workflow.lastSelectionRemovedCount)
    }

    @Test fun `非取消来源在没有可靠状态字段时显示来源未说明`() {
        val state = state(listOf(occurrence("uid-a", "a", "Synthetic", t1)))
        assertEquals(TumEventStatus.UNKNOWN, state.courses.single().sourceStatus)
    }

    private fun state(
        occurrences: List<SourceCalendarOccurrence>,
        ledger: RuleLedger = RuleLedger(),
        persistence: UserDataPersistence = UserDataPersistence.InMemory,
    ) = CalendarControlState(
        initialCourses = emptyList(),
        initialRuleLedger = ledger,
        initialSourceSnapshots = SavedSourceSnapshots(latest = SavedSourceSnapshot(t1, occurrences.size, emptyMap(), occurrences)),
        userDataPersistence = persistence,
    )

    private fun preview(items: List<SourceCalendarOccurrence>) = IcalReadOnlyPreview(
        items.size,
        items.size,
        emptyMap(),
        items.minOfOrNull(SourceCalendarOccurrence::startsAt),
        items.maxOfOrNull(SourceCalendarOccurrence::endsAt),
        SourceCourseCatalog.from(items),
        items,
    )

    private fun occurrence(uid: String, key: String, title: String, start: Instant, cancelled: Boolean = false) =
        SourceCalendarOccurrence(uid, key, title, start, start.plusSeconds(3600), cancelled, "Synthetic location")
}
