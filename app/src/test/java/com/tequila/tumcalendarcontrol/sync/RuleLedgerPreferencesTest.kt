/*
 * 职责：验证规则业务保存与同步映射检查点共用串行提交边界。
 * 输入／输出：Robolectric 应用私有偏好与合成资料；输出原子性断言。
 * 外部副作用：仅测试沙盒 SharedPreferences，不访问真实文件、账户或日历。
 * 不负责：系统杀进程、磁盘硬件故障或跨进程并发。
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
