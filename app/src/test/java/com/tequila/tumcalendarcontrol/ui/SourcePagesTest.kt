/*
 * Aufgabe: Bedienung und Bildbasis der Seiten Lesezeitraum, Kursauswahl (Kurs- und Datumsansicht) und Teilnahme festhalten.
 * Ein-/Ausgabe: Synthetischer gespeicherter Datenstand -> Compose-Interaktionen, Zustandsprüfungen und PNG-Dateien.
 * Nebenwirkungen: Nur lokale Testbilder.
 * Nicht zuständig: echtes Lesen, Regelauflösung oder Kalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import com.github.takahirom.roborazzi.captureRoboImage
import com.tequila.tumcalendarcontrol.i18n.AppLanguage
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import java.time.Instant
import org.junit.Assert.assertEquals
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
class SourcePagesTest {
    @get:Rule val composeRule = createComposeRule()

    private val start = Instant.parse("2026-10-05T08:00:00Z")
    private fun occurrence(uid: String, instance: String, title: String, day: Long) =
        SourceCalendarOccurrence(uid, instance, title, start.plusSeconds(day * 86_400), start.plusSeconds(day * 86_400 + 5_400), false, "Raum ${uid.uppercase()}")

    /** Drei synthetische Kurse; der erste mit zwei Lehrtypen-Quellen. Fester Speicherzeitpunkt, damit Bilder nicht vom Tag abhängen. */
    private val items = listOf(
        occurrence("a-vo", "a1", "Synthetik Messtechnik", 0), occurrence("a-vo", "a2", "Synthetik Messtechnik", 7),
        occurrence("a-ue", "a3", "Synthetik Messtechnik", 2), occurrence("b", "b1", "Synthetik Regelung", 1),
        occurrence("b", "b2", "Synthetik Regelung", 8), occurrence("c", "c1", "Synthetik Signale", 3),
    )

    private fun loaded(screen: AppScreen = AppScreen.SOURCE_PREVIEW) = CalendarControlState(
        initialSourceSnapshots = SavedSourceSnapshots(latest = SavedSourceSnapshot(start, items.size, emptyMap(), items)),
        initialSyncDestinations = SyncDestinations(localCalendarEnabled = true),
    ).also {
        it.themeMode = ThemeMode.LIGHT
        it.lightPaletteStyle = LightPaletteStyle.WARM_WHITE
        when (screen) {
            AppScreen.SOURCE_RANGE -> it.openSourceRange()
            AppScreen.SOURCE_PREVIEW -> it.openSourceCourseSelector()
            else -> Unit
        }
    }

    private fun render(state: CalendarControlState, language: AppLanguage = AppLanguage.CHINESE) {
        val selected = mutableStateOf(language)
        composeRule.setContent {
            CompositionLocalProvider(LocalAppLanguage provides LanguageSelection(selected.value) { selected.value = it }) { CalendarControlSurface(state) }
        }
        composeRule.waitForIdle()
    }

    private fun capture(name: String) = composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")

    /** Ganz ans Ende scrollen: performScrollToNode hält nur knapp sichtbar, das Ziel läge sonst unter der schwebenden Aktion. */
    private fun scrollToEnd(tag: String) {
        composeRule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        composeRule.waitForIdle()
    }

    @Test fun `读取页有已保存课程时浮动按钮直接继续`() {
        val state = loaded(AppScreen.SOURCE_RANGE)
        render(state)
        composeRule.onNodeWithTag("source-saved-card").assertIsDisplayed()
        capture("source_range_redesign_412x892")
        composeRule.onNodeWithTag("source-range-action").performClick()
        assertEquals(AppScreen.SOURCE_PREVIEW, state.screen)
    }

    /** Ohne Auswahl ist „weiter“ gesperrt; Schnellauswahl „ganzer Kurs“ folgt dem zuletzt aufgeklappten Kurs. */
    @Test fun `选课页快捷选择与当前课程`() {
        val state = loaded()
        render(state)
        composeRule.onNodeWithTag("source-next").assertIsNotEnabled()
        composeRule.onNodeWithTag("source-current-course").assertTextEquals("当前课程：Synthetik Messtechnik")
        composeRule.onNodeWithTag("source-selection-list").performScrollToNode(hasText("Synthetik Regelung"))
        composeRule.onNodeWithText("Synthetik Regelung").performClick()
        composeRule.onNodeWithTag("source-current-course").assertTextEquals("当前课程：Synthetik Regelung")
        composeRule.onNodeWithTag("source-select-course").performClick()
        assertEquals(2, state.selectedSourceOccurrences.size)
        composeRule.onNodeWithTag("source-selection-list").performScrollToNode(hasTestTag("source-occurrence"))
        capture("source_selection_redesign_412x892")
        composeRule.onNodeWithTag("source-selection-list").performScrollToIndex(0)
        composeRule.onNodeWithTag("source-select-all").performClick()
        assertEquals(items.size, state.selectedSourceOccurrences.size)
        composeRule.onNodeWithTag("source-next").performClick()
        assertEquals(AppScreen.SOURCE_ATTENDANCE, state.screen)
    }

    @Test fun `按日期选课 点日期后勾选当天课次`() {
        val state = loaded()
        render(state)
        composeRule.onNodeWithText("按日期").performClick()
        scrollToEnd("source-selection-list")
        composeRule.onNodeWithTag("date-day-6").performClick()
        scrollToEnd("source-selection-list")
        composeRule.onAllNodesWithTag("source-occurrence")[0].performClick()
        assertEquals(setOf(items[3].identity), state.selectedSourceOccurrences)
        capture("source_selection_dates_redesign_412x892")
    }

    @Test @Config(sdk = [36], qualifiers = "w840dp-h892dp-420dpi")
    fun `展开屏选课 左栏控制右栏列表`() {
        val state = loaded()
        state.selectAllSourceOccurrences()
        render(state)
        composeRule.onNodeWithTag("source-list-viewport").assertIsDisplayed()
        capture("source_selection_wide_redesign_840x892")
    }

    @Test fun `德语选课页长文案`() {
        render(loaded(), AppLanguage.GERMAN)
        composeRule.onNodeWithText("Schnellauswahl").assertIsDisplayed()
        capture("source_selection_de_412x892")
    }

    @Test fun `统一设置出席与收藏 取消返回且不保存规则`() {
        val state = loaded()
        state.openSourceAttendanceEditor(items.map { it.identity }.toSet())
        render(state)
        composeRule.onNodeWithTag("source-attendance-STOP_ATTENDING").performClick()
        capture("source_attendance_redesign_412x892")
        scrollToEnd("source-attendance-content")
        composeRule.onNodeWithTag("source-attendance-cancel").performClick()
        assertEquals(AppScreen.SOURCE_PREVIEW, state.screen)
        assertTrue(state.ruleLedger.rules.isEmpty())
    }
}
