/*
 * Aufgabe: Zweispaltige Darstellung prüfen: Regel (Breite, Suche/Favoriten, Übersicht ausgenommen, Herkunft der
 *   Details), breites Fenster mit Suche und Details, Wechsel zu anderem Kurs ohne Verlust der Liste, schmales Fenster unverändert; Bild als Referenz.
 * Ein-/Ausgabe: Beispielkurse (synthetisch) und Fenstergrößen -> sichtbare Bereiche, PNG.
 * Nebenwirkungen: Nur Robolectric-Umgebung und lokales Testbild.
 * Nicht zuständig: Inhalte der Seiten selbst.
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class TwoPaneLayoutTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `Regel`() {
        assertEquals(AppScreen.SEARCH, twoPaneListScreen(AppScreen.SEARCH, 820, AppScreen.OVERVIEW))
        assertEquals("Details aus der Suche", AppScreen.SEARCH, twoPaneListScreen(AppScreen.DETAIL, 820, AppScreen.SEARCH))
        assertNull("Übersicht hat eigene breite Darstellung", twoPaneListScreen(AppScreen.OVERVIEW, 1200, AppScreen.OVERVIEW))
        assertNull("Details aus der Übersicht", twoPaneListScreen(AppScreen.DETAIL, 1200, AppScreen.OVERVIEW))
        assertNull("zu schmal", twoPaneListScreen(AppScreen.SEARCH, 699, AppScreen.OVERVIEW))
        assertNull("andere Seite", twoPaneListScreen(AppScreen.SETTINGS, 1200, AppScreen.OVERVIEW))
        assertNull("Details nicht aus einer Liste", twoPaneListScreen(AppScreen.DETAIL, 1200, AppScreen.SOURCE_PREVIEW))
    }

    @Config(qualifiers = "w1000dp-h760dp-xhdpi")
    @Test fun `breit Liste und Details`() {
        val state = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT; it.lightPaletteStyle = LightPaletteStyle.WARM_WHITE; it.open(AppScreen.SEARCH) }
        composeRule.setContent { CalendarControlSurface(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("two-pane").assertExists()
        composeRule.onNode(hasTestTag("detail-title")).assertExists()
        state.openCourse(state.courses.last().id)
        composeRule.waitForIdle()
        assertEquals(AppScreen.DETAIL, state.screen)
        composeRule.onNodeWithTag("two-pane").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/two_pane_1000x760.png")
    }

    @Config(qualifiers = "w412dp-h892dp-420dpi")
    @Test fun `schmal unverändert`() {
        val state = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT }
        composeRule.setContent { CalendarControlSurface(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("two-pane").assertDoesNotExist()
    }
}
