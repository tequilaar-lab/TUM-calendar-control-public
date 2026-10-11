/*
 * Aufgabe: synthetische Regressionstests für SyncRuleResolver.
 * Ein-/Ausgabe: künstliche Zustände und Fixtures -> JUnit-Prüfergebnisse.
 * Nebenwirkungen: nur lokaler Testprozess und gegebenenfalls Robolectric-Präferenzen.
 * Nicht zuständig: echte Konten, private Quellen, Geräte oder reale Kalenderausgaben.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncRuleResolverTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val uid = "synthetic-rule"

    @Test
    fun `灰色截点不追溯历史且单实例覆盖优先`() {
        val rule = SyncRule(
            sourceUid = uid,
            hasConfirmedFixplatz = true,
            defaultState = AttendanceState.CHOOSE_TO_ATTEND,
            stopAttendingFrom = LocalDate.of(2026, 10, 15),
            instanceOverrides = mapOf("override" to AttendanceState.MUST_ATTEND),
        )

        assertEquals(AttendanceState.CHOOSE_TO_ATTEND, SyncRuleResolver.resolve(rule, occurrence("history", "2026-10-08T08:00:00Z"), zone))
        assertEquals(AttendanceState.STOP_ATTENDING, SyncRuleResolver.resolve(rule, occurrence("future", "2026-10-22T08:00:00Z"), zone))
        assertEquals(AttendanceState.MUST_ATTEND, SyncRuleResolver.resolve(rule, occurrence("override", "2026-10-22T08:00:00Z"), zone))
    }

    @Test
    fun `旧资格字段不再筛选来源但取消来源仍不能输出`() {
        val unconfirmed = SyncRule(uid, false, AttendanceState.MUST_ATTEND)
        val confirmed = SyncRule(uid, true, AttendanceState.MUST_ATTEND)

        assertEquals(AttendanceState.MUST_ATTEND, SyncRuleResolver.resolve(unconfirmed, occurrence("one", "2026-10-01T08:00:00Z"), zone))
        assertNull(SyncRuleResolver.resolve(confirmed, occurrence("two", "2026-10-01T08:00:00Z", true), zone))
    }

    private fun occurrence(key: String, startsAt: String, cancelled: Boolean = false) = SourceCalendarOccurrence(
        sourceUid = uid,
        instanceKey = key,
        title = "合成来源",
        startsAt = Instant.parse(startsAt),
        endsAt = Instant.parse(startsAt).plusSeconds(3600),
        isCancelled = cancelled,
    )
}
