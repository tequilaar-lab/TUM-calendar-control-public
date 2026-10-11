/*
 * Aufgabe: synthetische Regressionstests für RuleLedgerPreferences.
 * Ein-/Ausgabe: künstliche Zustände und Fixtures -> JUnit-Prüfergebnisse.
 * Nebenwirkungen: nur lokaler Testprozess und gegebenenfalls Robolectric-Präferenzen.
 * Nicht zuständig: echte Konten, private Quellen, Geräte oder reale Kalenderausgaben.
 */
package com.tequila.tumcalendarcontrol.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RuleLedgerPreferencesTest {
    private lateinit var context: Context
    private lateinit var preferences: RuleLedgerPreferences

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteSharedPreferences("rule_ledger")
        preferences = RuleLedgerPreferences(context)
    }

    @Test fun `业务保存保留最新映射且展示资料失败时恢复原资料库`() {
        val mapping = StoredEventMapping(OutputTarget.LOCAL_CALENDAR, ManagedEventIdentity("uid", "instance"), "event", "fingerprint")
        assertTrue(preferences.save(RuleLedger(mappings = listOf(mapping))))
        val business = RuleLedger(
            rules = listOf(SyncRule("uid", true, AttendanceState.MUST_ATTEND)),
            manualEvents = listOf(ManualScheduleItem("manual", "Synthetic", Instant.parse("2026-10-01T08:00:00Z"), Instant.parse("2026-10-01T09:00:00Z"), AttendanceState.CHOOSE_TO_ATTEND)),
        )

        val committed = preferences.saveBusinessState(business) { true }
        assertEquals(listOf(mapping), committed!!.mappings)
        assertEquals(listOf(mapping), preferences.load().mappings)
        assertEquals(business.rules, preferences.load().rules)
        val beforeFailure = preferences.load()
        assertEquals(null, preferences.saveBusinessState(RuleLedger(rules = emptyList())) { false })
        assertEquals(beforeFailure, preferences.load())
    }

    @Test fun `映射检查点只替换映射不覆盖业务规则`() {
        val rule = SyncRule("uid", true, AttendanceState.MUST_ATTEND)
        assertTrue(preferences.save(RuleLedger(rules = listOf(rule))))
        val mapping = StoredEventMapping(OutputTarget.GOOGLE_CALENDAR, ManagedEventIdentity("uid", "instance"), "event", "fingerprint")
        preferences.checkpointMappings(listOf(mapping))

        assertEquals(listOf(rule), preferences.load().rules)
        assertEquals(listOf(mapping), preferences.load().mappings)
    }
}
