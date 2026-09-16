/*
 * 职责：回归验证安装前审查缺陷的正确行为。
 * 输入输出：仅合成课程与内存网关；无网络、账户、手机、文件偏好或真实日历操作。
 * 不负责：网络、真实日历或 UI 真机验证。
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.domain.*
import com.tequila.tumcalendarcontrol.sync.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class PreinstallFixRegressionTest {
    private val start = Instant.parse("2026-10-01T08:00:00Z")
    private fun item(uid: String = "synthetic-a", location: String = "room") = SourceCalendarOccurrence(uid, "instance-a", "Synthetic VO", start, start.plusSeconds(3600), false, location)
    private fun snapshot(vararg items: SourceCalendarOccurrence) = SavedSourceSnapshot(start, items.size, emptyMap(), items.toList())
    private fun state(snapshots: SavedSourceSnapshots = SavedSourceSnapshots(latest = snapshot(item()))) = CalendarControlState(
        initialSourceSnapshots = snapshots, initialSyncDestinations = SyncDestinations(localCalendarEnabled = true),
    )
    @Test fun emptyLocationRoundTrips() {
        val saved = SavedSourceSnapshots(latest = snapshot(item(location = "")))
        assertEquals(saved, SourceSnapshotCodec.decode(SourceSnapshotCodec.encode(saved)))
    }
    @Test fun failedRereadKeepsSavedCourseSelector() {
        val state = state()
        state.updatePrivateSyncConfiguration(PrivateSyncConfiguration(tumSubscriptionUrl = "https://example.invalid/synthetic.ics"))
        state.requestSourcePreview()
        state.resolveSourcePreview(state.sourceReadToken, IcalReadOnlyImportResult.Failure("synthetic failure"))
        assertTrue(state.sourceCatalogLoaded)
        state.openSourceCourseSelector()
        assertEquals(AppScreen.SOURCE_PREVIEW, state.screen)
    }
    @Test fun emptyCourseCleanupSelectionMatchesNothing() {
        val state = state(SavedSourceSnapshots(latest = snapshot(item(), item("synthetic-b"))))
        assertTrue(state.cleanupIdentities(emptySet(), null, null).isEmpty())
    }
    @Test fun missingTargetIsRecreated() {
        val event = DesiredCalendarEvent(ManagedEventIdentity("synthetic", "one"), "Synthetic", start, start.plusSeconds(3600), AttendanceState.MUST_ATTEND)
        val mapping = StoredEventMapping(OutputTarget.LOCAL_CALENDAR, event.identity, "deleted-event", SyncDiffPlanner().run { event.fingerprint() })
        val gateway = FakeOwnedCalendarGateway(setOf("synthetic-owned"))
        val result = OwnedCalendarSyncExecutor().execute(OwnedCalendarRef(OutputTarget.LOCAL_CALENDAR, "synthetic-owned", "tum-calendar-control"), listOf(event), listOf(mapping), gateway)
        assertEquals(SyncChangeType.CREATE, result.changes.single().type)
        assertEquals(1, gateway.operations.size)
    }
    @Test fun localEditsReachWriteCandidates() {
        val state = state()
        state.openCourse("synthetic-a")
        state.updateSelectedCourseOverride(CourseLocalOverride(title = "Edited", location = "Changed room"))
        state.toggleSourceOccurrenceSelection(item().identity)
        state.prepareCalendarWrite()
        assertEquals("Edited", state.currentDesiredEvents().single().title)
        assertEquals("Changed room", state.currentDesiredEvents().single().location)
    }
    @Test fun singleOccurrenceRuleUsesRealKey() {
        val state = state()
        state.openCourse("synthetic-a")
        state.pendingAttendance = AttendanceState.MUST_ATTEND
        state.pendingAttendanceScope = AttendanceEditScope.ONLY_THIS_OCCURRENCE
        state.confirmAttendance()
        assertTrue(state.ruleLedger.rules.single().instanceOverrides.containsKey("instance-a"))
        state.toggleSourceOccurrenceSelection(item().identity)
        state.prepareCalendarWrite()
        assertEquals(AttendanceState.MUST_ATTEND, state.currentDesiredEvents().single().attendanceState)
    }
    @Test fun switchingSnapshotsPreservesFavoritesAndInvalidatesPreview() {
        val state = state(SavedSourceSnapshots(latest = snapshot(item()), previous = snapshot(item("synthetic-old"))))
        state.toggleFavorite("synthetic-a")
        state.toggleSourceOccurrenceSelection(item().identity)
        state.prepareCalendarWrite()
        state.selectSourceSnapshot(SourceSnapshotSlot.PREVIOUS)
        assertTrue(state.currentDesiredEvents().isEmpty())
        assertNull(state.managedSyncPreview)
        state.selectSourceSnapshot(SourceSnapshotSlot.LATEST)
        assertTrue(state.courses.single().isFavorite)
    }
    @Test fun teachingTypeBeforeGroupSuffixIsRecognized() {
        val courses = listOf("VO", "UE", "PR").map { type -> Course(type, "Synthetic $type, Standardgruppe", "", "", AttendanceState.CHOOSE_TO_ATTEND, false) }
        val groups = CourseDisplayGroupModel.groups(courses)
        assertEquals(1, groups.size)
        assertEquals(setOf("VO", "UE", "PR"), groups.single().teachingTypes.map { it.label }.toSet())
    }
    @Test fun multiDayOccurrenceDisplaysDates() {
        val overnight = item().copy(startsAt = Instant.parse("2026-10-01T21:00:00Z"), endsAt = Instant.parse("2026-10-02T01:00:00Z"))
        assertTrue(state(SavedSourceSnapshots(latest = snapshot(overnight))).courses.single().schedule.contains("2026-10-02"))
    }
    @Test fun repeatedUidHasEveryOccurrence() {
        val first = item()
        val second = first.copy(instanceKey = "instance-b", startsAt = start.plusSeconds(604800), endsAt = start.plusSeconds(608400))
        val state = state(SavedSourceSnapshots(latest = snapshot(first, second)))
        assertEquals(2, state.currentSourceOccurrences.size)
        assertEquals(2, CourseDisplayGroupModel.groups(state.displayedCourses).single().courses.size)
    }
    @Test fun explicitReadyOnlyConfirmationAllowsLocalWrite() {
        val state = state()
        state.refreshLocalCalendarPermission(true)
        state.setSyncDestinationEnabled(SyncDestination.GOOGLE_CALENDAR, true)
        state.toggleSourceOccurrenceSelection(item().identity)
        state.prepareCalendarWrite()
        state.requestManagedSyncExecution()
        assertFalse(state.managedSyncRequested)
        assertTrue(state.feedback!!.contains("请先连接 Google"))
        state.requestReadyManagedSyncExecution()
        assertTrue(state.managedSyncRequested)
        assertEquals(setOf(OutputTarget.LOCAL_CALENDAR), state.currentManagedTargets())
    }
    @Test fun partialWriteFailureRetainsSuccessCountAndMapping() {
        val events = listOf("one", "two").map { key -> DesiredCalendarEvent(ManagedEventIdentity("synthetic", key), "Synthetic", start, start.plusSeconds(3600), AttendanceState.MUST_ATTEND) }
        var created = 0
        val backing = FakeOwnedCalendarGateway(setOf("synthetic-owned"))
        val gateway = object : OwnedCalendarGateway by backing {
            override fun create(calendar: OwnedCalendarRef, event: DesiredCalendarEvent): String {
                if (created == 1) error("synthetic failure after first creation")
                created++
                return "synthetic-created"
            }
        }
        val result = ManagedSyncEngine().execute(events, emptyList(), listOf(TargetBinding(OwnedCalendarRef(OutputTarget.LOCAL_CALENDAR, "synthetic-owned", "tum-calendar-control"), gateway)))
        assertEquals(1, created)
        assertEquals(1, result.createdCount)
        assertEquals(1, result.mappings.size)
        assertNotNull(result.failureSummary)
    }
}
