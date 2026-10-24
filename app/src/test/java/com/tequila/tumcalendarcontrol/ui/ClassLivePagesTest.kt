/*
 * Aufgabe: Einstellungsseite der Live-Kursbenachrichtigung prüfen: Einstieg über Einstellungen, Schalter, Vorlauf und
 *   „auch blaue Kurse“ werden gespeichert; Zurück führt zu den Einstellungen; Seitenbild hell.
 * Ein-/Ausgabe: Leerer Zustand mit erteilter Benachrichtigungsberechtigung -> Klicks, gespeicherte Einstellungen, PNG.
 * Nebenwirkungen: Nur private Test-Präferenzen, Robolectric-Benachrichtigungen/-Wecker und lokales Testbild.
 * Nicht zuständig: Planung und Benachrichtigungsinhalt (ClassLiveTest).
 */
package com.tequila.tumcalendarcontrol.ui

import android.Manifest
import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.tequila.tumcalendarcontrol.i18n.AppLanguage
import com.tequila.tumcalendarcontrol.i18n.AppLanguagePreferences
import com.tequila.tumcalendarcontrol.notify.ClassLiveSettings
import com.tequila.tumcalendarcontrol.notify.ClassLiveSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
class ClassLivePagesTest {
    @get:Rule val composeRule = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        AppLanguagePreferences(app).save(AppLanguage.CHINESE)
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test fun `设置进入课程实况通知并保存`() {
        val state = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT; it.lightPaletteStyle = LightPaletteStyle.WARM_WHITE; it.open(AppScreen.SETTINGS) }
        composeRule.setContent { CalendarControlSurface(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-class-live"))
        composeRule.onNode(hasClickAction() and hasAnyAncestor(hasTestTag("settings-class-live")), useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(AppScreen.CLASS_LIVE, state.screen)
        composeRule.onNodeWithTag("class-live-enabled").performClick()
        composeRule.onNode(hasText("30 分钟") and hasAnyAncestor(hasTestTag("class-live-lead"))).performClick()
        composeRule.onNodeWithTag("class-live-choose").performClick()
        composeRule.waitForIdle()
        assertEquals(ClassLiveSettings(enabled = true, leadMinutes = 30, includeChooseToAttend = true), ClassLiveSettingsStore(app).load())
        composeRule.onNodeWithTag("class-live-promotion").assertExists()
        composeRule.onNodeWithTag("class-live-content").performScrollToNode(hasTestTag("class-live-test"))
        composeRule.onNodeWithTag("class-live-test").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("class-live-content").performScrollToNode(hasTestTag("class-live-test-result"))
        composeRule.onNodeWithTag("class-live-test-result").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/class_live_settings_412x892.png")
        assertTrue(state.handleBack())
        assertEquals(AppScreen.SETTINGS, state.screen)
    }
}
