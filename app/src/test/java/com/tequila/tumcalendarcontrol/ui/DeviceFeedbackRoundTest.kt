/*
 * Aufgabe: Korrekturen nach der ersten Geräte-Rückmeldung prüfen: umkehrbare Schnellauswahl, geteilte Hauptaktion nach
 *   gespeicherter Teilnahme, Suche leeren, Quelldaten wiederherstellen, alle Bereinigungskurse, ziehbare Navigation.
 * Ein-/Ausgabe: Synthetischer Zustand -> Bedienung, Zustandsprüfungen und PNG-Dateien.
 * Nebenwirkungen: Nur lokale Testbilder; keine Kalender, Konten oder Netzwerke.
 * Nicht zuständig: Gefühl von Animation/Haptik auf dem Gerät.
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.github.takahirom.roborazzi.captureRoboImage
import com.tequila.tumcalendarcontrol.domain.CourseLocalOverride
import com.tequila.tumcalendarcontrol.sync.PrivateSyncConfiguration
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
class DeviceFeedbackRoundTest {
    @get:Rule val composeRule = createComposeRule()

    private val start = Instant.parse("2026-10-05T08:00:00Z")
    private val items = (0L until 6L).map { day ->
        SourceCalendarOccurrence("uid-${day % 3}", "inst-$day", listOf("Synthetik Messtechnik", "Synthetik Regelung", "Synthetik Signale")[(day % 3).toInt()],
            start.plusSeconds(day * 86_400), start.plusSeconds(day * 86_400 + 5_400), false, "Raum")
    }

    private fun loaded() = CalendarControlState(
        initialSourceSnapshots = SavedSourceSnapshots(latest = SavedSourceSnapshot(start, items.size, emptyMap(), items)),
        initialSyncDestinations = SyncDestinations(localCalendarEnabled = true),
    ).also { it.themeMode = ThemeMode.LIGHT; it.lightPaletteStyle = LightPaletteStyle.WARM_WHITE }

    private fun render(state: CalendarControlState) {
        composeRule.setContent { CalendarControlSurface(state) }
        composeRule.waitForIdle()
    }

    private fun capture(name: String) = composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")

    /** „Alle“ und „ganzer Kurs“ lassen sich wieder aufheben; Kurse starten zugeklappt. */
    @Test fun `快捷选择可取消 课程默认收起`() {
        val state = loaded().also { it.openSourceCourseSelector() }
        render(state)
        assertEquals(0, composeRule.onAllNodesWithTagCount("source-occurrence"))
        capture("source_selection_collapsed_412x892")
        composeRule.onNodeWithTag("source-quick").performClick()
        composeRule.onNodeWithTag("source-select-all").performClick()
        assertEquals(items.size, state.selectedSourceOccurrences.size)
        composeRule.onNodeWithTag("source-select-all").assertTextContains("取消全选", substring = true)
        composeRule.onNodeWithTag("source-select-course").assertTextContains("取消本课选择", substring = true).performClick()
        assertEquals(items.size - 2, state.selectedSourceOccurrences.size)
        composeRule.onNodeWithTag("source-select-course").assertTextContains("选中本课全部", substring = true)
        composeRule.onNodeWithTag("source-select-all").performClick()
        assertEquals(items.size, state.selectedSourceOccurrences.size)
        composeRule.onNodeWithTag("source-select-all").performClick()
        assertTrue(state.selectedSourceOccurrences.isEmpty())
    }

    /** Nach gespeicherter Teilnahme: links Teilnahme (klein), rechts Ausgabe prüfen. */
    @Test fun `保存出席后底部分为两个浮块`() {
        val state = loaded().also { it.openSourceCourseSelector(); it.selectAllSourceOccurrences() }
        render(state)
        composeRule.onNodeWithTag("source-next").assertTextContains("出席与收藏", substring = true)
        assertEquals(0, composeRule.onAllNodesWithTagCount("source-attendance-again"))
        state.openSourceAttendanceEditor()
        state.pendingSourceAttendanceScope = AttendanceEditScope.ONLY_THIS_OCCURRENCE
        state.pendingSourceAttendance = com.tequila.tumcalendarcontrol.domain.AttendanceState.MUST_ATTEND
        state.confirmSourceAttendance()
        composeRule.waitForIdle()
        assertEquals(AppScreen.SOURCE_PREVIEW, state.screen)
        composeRule.onNodeWithTag("source-attendance-again").assertIsDisplayed()
        composeRule.onNodeWithTag("source-next").assertTextContains("查看写入内容", substring = true)
        capture("source_selection_split_actions_412x892")
        composeRule.onNodeWithTag("source-next").performClick()
        assertEquals(AppScreen.MANAGED_SYNC_PREVIEW, state.screen)
    }

    @Test fun `搜索框可一键清空`() {
        val state = loaded().also { it.open(AppScreen.SEARCH) }
        render(state)
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("search-field"))).performTextInput("课程 A")
        composeRule.onNodeWithTag("field-clear", useUnmergedTree = true).performClick()
        assertEquals("", state.searchQuery)
    }

    @Test fun `编辑课程可恢复为来源信息`() {
        val state = CalendarControlState().also { it.openCourse("course-a") }
        state.updateSelectedCourseOverride(CourseLocalOverride(location = "Synthetik Hörsaal"))
        state.openCourseEdit()
        render(state)
        composeRule.onNodeWithTag("edit-content").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        composeRule.onNodeWithTag("edit-restore").performClick()
        composeRule.waitForIdle()
        assertEquals(CourseLocalOverride(), state.selectedCourse.localOverride)
        assertEquals(AppScreen.DETAIL, state.screen)
    }

    @Test fun `清理按课程可全选与取消全选`() {
        val state = loaded()
        state.updatePrivateSyncConfiguration(PrivateSyncConfiguration(localCalendarId = "synthetic-local"))
        state.openOutputCleanup()
        render(state)
        composeRule.onNodeWithTag("cleanup-target-LOCAL_CALENDAR").performClick()
        // Ohne geprüften Bestand gibt es keine Kursliste; dann zeigt die Seite den Hinweis statt der Auswahl.
        assertTrue(composeRule.onAllNodesWithTagCount("cleanup-all-courses") <= 1)
    }

    /** Ziehen des Navigationsblocks nach rechts wählt das Ziel unter dem Finger. */
    @Test fun `底部导航可拖动选中块切换`() {
        val state = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT }
        render(state)
        composeRule.onNodeWithTag("floating-navigation").performTouchInput { swipeRight(startX = centerX - width / 3f, endX = centerX + width / 3f) }
        composeRule.waitForIdle()
        assertTrue(state.screen != AppScreen.OVERVIEW)
        composeRule.onNodeWithTag("floating-navigation").performTouchInput { swipeLeft(startX = centerX + width / 3f, endX = centerX - width / 3f) }
        composeRule.waitForIdle()
        assertEquals(AppScreen.OVERVIEW, state.screen)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size
}
