/*
 * 职责：验证私有同步配置的纯格式校验与脱敏显示。
 * 输入／输出：输入合成地址与合成客户端标识；输出配置问题与不含路径的摘要断言。
 * 外部副作用：无。
 * 不负责：Android Keystore、真实订阅、OAuth、网络或日历 Provider 集成。
 */
package com.tequila.tumcalendarcontrol.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateSyncConfigurationTest {
    @Test
    fun `有效的合成配置通过并隐藏订阅路径`() {
        val configuration = PrivateSyncConfiguration(
            tumSubscriptionUrl = "https://calendar.example.test/private/token-value",
            googleOAuthClientId = "synthetic.apps.googleusercontent.com",
            googleCalendarName = "合成目标",
        )

        assertTrue(configuration.validate().isEmpty())
        assertEquals("calendar.example.test · 已配置", configuration.maskedTumSubscription())
        assertFalse(configuration.maskedTumSubscription().contains("token-value"))
    }

    @Test
    fun `无效地址和客户端标识被拒绝`() {
        val configuration = PrivateSyncConfiguration(
            tumSubscriptionUrl = "not-a-url",
            googleOAuthClientId = "not-google",
            googleCalendarName = " ",
        )

        assertEquals(
            setOf(
                ConfigurationProblem.INVALID_TUM_SUBSCRIPTION,
                ConfigurationProblem.INVALID_GOOGLE_CLIENT_ID,
                ConfigurationProblem.BLANK_GOOGLE_CALENDAR_NAME,
            ),
            configuration.validate().toSet(),
        )
    }
}
