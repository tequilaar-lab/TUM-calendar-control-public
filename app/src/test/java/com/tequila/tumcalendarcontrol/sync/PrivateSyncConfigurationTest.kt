/*
 * Aufgabe: synthetische Regressionstests für PrivateSyncConfiguration.
 * Ein-/Ausgabe: künstliche Zustände und Fixtures -> JUnit-Prüfergebnisse.
 * Nebenwirkungen: nur lokaler Testprozess und gegebenenfalls Robolectric-Präferenzen.
 * Nicht zuständig: echte Konten, private Quellen, Geräte oder reale Kalenderausgaben.
 */
package com.tequila.tumcalendarcontrol.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateSyncConfigurationTest {

    @Test
    fun `私有来源只接受HTTPS`() {
        val problems = PrivateSyncConfiguration(tumSubscriptionUrl = "http://synthetic.example.test/calendar").validate()

        assertTrue(problems.contains(ConfigurationProblem.INVALID_TUM_SUBSCRIPTION))
    }

    @Test
    fun `已配置订阅只显示全遮罩 不泄露主机或路径`() {
        val configuration = PrivateSyncConfiguration(tumSubscriptionUrl = "https://private.example.test/path?token=synthetic")

        assertEquals("•••••••• · 已加密保存在本机", configuration.maskedTumSubscription())
    }
    @Test
    fun `有效的合成配置通过并隐藏订阅路径`() {
        val configuration = PrivateSyncConfiguration(
            tumSubscriptionUrl = "https://calendar.example.test/private/token-value",
            googleOAuthClientId = "synthetic.apps.googleusercontent.com",
            googleCalendarName = "合成目标",
        )

        assertTrue(configuration.validate().isEmpty())
        assertEquals("•••••••• · 已加密保存在本机", configuration.maskedTumSubscription())
        assertFalse(configuration.maskedTumSubscription().contains("calendar.example.test"))
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
