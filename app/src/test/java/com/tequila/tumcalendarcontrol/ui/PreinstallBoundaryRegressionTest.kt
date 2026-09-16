/*
 * 职责：验证缓存恢复、课次时间线、目标选择和旧日程清理边界。
 * 输入／输出：合成来源与纯内存状态；输出正确行为断言。
 * 外部副作用：无。
 * 不负责：真实来源、账户、日历、手机或截图验收。
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.domain.*
import com.tequila.tumcalendarcontrol.sync.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class PreinstallBoundaryRegressionTest {
    private val start = Instant.parse("2026-10-01T08:00:00Z")
    private fun occurrence(uid: String = "synthetic", key: String = "one", at: Instant = start) =
        SourceCalendarOccurrence(uid, key, "Synthetic VO", at, at.plusSeconds(3600), false, "")
    private fun snapshot(vararg items: SourceCalendarOccurrence) = SavedSourceSnapshot(start, items.size, emptyMap(), items.toList())
    private fun state(vararg items: SourceCalendarOccurrence) = CalendarControlState(
        initialSourceSnapshots = SavedSourceSnapshots(snapshot(*items)),
        initialSyncDestinations = SyncDestinations(localCalendarEnabled = true),
    )

    @Test fun corruptedLatestDoesNotDiscardPrevious() {
        val original = SavedSourceSnapshots(snapshot(occurrence()), snapshot(occurrence("previous")))
        val damaged = SourceSnapshotCodec.encode(original).replace("latest|meta|${start.toEpochMilli()}", "latest|meta|invalid")
        val recovered = SourceSnapshotCodec.decode(damaged)
        assertNull(recovered.latest)
        assertEquals(original.previous, recovered.active)
    }

    @Test fun currentAndFutureStartsAtTheSelectedTimeNotTodayOrEarlierSameDay() {
        val early = occurrence()
        val late = occurrence(key = "two", at = start.plusSeconds(7200))
        val state = state(early, late)
        state.openCourse(state.courses.last().browseKey)
        state.pendingAttendance = AttendanceState.MUST_ATTEND
        state.pendingAttendanceScope = AttendanceEditScope.CURRENT_AND_FUTURE
        state.confirmAttendance()
        val rule = RuleLedgerCodec.decode(RuleLedgerCodec.encode(state.ruleLedger)).rules.single()
        assertEquals(AttendanceState.CHOOSE_TO_ATTEND, SyncRuleResolver.resolve(rule, early, ZoneId.of("Europe/Berlin")))
        assertEquals(AttendanceState.MUST_ATTEND, SyncRuleResolver.resolve(rule, late, ZoneId.of("Europe/Berlin")))
    }

    @Test fun batchCurrentAndFutureHasSeparateCutoffForEachCourse() {
        val a = occurrence("a")
        val bEarly = occurrence("b", "early")
        val bLate = occurrence("b", "late", start.plusSeconds(86400))
        val state = state(a, bEarly, bLate)
        state.openSourceAttendanceEditor(setOf(a.identity, bLate.identity))
        state.pendingSourceAttendance = AttendanceState.STOP_ATTENDING
        state.pendingSourceAttendanceScope = AttendanceEditScope.CURRENT_AND_FUTURE
        state.confirmSourceAttendance()
        val rule = state.ruleLedger.rules.first { it.sourceUid == "b" }
        assertEquals(AttendanceState.CHOOSE_TO_ATTEND, SyncRuleResolver.resolve(rule, bEarly, ZoneId.of("Europe/Berlin")))
        assertEquals(AttendanceState.STOP_ATTENDING, SyncRuleResolver.resolve(rule, bLate, ZoneId.of("Europe/Berlin")))
    }

    @Test fun structuredTimeOverridePreservesIdentity() {
        val item = occurrence()
        val event = SyncCandidateFactory().build(listOf(item), setOf(item.identity), rules = emptyList(), manualEvents = emptyList(),
            overrides = mapOf(item.sourceUid to CourseLocalOverride(meetingTime = CourseMeetingTime(CourseWeekday.FRIDAY, 14, 0, 16, 0)))).single()
        assertEquals(ManagedEventIdentity(item.sourceUid, item.instanceKey), event.identity)
        assertEquals(Instant.parse("2026-10-02T12:00:00Z"), event.startsAt)
        assertEquals(Instant.parse("2026-10-02T14:00:00Z"), event.endsAt)
    }

    @Test fun removedSourceWrittenEventRemainsAvailableForScopedCleanup() {
        val event = DesiredCalendarEvent(ManagedEventIdentity("old", "old-instance"), "Synthetic old", start, start.plusSeconds(3600), AttendanceState.MUST_ATTEND)
        val mapping = StoredEventMapping(OutputTarget.LOCAL_CALENDAR, event.identity, "old-event", "fingerprint", "owned", event)
        val restored = RuleLedgerCodec.decode(RuleLedgerCodec.encode(RuleLedger(mappings = listOf(mapping))))
        val state = CalendarControlState(initialCourses = emptyList(), initialRuleLedger = restored)
        assertEquals(setOf(event.identity), state.cleanupIdentities(setOf("old"), null, null))
        assertEquals(setOf(event.identity), state.cleanupIdentities(null, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-01")))
        assertFalse(RuleLedgerBackupCodec.encode(restored).contains("old-event"))
    }

    @Test fun cancelledSourceIsStillSelectableForCleanup() {
        val state = state(occurrence().copy(isCancelled = true))
        assertEquals(1, state.cleanupIdentities(setOf("synthetic"), null, null).size)
    }

    @Test fun changingRangeInvalidatesConfirmation() {
        val item = occurrence()
        val state = state(item)
        state.toggleSourceOccurrenceSelection(item.identity)
        state.prepareCalendarWrite()
        assertNotNull(state.managedSyncPreview)
        state.saveSourceOutputRange("2026-11-01", "2026-11-30")
        assertNull(state.managedSyncPreview)
        assertTrue(state.currentDesiredEvents().isEmpty())
    }

    @Test fun cleanupCanExplicitlySelectLocalWithoutGooglePermission() {
        val state = CalendarControlState(initialPrivateSyncConfiguration = PrivateSyncConfiguration(localCalendarId = "local", googleCalendarId = "google"))
        state.refreshLocalCalendarPermission(true)
        state.requestManagedRollback(selectedTargets = setOf(OutputTarget.LOCAL_CALENDAR))
        assertTrue(state.managedRollbackRequested)
        assertEquals(setOf(OutputTarget.LOCAL_CALENDAR), state.currentManagedTargets())
    }

    @Test fun sourceReadHonorsDatesOutsideDefaultWindowAndBoundsInfiniteRules() {
        val bounds = sourceReadBounds(SourceOutputDateWindow(LocalDate.parse("2028-01-01"), LocalDate.parse("2028-02-01")), start)!!
        assertEquals(Instant.parse("2027-12-31T23:00:00Z"), bounds.first)
        assertEquals(Instant.parse("2028-02-01T23:00:00Z"), bounds.second)
        assertNull(sourceReadBounds(SourceOutputDateWindow(LocalDate.parse("2020-01-01"), LocalDate.parse("2030-01-01")), start))
    }

    @Test fun dateCleanupUsesEachTargetsOwnWrittenTime() {
        val identity = ManagedEventIdentity("synthetic", "one")
        val local = DesiredCalendarEvent(identity, "Synthetic", start, start.plusSeconds(3600), AttendanceState.MUST_ATTEND)
        val google = local.copy(startsAt = start.plusSeconds(86400), endsAt = start.plusSeconds(90000))
        val mappings = listOf(
            StoredEventMapping(OutputTarget.LOCAL_CALENDAR, identity, "local-event", "f", "local", local),
            StoredEventMapping(OutputTarget.GOOGLE_CALENDAR, identity, "google-event", "g", "google", google),
        )
        val state = CalendarControlState(initialCourses = emptyList(), initialRuleLedger = RuleLedger(mappings = mappings))
        val scope = state.selectedCleanupScope(null, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-01"), OutputTarget.entries.toSet())
        assertEquals(setOf(identity), scope.forTarget(OutputTarget.LOCAL_CALENDAR))
        assertTrue(scope.forTarget(OutputTarget.GOOGLE_CALENDAR).isEmpty())
    }

    @Test fun editsDuringWriteInvalidateNextConfirmationWithoutChangingConfirmedEvents() {
        val item = occurrence()
        val state = state(item)
        state.refreshLocalCalendarPermission(true)
        state.toggleSourceOccurrenceSelection(item.identity)
        state.prepareCalendarWrite()
        state.requestManagedSyncExecution()
        val confirmed = state.currentDesiredEvents()
        state.saveSourceOutputRange("2026-11-01", "2026-11-30")
        assertEquals(confirmed, state.currentDesiredEvents())
        state.resolveManagedOperation(state.currentManagedOperationToken(), ManagedSyncResult(emptyList(), emptyList()), PrivateSyncConfiguration(localCalendarId = "owned"))
        assertNull(state.managedSyncPreview)
        assertEquals("2026-11-01", state.privateSyncConfiguration.sourceRangeStartDate)
        assertEquals("owned", state.privateSyncConfiguration.localCalendarId)
    }

    @Test fun recreatedScreenCannotStartSameWriteTwice() {
        val session = CalendarSessionModel()
        assertTrue(session.claim("write", 1))
        assertFalse(session.claim("write", 1))
        assertTrue(session.claim("write", 2))
    }
}
