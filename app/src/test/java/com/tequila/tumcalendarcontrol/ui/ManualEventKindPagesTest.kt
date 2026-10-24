/*
 * Aufgabe: Campustermine mit Art prüfen: Frist mit nur Fälligkeit (30-Minuten-Block davor), Kennzeichen in der Liste,
 *   vorgewählte Art aus einem Einstieg, Fehlerfall ohne Speichern; Seitenbild.
 * Ein-/Ausgabe: Synthetische Eingaben -> gespeicherte Termine, Anzeige und PNG-Datei.
 * Nebenwirkungen: Nur lokale Testbilder.
 * Nicht zuständig: Kodierung (ManualEventKindCodecTest) oder Countdown.
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.github.takahirom.roborazzi.captureRoboImage
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.sync.ManualEventKind
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
class ManualEventKindPagesTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `截止只填截止时间 写为前30分钟`() {
        val state = CalendarControlState()
        assertTrue(state.addManualEvent("Synthetik Abgabe", "2026-11-05", "", "", "23", "59", AttendanceState.MUST_ATTEND, ManualEventKind.DEADLINE))
        val item = state.ruleLedger.manualEvents.single()
        assertEquals(Instant.parse("2026-11-05T22:59:00Z"), item.endsAt)
        assertEquals(Duration.ofMinutes(30), Duration.between(item.startsAt, item.endsAt))
        assertFalse("Ungültige Zeit speichert nichts", state.addManualEvent("x", "2026-11-05", "", "", "25", "0", AttendanceState.MUST_ATTEND, ManualEventKind.DEADLINE))
        assertEquals(1, state.ruleLedger.manualEvents.size)
    }

    @Test fun `校园事项页选择类型并保存`() {
        val state = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT; it.lightPaletteStyle = LightPaletteStyle.WARM_WHITE }
        state.manualEventsInitialKind = ManualEventKind.EXAM
        state.openManualEvents()
        composeRule.setContent { CalendarControlSurface(state) }
        composeRule.waitForIdle()
        composeRule.onNode(hasText("截止") and hasAnyAncestor(hasTestTag("events-kind"))).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("events-due-hour").assertExists()
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("events-title"))).performTextReplacement("Synthetik Abgabe")
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("events-date"))).performTextReplacement("2026-11-05")
        composeRule.onNodeWithTag("events-save").performClick()
        composeRule.waitForIdle()
        assertEquals(ManualEventKind.DEADLINE, state.ruleLedger.manualEvents.single().kind)
        composeRule.onNodeWithText("截止于 2026-11-05 10:00（欧洲/柏林）").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/events_kind_412x892.png")
    }

    /** Ein Einstieg (Verknüpfung) gibt „Prüfung“ vor; das Segment startet dort. */
    @Test fun `入口预选考试`() {
        val state = CalendarControlState().also { it.manualEventsInitialKind = ManualEventKind.EXAM; it.openManualEvents() }
        composeRule.setContent { CalendarControlSurface(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("events-due-hour").assertDoesNotExist()
        assertEquals(AppScreen.MANUAL_EVENTS, state.screen)
    }
}
