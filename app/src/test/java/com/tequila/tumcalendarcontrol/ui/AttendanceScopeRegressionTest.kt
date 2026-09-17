/*
 * 职责：回归用户范围承诺与实际待写入集合。
 * 输入／输出：合成缓存与交互，输出范围、状态和日期断言。
 * 外部副作用：仅 Robolectric 沙盒；不访问网络或真实日历。
 * 不负责：真机与供应商同步验收。
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.sync.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AttendanceScopeRegressionTest {
    private val time = Instant.parse("2026-10-01T08:00:00Z")
    private fun item(uid: String, day: Long, title: String = "Synthetic VO, Group 1") =
        SourceCalendarOccurrence(uid, "instance-$day", title, time.plusSeconds(day * 86400), time.plusSeconds(day * 86400 + 3600), false)
    private fun state(vararg items: SourceCalendarOccurrence) = CalendarControlState(
        initialSyncDestinations = SyncDestinations(localCalendarEnabled = true),
        initialSourceSnapshots = SavedSourceSnapshots(latest = SavedSourceSnapshot(time, items.size, emptyMap(), items.toList())),
    )

    @Test fun futureScopeIncludesIndependentUidsButNotHistoryOtherNamesOrTypes() {
        val past = item("past", 0)
        val first = item("first", 1)
        val next = item("next", 2)
        val other = item("other", 2, "Other VO, Group 1")
        val type = item("type", 2, "Synthetic UE, Group 1")
        val state = state(past, first, next, other, type)
        state.openSourceAttendanceEditor(setOf(first.identity))
        state.pendingSourceAttendanceScope = AttendanceEditScope.CURRENT_AND_FUTURE
        state.pendingSourceAttendance = AttendanceState.MUST_ATTEND
        assertEquals(2, state.sourceAttendanceAffectedCount)
        state.confirmSourceAttendance()
        assertEquals(setOf(first.identity, next.identity), state.selectedSourceOccurrences)
        state.prepareCalendarWrite()
        assertEquals(2, state.currentDesiredEvents().size)
        assertTrue(state.courses.filter { it.id in setOf("first", "next") }.all { it.attendance == AttendanceState.MUST_ATTEND })
        assertTrue(state.courses.filter { it.id !in setOf("first", "next") }.all { it.attendance == AttendanceState.CHOOSE_TO_ATTEND })
    }

    @Test fun onlyThisRemainsExactAndWholeScopeExpands() {
        val first = item("a", 0)
        val next = item("a", 1)
        val state = state(first, next)
        state.openSourceAttendanceEditor(setOf(first.identity))
        state.pendingSourceAttendanceScope = AttendanceEditScope.ONLY_THIS_OCCURRENCE
        state.confirmSourceAttendance()
        assertEquals(setOf(first.identity), state.selectedSourceOccurrences)
        state.openSourceAttendanceEditor(setOf(first.identity))
        state.pendingSourceAttendanceScope = AttendanceEditScope.ALL_OCCURRENCES
        state.confirmSourceAttendance()
        assertEquals(setOf(first.identity, next.identity), state.selectedSourceOccurrences)
    }

    @Test fun newerScopeReplacesExistingInstanceExceptionWithinItsRange() {
        val first = item("a", 0)
        val next = item("a", 1)
        val state = state(first, next)
        state.openSourceAttendanceEditor(setOf(next.identity))
        state.pendingSourceAttendanceScope = AttendanceEditScope.ONLY_THIS_OCCURRENCE
        state.pendingSourceAttendance = AttendanceState.STOP_ATTENDING
        state.confirmSourceAttendance()
        state.openSourceAttendanceEditor(setOf(next.identity))
        assertEquals(AttendanceState.STOP_ATTENDING, state.pendingSourceAttendance)
        state.openSourceAttendanceEditor(setOf(first.identity))
        state.pendingSourceAttendanceScope = AttendanceEditScope.CURRENT_AND_FUTURE
        state.pendingSourceAttendance = AttendanceState.MUST_ATTEND
        state.confirmSourceAttendance()
        assertTrue(state.courses.all { it.attendance == AttendanceState.MUST_ATTEND })
    }

    @Test fun specifiedDateCancellationExpandsOnlyMatchingFutureCourse() {
        val first = item("a", 0)
        val next = item("b", 1)
        val cancelled = item("c", 2).copy(isCancelled = true)
        val state = state(first, next, cancelled)
        state.openSourceAttendanceEditor(setOf(first.identity))
        state.pendingSourceAttendanceScope = AttendanceEditScope.SPECIFIED_AND_FUTURE
        state.pendingSourceEffectiveDate = "2026-10-02"
        state.confirmSourceAttendance()
        assertTrue(next.identity in state.selectedSourceOccurrences)
        assertFalse(cancelled.identity in state.selectedSourceOccurrences)
        state.saveSourceOutputRange("2026-10-02", "2026-10-02")
        state.prepareCalendarWrite()
        assertEquals(listOf("a", "b"), state.currentDesiredEvents().map { it.identity.sourceUid })
    }

    @Test fun savedSnapshotSelectionIsNotCutDownByNextReadRange() {
        val items = (0 until 133).map { item("uid-$it", it.toLong()) }
        val state = state(*items.toTypedArray())
        state.selectAllSourceOccurrences()
        assertEquals(133, state.selectedSourceOccurrences.size)
        assertTrue(state.saveSourceOutputRange("2026-10-02", "2026-10-02").isEmpty())
        state.prepareCalendarWrite()
        assertEquals(133, state.currentDesiredEvents().size)
    }

    @Test fun newlyReadNarrowSnapshotOnlyOffersItsOwnSelectedItems() {
        val items = (0 until 28).map { item("uid-$it", it.toLong()) }
        val state = state(*items.toTypedArray())
        state.selectAllSourceOccurrences()
        state.prepareCalendarWrite()
        assertEquals(28, state.currentDesiredEvents().size)
    }

    @Test fun readButtonSavesDraftAndInvalidDateCannotStartRequest() {
        val state = state(item("a", 0))
        assertTrue(state.requestSourceReadForRange("2026-11-01", "2026-11-30").isEmpty())
        assertEquals("2026-11-01", state.privateSyncConfiguration.sourceRangeStartDate)
        assertTrue(state.requestSourceReadForRange("invalid", "").isNotEmpty())
        assertFalse(state.sourceReadRequested)
        assertEquals("2026-11-01", state.privateSyncConfiguration.sourceRangeStartDate)
    }

    @Test fun perWriteTargetsDoNotChangeDefaultsAndUnselectedGoogleCannotBlockLocal() {
        val first = item("a", 0)
        val state = state(first)
        state.toggleSourceOccurrenceSelection(first.identity)
        state.prepareCalendarWrite()
        state.toggleCurrentWriteTarget(OutputTarget.GOOGLE_CALENDAR)
        assertEquals(2, state.currentWriteTargets.size)
        assertFalse(state.syncDestinations.googleCalendarEnabled)
        state.requestManagedSyncExecution()
        assertFalse(state.managedSyncRequested)
        state.toggleCurrentWriteTarget(OutputTarget.GOOGLE_CALENDAR)
        state.requestCurrentWriteLocalPermission()
        state.resolveLocalCalendarPermission(true)
        assertNotNull(state.managedSyncPreview)
        state.requestManagedSyncExecution()
        assertEquals(setOf(OutputTarget.LOCAL_CALENDAR), state.currentManagedTargets())
        assertTrue(state.managedSyncRequested)
    }

    @Test fun emptyTargetsCannotWriteAndOneOffPermissionDoesNotEnableDefault() {
        val first = item("a", 0)
        val state = CalendarControlState(initialSourceSnapshots = SavedSourceSnapshots(latest = SavedSourceSnapshot(time, 1, emptyMap(), listOf(first))))
        state.toggleSourceOccurrenceSelection(first.identity)
        state.prepareCalendarWrite()
        assertNotNull(state.managedSyncPreview)
        state.requestManagedSyncExecution()
        assertFalse(state.managedSyncRequested)
        state.toggleCurrentWriteTarget(OutputTarget.LOCAL_CALENDAR)
        state.requestCurrentWriteLocalPermission()
        state.resolveLocalCalendarPermission(true)
        assertFalse(state.syncDestinations.localCalendarEnabled)
        assertEquals(setOf(OutputTarget.LOCAL_CALENDAR), state.currentWriteTargets)
    }
}
