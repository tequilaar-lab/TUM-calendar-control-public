/*
 * Aufgabe: synthetische Regressionstests für TargetStateReconciler.
 * Ein-/Ausgabe: künstliche Zustände und Fixtures -> JUnit-Prüfergebnisse.
 * Nebenwirkungen: nur lokaler Testprozess und gegebenenfalls Robolectric-Präferenzen.
 * Nicht zuständig: echte Konten, private Quellen, Geräte oder reale Kalenderausgaben.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class TargetStateReconcilerTest {
    private val first = event("uid-a", "first", "课程 A")
    private val second = event("uid-b", "second", "课程 B")
    private val planner = SyncDiffPlanner()

    @Test
    fun `缺失 外部修改 与时间冲突课程分别给出真实诊断`() {
        val mappings = listOf(
            mapping(first, "missing"),
            mapping(second, "changed"),
        )
        val actual = mapOf("changed" to ActualManagedEvent("changed", "外部修改后的指纹"))

        val findings = TargetStateReconciler().reconcile(listOf(first, second), mappings, actual)

        assertEquals(TargetStateCode.MISSING_IN_TARGET, findings.single { it.eventId == "missing" }.code)
        assertEquals(TargetStateCode.CHANGED_IN_TARGET, findings.single { it.eventId == "changed" }.code)
    }

    @Test
    fun `不同 UID 即使时间相同也分别核对`() {
        val sameTimeSecond = second.copy(startsAt = first.startsAt, endsAt = first.endsAt)
        val mappings = listOf(mapping(first, "a"), mapping(sameTimeSecond, "b"))
        val actual = mapOf(
            "a" to ActualManagedEvent("a", planner.run { first.fingerprint() }),
            "b" to ActualManagedEvent("b", planner.run { sameTimeSecond.fingerprint() }),
        )

        assertEquals(2, TargetStateReconciler().reconcile(listOf(first, sameTimeSecond), mappings, actual).size)
    }

    private fun event(uid: String, key: String, title: String) = DesiredCalendarEvent(
        ManagedEventIdentity(uid, key), title, Instant.parse("2026-10-01T08:00:00Z"), Instant.parse("2026-10-01T09:00:00Z"), AttendanceState.MUST_ATTEND, "合成地点",
    )

    private fun mapping(event: DesiredCalendarEvent, eventId: String) = StoredEventMapping(OutputTarget.LOCAL_CALENDAR, event.identity, eventId, planner.run { event.fingerprint() })
}
