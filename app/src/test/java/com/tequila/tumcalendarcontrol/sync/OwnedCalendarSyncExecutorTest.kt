/*
 * Aufgabe: synthetische Regressionstests für OwnedCalendarSyncExecutor.
 * Ein-/Ausgabe: künstliche Zustände und Fixtures -> JUnit-Prüfergebnisse.
 * Nebenwirkungen: nur lokaler Testprozess und gegebenenfalls Robolectric-Präferenzen.
 * Nicht zuständig: echte Konten, private Quellen, Geräte oder reale Kalenderausgaben.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnedCalendarSyncExecutorTest {
    private val identity = ManagedEventIdentity("synthetic", "2026-10-01T08:00:00Z")
    private val event = DesiredCalendarEvent(identity, "合成课程", Instant.parse(identity.instanceKey), Instant.parse(identity.instanceKey).plusSeconds(3600), AttendanceState.MUST_ATTEND)

    @Test fun `两个拥有目标各自创建且重复运行核对更新不重复创建`() {
        val executor = OwnedCalendarSyncExecutor()
        val gateway = FakeOwnedCalendarGateway(setOf("local", "google"))
        val local = OwnedCalendarRef(OutputTarget.LOCAL_CALENDAR, "local", "tum-calendar-control")
        val google = OwnedCalendarRef(OutputTarget.GOOGLE_CALENDAR, "google", "tum-calendar-control")
        val firstLocal = executor.execute(local, listOf(event), emptyList(), gateway)
        val firstGoogle = executor.execute(google, listOf(event), firstLocal.mappings, gateway)
        val repeated = executor.execute(local, listOf(event), firstGoogle.mappings, gateway)
        assertEquals(2, gateway.operations.count { it.startsWith("create:") })
        assertEquals(listOf(SyncChangeType.KEEP), repeated.changes.map { it.type })
        assertEquals(2, repeated.mappings.size)
    }

    @Test fun `未知目标拒绝且零写入`() {
        val gateway = FakeOwnedCalendarGateway(emptySet())
        val result = OwnedCalendarSyncExecutor().execute(OwnedCalendarRef(OutputTarget.LOCAL_CALENDAR, "unknown", "tum-calendar-control"), listOf(event), emptyList(), gateway)
        assertTrue(result.failureSummary!!.contains("拒绝"))
        assertTrue(gateway.operations.isEmpty())
    }

    @Test fun `映射缺失但隐藏身份已存在时更新旧事件而不再新建`() {
        val gateway = FakeOwnedCalendarGateway(setOf("local"), mapOf(identity to listOf("recovered-local-event")))
        val local = OwnedCalendarRef(OutputTarget.LOCAL_CALENDAR, "local", "tum-calendar-control")

        val result = OwnedCalendarSyncExecutor().execute(local, listOf(event), emptyList(), gateway)

        assertEquals(listOf("update:recovered-local-event"), gateway.operations)
        assertEquals("recovered-local-event", result.mappings.single().targetEventId)
    }

    @Test fun `添加新课程不会删除此前未选择课程的映射或日程`() {
        val oldIdentity = ManagedEventIdentity("old-synthetic", "2026-10-08T08:00:00Z")
        val newIdentity = ManagedEventIdentity("new-synthetic", "2026-10-15T08:00:00Z")
        val newEvent = DesiredCalendarEvent(newIdentity, "新合成课程", Instant.parse(newIdentity.instanceKey), Instant.parse(newIdentity.instanceKey).plusSeconds(3600), AttendanceState.CHOOSE_TO_ATTEND)
        val gateway = FakeOwnedCalendarGateway(setOf("local"))
        val local = OwnedCalendarRef(OutputTarget.LOCAL_CALENDAR, "local", "tum-calendar-control")
        val mappings = listOf(StoredEventMapping(OutputTarget.LOCAL_CALENDAR, oldIdentity, "old-event", "旧指纹"))

        val result = OwnedCalendarSyncExecutor().execute(local, listOf(newEvent), mappings, gateway)

        assertEquals(setOf(oldIdentity, newIdentity), result.mappings.map { it.identity }.toSet())
        assertEquals(listOf("create:local_calendar-1"), gateway.operations)
        assertEquals(listOf(SyncChangeType.CREATE), result.changes.map { it.type })
    }

    @Test fun `撤销只删除映射事件 不触碰其他目标映射`() {
        val executor = OwnedCalendarSyncExecutor()
        val gateway = FakeOwnedCalendarGateway(setOf("local", "google"))
        val local = OwnedCalendarRef(OutputTarget.LOCAL_CALENDAR, "local", "tum-calendar-control")
        val google = OwnedCalendarRef(OutputTarget.GOOGLE_CALENDAR, "google", "tum-calendar-control")
        val localResult = executor.execute(local, listOf(event), emptyList(), gateway)
        val both = executor.execute(google, listOf(event), localResult.mappings, gateway)

        val rolledBack = executor.rollback(local, both.mappings, gateway)

        assertEquals(1, rolledBack.mappings.size)
        assertEquals(OutputTarget.GOOGLE_CALENDAR, rolledBack.mappings.single().target)
        assertEquals(listOf("create:local_calendar-1", "create:google_calendar-2", "remove:local_calendar-1"), gateway.operations)
    }

    @Test fun `全量清理会恢复删除映射丢失但仍有应用标记的旧事件`() {
        val gateway = FakeOwnedCalendarGateway(setOf("local"), allManagedIds = listOf("mapped", "orphaned"))
        val local = OwnedCalendarRef(OutputTarget.LOCAL_CALENDAR, "local", "tum-calendar-control")
        val mappings = listOf(StoredEventMapping(OutputTarget.LOCAL_CALENDAR, identity, "mapped", "旧指纹"))

        val result = OwnedCalendarSyncExecutor().rollback(local, mappings, gateway)

        assertEquals(emptyList<StoredEventMapping>(), result.mappings)
        assertEquals(1, result.changes.size)
        assertEquals(1, result.recoveredRemovalCount)
        assertEquals(listOf("remove:mapped", "remove:orphaned"), gateway.operations)
    }

    @Test fun `全量清理会把资料库映射与恢复发现的旧日程一起计数`() {
        val mappings = (1..3).map { index ->
            StoredEventMapping(
                OutputTarget.LOCAL_CALENDAR,
                ManagedEventIdentity("synthetic-$index", "instance-$index"),
                "mapped-$index",
                "旧指纹",
            )
        }
        val gateway = FakeOwnedCalendarGateway(
            ownedCalendarIds = setOf("local"),
            allManagedIds = mappings.map(StoredEventMapping::targetEventId) + listOf("legacy-1", "legacy-2", "legacy-3"),
        )
        val local = OwnedCalendarRef(OutputTarget.LOCAL_CALENDAR, "local", "tum-calendar-control")

        val result = OwnedCalendarSyncExecutor().rollback(local, mappings, gateway)

        assertEquals(6, result.cleanupSummary!!.requestedCount)
        assertEquals(6, result.cleanupSummary.removedNowCount)
        assertEquals(0, result.cleanupSummary.remainingManagedCount)
        assertEquals(3, result.recoveredRemovalCount)
    }

    @Test fun `清理按目标事件计数 并在复核仍存在时保留映射`() {
        val gateway = FakeOwnedCalendarGateway(
            ownedCalendarIds = setOf("local"),
            allManagedIds = listOf("mapped", "orphaned"),
            retainedAfterRemovalIds = setOf("mapped"),
        )
        val local = OwnedCalendarRef(OutputTarget.LOCAL_CALENDAR, "local", "tum-calendar-control")
        val mappings = listOf(StoredEventMapping(OutputTarget.LOCAL_CALENDAR, identity, "mapped", "旧指纹"))

        val result = OwnedCalendarSyncExecutor().rollback(local, mappings, gateway)

        assertEquals(listOf("mapped"), result.mappings.map(StoredEventMapping::targetEventId))
        assertEquals(emptyList<SyncChange>(), result.changes)
        assertEquals(
            ManagedCleanupSummary(1, 1, 2, 2, 0, 0, 1),
            result.cleanupSummary,
        )
        assertTrue(result.failureSummary!!.contains("仍有 1 条"))
    }

    @Test fun `此前已不存在不算作本次删除 但可安全移除旧映射`() {
        val gateway = FakeOwnedCalendarGateway(
            ownedCalendarIds = setOf("local"),
            removalOutcomes = mapOf("missing" to ManagedEventRemovalOutcome.ALREADY_ABSENT),
        )
        val local = OwnedCalendarRef(OutputTarget.LOCAL_CALENDAR, "local", "tum-calendar-control")
        val mappings = listOf(StoredEventMapping(OutputTarget.LOCAL_CALENDAR, identity, "missing", "旧指纹"))

        val result = OwnedCalendarSyncExecutor().rollback(local, mappings, gateway)

        assertTrue(result.mappings.isEmpty())
        assertEquals(0, result.cleanupSummary!!.removedNowCount)
        assertEquals(1, result.cleanupSummary.alreadyAbsentCount)
        assertEquals(0, result.cleanupSummary.remainingManagedCount)
    }

    @Test fun `按精确身份清理不会误删同名或同时段的另一门课程`() {
        val otherIdentity = ManagedEventIdentity("another-synthetic", "2026-10-01T08:00:00Z")
        val gateway = FakeOwnedCalendarGateway(setOf("local"), mapOf(identity to listOf("first"), otherIdentity to listOf("other")))
        val local = OwnedCalendarRef(OutputTarget.LOCAL_CALENDAR, "local", "tum-calendar-control")
        val mappings = listOf(
            StoredEventMapping(OutputTarget.LOCAL_CALENDAR, identity, "first", "旧指纹"),
            StoredEventMapping(OutputTarget.LOCAL_CALENDAR, otherIdentity, "other", "旧指纹"),
        )

        val result = OwnedCalendarSyncExecutor().rollback(
            local,
            mappings,
            gateway,
            ManagedEventCleanupScope.SelectedIdentities(setOf(identity)),
        )

        assertEquals(listOf(otherIdentity), result.mappings.map { it.identity })
        assertEquals(listOf("remove:first"), gateway.operations)
    }
}
