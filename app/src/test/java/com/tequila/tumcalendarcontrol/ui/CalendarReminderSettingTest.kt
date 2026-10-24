/*
 * Aufgabe: Auswahl „Erinnerung beim Schreiben“ auf der Verbindungsseite prüfen: Chips setzen den Zustand, „keine“ nach
 *   vorheriger Verwaltung wird NO_REMINDER; Seitenbild hell.
 * Ein-/Ausgabe: Leerer Zustand -> Klicks -> calendarReminderMinutes, PNG.
 * Nebenwirkungen: Nur Robolectric und lokales Testbild.
 * Nicht zuständig: Schreiben (CalendarReminderTest) oder Speicherung (CalendarControlApp).
 */
package com.tequila.tumcalendarcontrol.ui

import android.content.Context
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.tequila.tumcalendarcontrol.i18n.AppLanguage
import com.tequila.tumcalendarcontrol.i18n.AppLanguagePreferences
import com.tequila.tumcalendarcontrol.sync.NO_REMINDER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
class CalendarReminderSettingTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun chinese() = AppLanguagePreferences(context).save(AppLanguage.CHINESE)

    @Test fun `写入提醒选择`() {
        val state = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT; it.lightPaletteStyle = LightPaletteStyle.WARM_WHITE; it.open(AppScreen.SYNC_CONFIGURATION) }
        composeRule.setContent { CalendarControlSurface(state) }
        composeRule.waitForIdle()
        assertNull(state.calendarReminderMinutes)
        composeRule.onNodeWithTag("connection-content").performScrollToNode(hasTestTag("connection-reminder"))
        // Semantische Klickaktion: die Chips liegen nach dem Scrollen unter der schwebenden Speichern-Schaltfläche.
        composeRule.onNode(hasText("15 分钟") and hasAnyAncestor(hasTestTag("connection-reminder"))).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(15, state.calendarReminderMinutes)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/connection_reminder_412x892.png")
        composeRule.onNode(hasText("不提醒") and hasAnyAncestor(hasTestTag("connection-reminder"))).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(NO_REMINDER, state.calendarReminderMinutes)
    }
}
