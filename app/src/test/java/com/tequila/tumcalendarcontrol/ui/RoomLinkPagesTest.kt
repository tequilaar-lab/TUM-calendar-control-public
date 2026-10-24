/*
 * Aufgabe: Raumlinks in der Oberfläche prüfen: Zeile in den Kursdetails und Kartenschaltfläche in der Übersicht
 *   öffnen genau die nav.tum.de-Adresse des Orts (Browser-Intent), sonst nichts.
 * Ein-/Ausgabe: Beispielkurse -> Klicks und gestartete Intents.
 * Nebenwirkungen: Keine; Robolectric fängt den Intent ab, kein Netzwerk.
 * Nicht zuständig: Linkbildung im Detail (RoomLinkTest).
 */
package com.tequila.tumcalendarcontrol.ui

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.activity.ComponentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class RoomLinkPagesTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun startedUrl(): String? = shadowOf(composeRule.activity).nextStartedActivity?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString

    @Test fun `详情页地点可打开教室地图`() {
        val state = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT; it.openCourse("course-a") }
        composeRule.setContent { CalendarControlSurface(state) }
        composeRule.waitForIdle()
        composeRule.onNode(hasClickAction() and hasAnyAncestor(hasTestTag("room-link")), useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        val url = startedUrl()
        assertTrue(url, url!!.startsWith("https://nav.tum.de/search?q="))
    }

    @Test fun `首页课次行的地图按钮单独打开地图`() {
        val state = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT }
        composeRule.setContent { CalendarControlSurface(state) }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("课程 A")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("room-map")[0].performClick()
        assertTrue(startedUrl()!!.startsWith("https://nav.tum.de/"))
        assertEquals("Karte öffnet keine Details", AppScreen.OVERVIEW, state.screen)
    }
}
