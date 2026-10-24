/*
 * Aufgabe: Karte „Demnächst“ und Countdown in der Terminliste prüfen (feste Uhr); ohne Prüfungen keine Karte.
 * Ein-/Ausgabe: Synthetische Prüfungen/Fristen -> Anzeige, Navigation und PNG-Datei.
 * Nebenwirkungen: Nur lokale Testbilder.
 * Nicht zuständig: Countdown-Berechnung (DeadlineCountdownTest).
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.sync.ManualEventKind
import java.time.LocalDateTime
import java.time.ZoneId
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
class UpcomingDeadlinesPagesTest {
    @get:Rule val composeRule = createComposeRule()
    private val zone = ZoneId.of("Europe/Berlin")
    private val clock = CountdownClock({ LocalDateTime.of(2026, 10, 21, 10, 30).atZone(zone).toInstant() }, { zone })

    private fun state() = CalendarControlState().also {
        it.themeMode = ThemeMode.LIGHT; it.lightPaletteStyle = LightPaletteStyle.WARM_WHITE
        it.addManualEvent("Synthetik Analysis", "2026-11-02", "9", "0", "11", "0", AttendanceState.MUST_ATTEND, ManualEventKind.EXAM)
        it.addManualEvent("Synthetik Abgabe", "2026-10-22", "", "", "12", "0", AttendanceState.MUST_ATTEND, ManualEventKind.DEADLINE)
        it.addManualEvent("Synthetik Treffen", "2026-10-23", "9", "0", "10", "0", AttendanceState.CHOOSE_TO_ATTEND)
        it.addManualEvent("Synthetik Fern", "2026-12-20", "9", "0", "11", "0", AttendanceState.MUST_ATTEND, ManualEventKind.EXAM)
    }

    private fun render(state: CalendarControlState) {
        composeRule.setContent { CompositionLocalProvider(LocalCountdownClock provides clock) { CalendarControlSurface(state) } }
        composeRule.waitForIdle()
    }

    @Test fun `首页显示即将到来 点击进入校园事项`() {
        val state = state().also { it.open(AppScreen.OVERVIEW) }
        render(state)
        // Frist morgen 12:00 (26 h), Prüfung am 2.11. (12 Tage); die Prüfung im Dezember liegt außerhalb von 30 Tagen.
        assertEquals(2, composeRule.onAllNodesWithTag("upcoming-deadline").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("还有 26 小时").assertExists()
        composeRule.onNodeWithText("还有 12 天").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/overview_upcoming_412x892.png")
        composeRule.onAllNodesWithTag("upcoming-deadline")[0].performClick()
        composeRule.waitForIdle()
        assertEquals(AppScreen.MANUAL_EVENTS, state.screen)
    }

    @Test fun `校园事项列表显示类型与倒计时`() {
        val state = state().also { it.openManualEvents() }
        render(state)
        composeRule.onNodeWithText("截止 · 还有 26 小时").assertExists()
        composeRule.onNodeWithText("考试 · 还有 60 天").assertExists()
    }

    @Test fun `没有考试或截止时不显示卡片`() {
        render(CalendarControlState().also { it.open(AppScreen.OVERVIEW) })
        assertEquals(0, composeRule.onAllNodes(hasTestTag("upcoming-deadlines")).fetchSemanticsNodes().size)
    }
}
