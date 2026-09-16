/*
 * 职责：验证真实目标字段比较、恢复创建、检查点和陈旧映射的删除边界。
 * 输入／输出：合成 JSON、事件和内存网关；输出确定性断言。
 * 外部副作用：仅 Robolectric 本地 JVM，无网络或真实日历。
 * 不负责：OAuth、手机和实际同步延迟验收。
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TargetReadbackRegressionTest {
    private val calendar = OwnedCalendarRef(OutputTarget.LOCAL_CALENDAR, "owned", "tum-calendar-control")
    private val identity = ManagedEventIdentity("synthetic", "instance")
    private val event = DesiredCalendarEvent(identity, "Synthetic", Instant.parse("2026-10-01T08:00:00Z"), Instant.parse("2026-10-01T09:00:00Z"), AttendanceState.MUST_ATTEND)

    @Test fun actualTargetChangesAreUpdatedEvenWhenLocalFingerprintMatches() {
        val gateway = FakeOwnedCalendarGateway(setOf("owned"))
        val executor = OwnedCalendarSyncExecutor()
        val initial = executor.execute(calendar, listOf(event), emptyList(), gateway)
        gateway.update(calendar, initial.mappings.single().targetEventId, event.copy(attendanceState = AttendanceState.STOP_ATTENDING))
        val result = executor.execute(calendar, listOf(event), initial.mappings, gateway)
        assertEquals(SyncChangeType.UPDATE, result.changes.single().type)
        assertEquals(SyncDiffPlanner().run { event.fingerprint() }, gateway.readFingerprint(calendar, result.mappings.single().targetEventId, identity))
    }

    @Test fun everyCompletedEventHasAPersistentCheckpointBeforeNextItem() {
        val checkpoints = mutableListOf<List<StoredEventMapping>>()
        val second = event.copy(identity = ManagedEventIdentity("synthetic", "two"))
        val gateway = object : OwnedCalendarGateway by FakeOwnedCalendarGateway(setOf("owned")) {
            override fun create(calendar: OwnedCalendarRef, event: DesiredCalendarEvent): String {
                if (event.identity == second.identity) {
                    assertEquals(1, checkpoints.size)
                    error("synthetic failure")
                }
                return "first-created"
            }
        }
        val result = ManagedSyncEngine().execute(listOf(event, second), emptyList(), listOf(TargetBinding(calendar, gateway))) { checkpoints += it }
        assertEquals(1, result.createdCount)
        assertEquals(1, result.mappings.size)
        assertEquals(1, result.targetResults.single().failedCount)
    }

    @Test fun staleMappingCannotDeleteAnUnmarkedEventId() {
        val gateway = FakeOwnedCalendarGateway(setOf("owned"))
        val mapping = StoredEventMapping(calendar.target, identity, "moved-to-another-calendar", "old", "previous")
        val result = OwnedCalendarSyncExecutor().rollback(calendar, listOf(mapping), gateway, ManagedEventCleanupScope.SelectedIdentities(setOf(identity)))
        assertTrue(gateway.operations.isEmpty())
        assertEquals(0, result.cleanupSummary!!.removedNowCount)
        assertEquals(1, result.cleanupSummary.alreadyAbsentCount)
    }

    @Test fun legacyCleanupCatalogIsFilledByReadOnlyIdentityChecks() {
        val fake = FakeOwnedCalendarGateway(setOf("owned"))
        val gateway = object : OwnedCalendarGateway by fake {
            override fun findEventIdsByIdentity(calendar: OwnedCalendarRef, identity: ManagedEventIdentity) = listOf("existing")
            override fun readEvent(calendar: OwnedCalendarRef, eventId: String, identity: ManagedEventIdentity) = event
        }
        val legacy = StoredEventMapping(calendar.target, identity, "old", "fingerprint")
        val result = ManagedCleanupCatalog.read(listOf(legacy), listOf(TargetBinding(calendar, gateway)))
        assertEquals(1, result.checkedCount)
        assertEquals(0, result.failedCount)
        assertEquals(event, result.mappings.single().writtenEvent)
        assertEquals("existing", result.mappings.single().targetEventId)
        assertTrue(fake.operations.isEmpty())
    }

    @Test fun googleReadbackUsesActualColorAndTimezoneNormalizedTimes() {
        val body = """{"status":"confirmed","summary":"Synthetic","location":"","colorId":"10","start":{"dateTime":"2026-10-01T10:00:00+02:00"},"end":{"dateTime":"2026-10-01T11:00:00+02:00"},"extendedProperties":{"private":{"tumEventKey":"${identity.opaqueKey()}"}}}"""
        assertEquals(SyncDiffPlanner().run { event.fingerprint() }, googleEventFingerprint(body, identity))
        assertNotEquals(googleEventFingerprint(body, identity), googleEventFingerprint(body.replace("\"10\"", "\"8\""), identity))
        assertTrue(runCatching { googleEventFingerprint(body.replace(identity.opaqueKey(), "not-ours"), identity) }.isFailure)
    }
}
