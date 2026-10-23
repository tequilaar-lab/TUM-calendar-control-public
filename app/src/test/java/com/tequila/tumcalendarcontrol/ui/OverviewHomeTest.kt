/*
 * Aufgabe: Bedienung der neuen Übersicht und des Seitenkopfs prüfen und Bildbasis für Aufklappen und Detail erzeugen.
 * Ein-/Ausgabe: Synthetischer CalendarControlState -> Compose-Interaktionen, Zustandsprüfungen und PNG-Dateien.
 * Nebenwirkungen: Nur lokale Testbilder.
 * Nicht zuständig: Kursdaten-Lesen, Teilnahmeregeln oder echte Geräte.
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
class OverviewHomeTest {
    @get:Rule val composeRule = createComposeRule()

    private fun render(state: CalendarControlState) {
        composeRule.setContent { CalendarControlSurface(state) }
        composeRule.waitForIdle()
    }

    @Test fun headerShowsNeutralKickerAndListTitle() {
        render(CalendarControlState().also { it.themeMode = ThemeMode.LIGHT })
        composeRule.onNodeWithTag("page-kicker").assertTextEquals(NeutralOverviewKicker)
        composeRule.onNodeWithTag("page-title").assertTextEquals("课程总览")
        composeRule.onNodeWithTag("floating-navigation").assertIsDisplayed()
    }

    /** Schmal: Kurs aufklappen zeigt Typ-Umbenennen und Termine; Antippen eines Termins öffnet die Details mit Zurück. */
    @Test fun phoneExpandsCourseAndOpensSession() {
        val state = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT; it.lightPaletteStyle = LightPaletteStyle.WARM_WHITE }
        render(state)
        composeRule.onNodeWithText("课程 A").performClick()
        composeRule.onNodeWithText("改名").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/overview_expanded_412x892.png")
        composeRule.onAllNodesWithText("示例校区")[0].performClick()
        composeRule.waitForIdle()
        assertEquals(AppScreen.DETAIL, state.contentScreen)
        composeRule.onNodeWithTag("back-button").assertIsDisplayed()
        composeRule.onNodeWithTag("page-kicker").assertTextEquals("COURSE / DETAIL")
    }

    /** Breit: erster Kurs ist vorgewählt, Antippen wechselt das Detail rechts. */
    @Test @Config(sdk = [36], qualifiers = "w840dp-h892dp-420dpi")
    fun wideSwitchesCurrentCourse() {
        render(CalendarControlState().also { it.themeMode = ThemeMode.LIGHT; it.lightPaletteStyle = LightPaletteStyle.WARM_WHITE })
        composeRule.onNodeWithTag("overview-detail-title").assertTextEquals("课程 A")
        composeRule.onNodeWithText("课程 C").performClick()
        composeRule.onNodeWithTag("overview-detail-title").assertTextEquals("课程 C")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/overview_wide_detail_840x892.png")
    }
}
