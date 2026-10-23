/*
 * Aufgabe: Schneller Ausgabeweg prüfen: ein Lesedurchlauf statt Einzelabfragen liefert dieselben Ergebnisse
 *   (behalten/aktualisieren/neu, Doppelte abgelehnt); Google prüft Eigentum je Ausgabe nur einmal; Feldauslese.
 * Ein-/Ausgabe: Synthetische Termine und Ersatz-Gateways -> Änderungen, Zuordnungen und Aufrufzählungen.
 * Nebenwirkungen: Keine; keine Kalender, Konten oder Netzwerke.
 * Nicht zuständig: echte Laufzeiten auf dem Gerät (nur mit Gerätefreigabe messbar).
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WriteSpeedPathTest {
    private val calendar = OwnedCalendarRef(OutputTarget.LOCAL_CALENDAR, "owned", "tum-calendar-control")
    private fun event(n: Int, state: AttendanceState = AttendanceState.MUST_ATTEND) = DesiredCalendarEvent(
        ManagedEventIdentity("synthetic", "i$n"), "Synthetik $n", Instant.parse("2026-10-01T08:00:00Z").plusSeconds(n * 86_400L),
        Instant.parse("2026-10-01T09:00:00Z").plusSeconds(n * 86_400L), state, "Raum",
    )

    /** Ersatz mit Lesedurchlauf: zählt Einzelabfragen, die der Schnellweg vermeiden soll. */
    private class SnapshotGateway(private val inner: FakeOwnedCalendarGateway, private val existing: Map<ManagedEventIdentity, List<Pair<String, DesiredCalendarEvent?>>>) :
        OwnedCalendarGateway by inner {
        var singleLookups = 0
        override fun findEventIdsByIdentity(calendar: OwnedCalendarRef, identity: ManagedEventIdentity): List<String> { singleLookups++; return inner.findEventIdsByIdentity(calendar, identity) }
        override fun readFingerprint(calendar: OwnedCalendarRef, eventId: String, identity: ManagedEventIdentity): String? { singleLookups++; return inner.readFingerprint(calendar, eventId, identity) }
        override fun snapshotManagedEvents(calendar: OwnedCalendarRef) = existing.entries.associate { (identity, entries) ->
            identity.opaqueKey() to entries.map { (id, value) -> ManagedEventSnapshot(id, value?.let { ManagedEventContent(it.title, it.startsAt, it.endsAt, it.attendanceState, it.location) }) }
        }
    }

    @Test fun `一次读取后按内容判断保留更新新建且不逐条查询`() {
        val same = event(1)
        val changed = event(2)
        val fresh = event(3)
        val gateway = SnapshotGateway(
            FakeOwnedCalendarGateway(setOf("owned")),
            mapOf(same.identity to listOf("e1" to same), changed.identity to listOf("e2" to changed.copy(attendanceState = AttendanceState.STOP_ATTENDING))),
        )
        val result = OwnedCalendarSyncExecutor().execute(calendar, listOf(same, changed, fresh), emptyList(), gateway)
        assertEquals(listOf(SyncChangeType.KEEP, SyncChangeType.UPDATE, SyncChangeType.CREATE), result.changes.map { it.type })
        assertEquals(listOf("e1", "e2"), result.changes.take(2).map { it.targetEventId })
        assertEquals(0, gateway.singleLookups)
        assertNull(result.failureSummary)
    }

    @Test fun `读取到的格式未知时视为已变化并更新`() {
        val e = event(1)
        val gateway = SnapshotGateway(FakeOwnedCalendarGateway(setOf("owned")), mapOf(e.identity to listOf("e1" to null)))
        assertEquals(SyncChangeType.UPDATE, OwnedCalendarSyncExecutor().execute(calendar, listOf(e), emptyList(), gateway).changes.single().type)
    }

    @Test fun `同一身份出现两条时仍拒绝写入`() {
        val e = event(1)
        val gateway = SnapshotGateway(FakeOwnedCalendarGateway(setOf("owned")), mapOf(e.identity to listOf("a" to e, "b" to e)))
        val result = OwnedCalendarSyncExecutor().execute(calendar, listOf(e), emptyList(), gateway)
        assertEquals(0, result.changes.size)
        assertEquals(1, result.failedCount)
    }

    /** Ohne Lesedurchlauf (null) bleibt der bisherige Einzelweg unverändert. */
    @Test fun `不支持一次读取时沿用逐条路径`() {
        val gateway = FakeOwnedCalendarGateway(setOf("owned"))
        val first = OwnedCalendarSyncExecutor().execute(calendar, listOf(event(1)), emptyList(), gateway)
        val second = OwnedCalendarSyncExecutor().execute(calendar, listOf(event(1)), first.mappings, gateway)
        assertEquals(SyncChangeType.KEEP, second.changes.single().type)
    }

    @Test fun `谷歌网关每次写入只核对一次日历归属`() {
        var calendarReads = 0
        val api = object : GoogleCalendarApi {
            override fun getCalendar(calendarId: String): GoogleRemoteCalendar { calendarReads++; return GoogleRemoteCalendar(calendarId, GOOGLE_OWNERSHIP_DESCRIPTION) }
            override fun createCalendar(summary: String, description: String) = error("nicht erwartet")
            override fun findEventIdsByIdentity(calendarId: String, identityKey: String): List<String> = emptyList()
            override fun findAllManagedEventIds(calendarId: String): List<String> = emptyList()
            override fun createEvent(calendarId: String, event: DesiredCalendarEvent) = "g-${event.identity.instanceKey}"
            override fun updateEvent(calendarId: String, eventId: String, event: DesiredCalendarEvent) = Unit
            override fun deleteEvent(calendarId: String, eventId: String) = ManagedEventRemovalOutcome.REMOVED_NOW
            override fun snapshotManagedEvents(calendarId: String): Map<String, List<ManagedEventSnapshot>> = emptyMap()
        }
        val google = OwnedCalendarRef(OutputTarget.GOOGLE_CALENDAR, "g", AppOwnedCalendarContract.OWNERSHIP_MARKER)
        val result = OwnedCalendarSyncExecutor().execute(google, (1..20).map { event(it) }, emptyList(), GoogleCalendarGateway(api))
        assertEquals(20, result.changes.count { it.type == SyncChangeType.CREATE })
        assertEquals(1, calendarReads)
    }

    @Test fun `谷歌列表项解析为可比较内容 未知颜色为空`() {
        val item = JSONObject("""{"id":"x","summary":"Synthetik","location":"Raum","colorId":"9",
            "start":{"dateTime":"2026-10-01T10:00:00+02:00"},"end":{"dateTime":"2026-10-01T11:00:00+02:00"}}""")
        val content = googleEventContent(item)!!
        assertEquals(AttendanceState.CHOOSE_TO_ATTEND, content.state)
        assertEquals(Instant.parse("2026-10-01T08:00:00Z"), content.startsAt)
        assertNull(googleEventContent(JSONObject(item.toString()).put("colorId", "3")))
    }
}
