/*
 * Aufgabe: Einstellungsseite der Widgets prüfen: Einstieg über Einstellungen und über das Widget-Einstiegsziel,
 *   Optionen werden gespeichert, Vorschau zeigt die gewählte Größe, „Systemaussehen folgen“ und Quadrat-Hinweis;
 *   Seitenbilder hell.
 * Ein-/Ausgabe: Leerer Zustand (Beispieltermine in der Vorschau) -> Klicks, gespeicherte Einstellungen, PNG-Dateien.
 * Nebenwirkungen: Nur private Test-Präferenzen und lokale Testbilder.
 * Nicht zuständig: Widget-Inhalt (ScheduleWidgetModelTest/ScheduleWidgetRendererTest).
 */
package com.tequila.tumcalendarcontrol.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.tequila.tumcalendarcontrol.widget.WidgetCorner
import com.tequila.tumcalendarcontrol.widget.WidgetPreferences
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
class WidgetSettingsPagesTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    /** Feste Vorschauzeit (Mittwoch 10:30 in Berlin), damit die Bilder unabhängig von der Uhrzeit sind. */
    private val fixedClock = WidgetPreviewClock({ java.time.Instant.parse("2026-10-21T08:30:00Z") }, { java.time.ZoneId.of("Europe/Berlin") })

    /** Vorschau folgt der App-Sprache wie das echte Widget. */
    @org.junit.Before fun chinese() = com.tequila.tumcalendarcontrol.i18n.AppLanguagePreferences(context).save(com.tequila.tumcalendarcontrol.i18n.AppLanguage.CHINESE)

    @Test fun `设置进入小组件页 选项保存`() {
        val state = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT; it.lightPaletteStyle = LightPaletteStyle.WARM_WHITE; it.open(AppScreen.SETTINGS) }
        composeRule.setContent { CompositionLocalProvider(LocalWidgetPreviewClock provides fixedClock) { CalendarControlSurface(state) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-widgets"))
        composeRule.onNode(hasClickAction() and hasAnyAncestor(hasTestTag("settings-widgets")), useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(AppScreen.WIDGETS, state.screen)
        composeRule.onNodeWithTag("widgets-preview").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/widgets_settings_412x892.png")

        composeRule.onNodeWithTag("widgets-hide-gray").performClick()
        composeRule.onNodeWithTag("widgets-content").performScrollToNode(hasTestTag("widgets-corner"))
        composeRule.onNode(hasText("圆润") and hasAnyAncestor(hasTestTag("widgets-corner"))).performClick()
        composeRule.waitForIdle()
        val saved = WidgetPreferences(context).load()
        assertFalse(saved.hideStopAttending)
        assertEquals(WidgetCorner.ROUND, saved.corner)
        assertTrue(state.handleBack())
        assertEquals(AppScreen.SETTINGS, state.screen)
    }

    @Test fun `预览可切换为大尺寸`() {
        val state = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT; it.open(AppScreen.WIDGETS) }
        composeRule.setContent { CompositionLocalProvider(LocalWidgetPreviewClock provides fixedClock) { CalendarControlSurface(state) } }
        composeRule.waitForIdle()
        composeRule.onNode(hasText("本周") and hasAnyAncestor(hasTestTag("widgets-preview-size"))).performClick()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/widgets_settings_large_412x892.png")
    }

    /** Countdown-Abschnitt: Prüfung anheften speichert die ID; Vorschau auf Countdown umschalten. */
    @Test fun `倒数日置顶与预览`() {
        val state = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT; it.open(AppScreen.WIDGETS) }
        state.addManualEvent("Synthetik Analysis", "2026-11-02", "9", "0", "11", "0", com.tequila.tumcalendarcontrol.domain.AttendanceState.MUST_ATTEND, com.tequila.tumcalendarcontrol.sync.ManualEventKind.EXAM)
        composeRule.setContent { CompositionLocalProvider(LocalWidgetPreviewClock provides fixedClock) { CalendarControlSurface(state) } }
        composeRule.waitForIdle()
        composeRule.onNode(hasText("倒数日") and hasAnyAncestor(hasTestTag("widgets-preview-size"))).performClick()
        val id = state.ruleLedger.manualEvents.single().id
        composeRule.onNodeWithTag("widgets-content").performScrollToNode(hasTestTag("widgets-pin-$id"))
        composeRule.onNodeWithTag("widgets-pin-$id").performClick()
        composeRule.waitForIdle()
        assertEquals(id, WidgetPreferences(context).load().countdownPinnedId)
        composeRule.onNodeWithTag("widgets-content").performScrollToNode(hasTestTag("widgets-preview-size"))
        composeRule.onRoot().captureRoboImage("src/test/screenshots/widgets_settings_countdown_412x892.png")
    }

    /** Platziertes Widget (Robolectric): Seite bearbeitet genau dieses; Quadrat und Breite landen unter seiner ID. */
    @Test fun `编辑桌面上的单个组件与方块大小`() {
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val id = org.robolectric.Shadows.shadowOf(manager).createWidget(com.tequila.tumcalendarcontrol.widget.ScheduleWidgetCountdown::class.java, com.tequila.tumcalendarcontrol.R.layout.widget_countdown)
        val state = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT; it.editingWidgetId = id; it.open(AppScreen.WIDGETS) }
        composeRule.setContent { CompositionLocalProvider(LocalWidgetPreviewClock provides fixedClock) { CalendarControlSurface(state) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("widgets-target-$id").assertExists()
        // Für ein Countdown-Widget keine Kursoptionen und kein „Hinzufügen“.
        assertEquals(0, composeRule.onAllNodes(hasTestTag("widgets-hide-blue")).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodes(hasTestTag("widgets-pin")).fetchSemanticsNodes().size)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/widgets_edit_placed_412x892.png")
        composeRule.onNodeWithTag("widgets-content").performScrollToNode(hasTestTag("widgets-square"))
        composeRule.onNodeWithTag("widgets-square").performClick()
        composeRule.waitForIdle()
        val preferences = WidgetPreferences(context)
        assertEquals("Countdown startet quadratisch, Klick schaltet aus", false, preferences.load(id, com.tequila.tumcalendarcontrol.widget.WidgetSize.COUNTDOWN).square)
        assertEquals("Standard unverändert", false, preferences.load().square)
    }

    /** Seite schon offen (Standard), dann „Bearbeiten“ auf dem Startbildschirm: Ziel wechselt zum Widget (Gerätefund). */
    @Test fun `页面已打开时从桌面编辑进入对应组件`() {
        val state = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT; it.open(AppScreen.WIDGETS) }
        composeRule.setContent { CompositionLocalProvider(LocalWidgetPreviewClock provides fixedClock) { CalendarControlSurface(state) } }
        composeRule.waitForIdle()
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val id = org.robolectric.Shadows.shadowOf(manager).createWidget(com.tequila.tumcalendarcontrol.widget.ScheduleWidgetLarge::class.java, com.tequila.tumcalendarcontrol.R.layout.widget_week)
        state.editingWidgetId = id
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("widgets-target-$id").assertIsSelected()
        assertEquals("Wunsch verbraucht", null, state.editingWidgetId)
    }

    /** „Systemaussehen folgen“: Schalter speichert, Systemradius sichtbar, Quadrat gesperrt; Ausschalten behält manuelle Werte. */
    @Test fun `跟随系统外观开关`() {
        WidgetPreferences(context).save(com.tequila.tumcalendarcontrol.widget.WidgetSettings(corner = WidgetCorner.SMALL, blockHeightPercent = 80))
        val state = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT; it.open(AppScreen.WIDGETS) }
        composeRule.setContent { CompositionLocalProvider(LocalWidgetPreviewClock provides fixedClock) { CalendarControlSurface(state) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("widgets-content").performScrollToNode(hasTestTag("widgets-follow-system"))
        composeRule.onNodeWithTag("widgets-follow-system").performClick()
        composeRule.waitForIdle()
        assertTrue(WidgetPreferences(context).load().followSystem)
        composeRule.onNodeWithTag("widgets-system-radius").assertExists()
        composeRule.onNodeWithTag("widgets-content").performScrollToNode(hasTestTag("widgets-square"))
        composeRule.onNodeWithTag("widgets-square").assertIsNotEnabled()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/widgets_follow_system_412x892.png")
        composeRule.onNodeWithTag("widgets-content").performScrollToNode(hasTestTag("widgets-follow-system"))
        composeRule.onNodeWithTag("widgets-follow-system").performClick()
        composeRule.waitForIdle()
        val saved = WidgetPreferences(context).load()
        assertFalse(saved.followSystem)
        assertEquals("manuelle Werte bleiben", WidgetCorner.SMALL to 80, saved.corner to saved.blockHeightPercent)
        composeRule.onNodeWithTag("widgets-square").assertIsEnabled()
    }

    /** Quadrat in einem länglichen Feld (1×2, 73×211 dp): Hinweis, dass Aufziehen nicht wirkt (Gerätefund). */
    @Test fun `长条组件开正方形时提示`() {
        assertTrue(elongated(73, 211))
        assertFalse(elongated(155, 211))
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val id = org.robolectric.Shadows.shadowOf(manager).createWidget(com.tequila.tumcalendarcontrol.widget.ScheduleWidgetCheck::class.java, com.tequila.tumcalendarcontrol.R.layout.widget_check)
        manager.updateAppWidgetOptions(id, android.os.Bundle().apply {
            putParcelableArrayList(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(android.util.SizeF(73f, 211f)))
        })
        WidgetPreferences(context).save(id, com.tequila.tumcalendarcontrol.widget.WidgetSettings(square = true))
        val state = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT; it.editingWidgetId = id; it.open(AppScreen.WIDGETS) }
        composeRule.setContent { CompositionLocalProvider(LocalWidgetPreviewClock provides fixedClock) { CalendarControlSurface(state) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("widgets-content").performScrollToNode(hasTestTag("widgets-square-hint"))
        composeRule.onNodeWithTag("widgets-square-hint").assertExists()
        composeRule.onNodeWithTag("widgets-square").performClick()
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodes(hasTestTag("widgets-square-hint")).fetchSemanticsNodes().size)
    }

    @Test fun `小组件入口打开设置页`() {
        val intent = AppEntryIntents.intent(context, AppEntry.WidgetSettings())
        assertEquals(AppEntry.WidgetSettings(), AppEntryIntents.parse(intent))
        val exam = AppEntry.ManualEvents(com.tequila.tumcalendarcontrol.sync.ManualEventKind.EXAM)
        assertEquals(exam, AppEntryIntents.parse(AppEntryIntents.intent(context, exam)))
        assertEquals(AppEntry.ManualEvents(null), AppEntryIntents.parse(AppEntryIntents.intent(context, AppEntry.ManualEvents())))
    }
}
