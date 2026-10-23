/*
 * Aufgabe: Durchgängige Abläufe über die Oberfläche prüfen, damit spätere Änderungen frühe Funktionen nicht brechen:
 *   gespeicherte Kurse -> Auswahl -> Teilnahme -> Schreibvorschau sowie Übersicht -> Details -> Favorit/Bearbeiten/Teilnahme.
 * Ein-/Ausgabe: Synthetische gespeicherte Termine und Beispielkurse -> Klicks, Eingaben und Zustandsprüfungen je Schritt.
 * Nebenwirkungen: Keine; keine Kalender, Konten, Netzwerke oder Dateien.
 * Nicht zuständig: echte Ausgabe (endet vor dem Schreiben) oder Gerätedarstellung.
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.tequila.tumcalendarcontrol.domain.AttendanceState
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
class EndToEndFlowTest {
    @get:Rule val composeRule = createComposeRule()

    private val start = Instant.parse("2026-10-05T08:00:00Z")
    private val items = (0L until 6L).map { day ->
        SourceCalendarOccurrence("uid-${day % 3}", "inst-$day", listOf("Synthetik Messtechnik", "Synthetik Regelung", "Synthetik Signale")[(day % 3).toInt()],
            start.plusSeconds(day * 86_400), start.plusSeconds(day * 86_400 + 5_400), false, "Raum")
    }

    private fun render(state: CalendarControlState) {
        composeRule.setContent { CalendarControlSurface(state) }
        composeRule.waitForIdle()
    }

    /** Listenzeilen tragen die Kennung am Rahmen; Klickaktion des inneren Elements direkt auslösen. */
    private fun clickRow(tag: String) {
        composeRule.onNode(hasClickAction() and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun scrollToEnd(tag: String) {
        composeRule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        composeRule.waitForIdle()
    }

    private fun replaceText(fieldTag: String, text: String) {
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(fieldTag))).performTextReplacement(text)
        composeRule.waitForIdle()
    }

    /** Gespeicherte Kurse weiterverwenden, alles auswählen, Teilnahme setzen, zur Schreibvorschau – Auswahl bleibt jeweils erhalten. */
    @Test fun `已保存课程到写入确认的完整流程`() {
        val state = CalendarControlState(
            initialSourceSnapshots = SavedSourceSnapshots(latest = SavedSourceSnapshot(start, items.size, emptyMap(), items)),
            initialSyncDestinations = SyncDestinations(localCalendarEnabled = true),
        ).also { it.openSourceRange() }
        render(state)

        composeRule.onNodeWithTag("source-range-action").performClick()
        assertEquals(AppScreen.SOURCE_PREVIEW, state.screen)
        assertTrue(!state.sourceReadRequested)

        composeRule.onNodeWithTag("source-quick").performClick()
        composeRule.onNodeWithTag("source-select-all").performClick()
        assertEquals(items.size, state.selectedSourceOccurrences.size)
        composeRule.onNodeWithTag("source-next").performClick()
        assertEquals(AppScreen.SOURCE_ATTENDANCE, state.screen)

        composeRule.onNodeWithTag("source-attendance-MUST_ATTEND").performClick()
        scrollToEnd("source-attendance-content")
        composeRule.onNodeWithTag("source-scope-ONLY_THIS_OCCURRENCE").performClick()
        composeRule.onNodeWithTag("source-attendance-save").performClick()
        composeRule.waitForIdle()
        assertEquals(AppScreen.SOURCE_PREVIEW, state.screen)
        assertTrue("Regeln gespeichert", state.ruleLedger.rules.isNotEmpty())
        assertEquals("Auswahl bleibt nach dem Speichern", items.size, state.selectedSourceOccurrences.size)

        // Teilnahme gespeichert: Hauptaktion wird „Ausgabe prüfen“, Teilnahme rückt als Nebenaktion nach links.
        composeRule.onNodeWithTag("source-attendance-again").assertIsDisplayed()
        composeRule.onNodeWithTag("source-next").performClick()
        assertEquals(AppScreen.MANAGED_SYNC_PREVIEW, state.screen)
        composeRule.onNodeWithTag("write-count").assertTextEquals(items.size.toString())

        assertTrue(state.handleBack())
        assertEquals(AppScreen.SOURCE_PREVIEW, state.screen)
        assertEquals("Auswahl bleibt nach der Rückkehr", items.size, state.selectedSourceOccurrences.size)
    }

    /** Übersicht -> Termin -> Details: Favorit, lokale Bearbeitung und Teilnahme mit Datumsprüfung (frühere Korrektur ac70662). */
    @Test fun `首页到详情 收藏 编辑 出席与日期校验`() {
        val state = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT }
        render(state)

        composeRule.onNodeWithText("课程 A").performClick()
        composeRule.onAllNodesWithText("示例校区")[0].performClick()
        assertEquals(AppScreen.DETAIL, state.contentScreen)
        val courseId = state.selectedCourse.id

        composeRule.onNodeWithTag("detail-favorite").performClick()
        assertTrue(state.selectedCourse.isFavorite)
        assertTrue(state.favoriteCourses.any { it.id == courseId })

        clickRow("detail-edit")
        assertEquals(AppScreen.COURSE_EDIT, state.screen)
        replaceText("edit-location", "Synthetik Hörsaal")
        composeRule.onNodeWithTag("edit-save").performClick()
        composeRule.waitForIdle()
        assertEquals(AppScreen.DETAIL, state.screen)
        assertEquals("Synthetik Hörsaal", state.selectedCourse.localOverride.location)

        composeRule.onNodeWithTag("detail-attendance").performClick()
        assertEquals(AppScreen.ATTENDANCE, state.screen)
        composeRule.onNodeWithTag("attendance-STOP_ATTENDING").performClick()
        scrollToEnd("attendance-content")
        composeRule.onNodeWithTag("scope-SPECIFIED_AND_FUTURE").performClick()
        replaceText("attendance-date", "kein Datum")
        composeRule.onNodeWithTag("attendance-confirm").performClick()
        composeRule.waitForIdle()
        assertEquals("Ungültiges Datum: Seite bleibt offen", AppScreen.ATTENDANCE, state.screen)
        composeRule.onNodeWithText("请输入 YYYY-MM-DD 格式的指定日期。").assertIsDisplayed()

        replaceText("attendance-date", "2026-10-30")
        composeRule.onNodeWithTag("attendance-confirm").performClick()
        composeRule.waitForIdle()
        assertEquals(AppScreen.DETAIL, state.screen)
        // Grau ab Datum: frühere Termine bleiben unverändert (keine rückwirkende Änderung).
        assertEquals(courseId, state.selectedCourse.id)
        assertTrue(state.ruleLedger.rules.any { it.sourceUid == courseId })
    }

    /** Hauptziele über die untere Navigation; Detail zurück führt zur Herkunftsseite. */
    @Test fun `底部导航与详情返回来源页`() {
        val state = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT }
        render(state)
        composeRule.onNodeWithText("收藏").performClick()
        assertEquals(AppScreen.FAVORITES, state.screen)
        composeRule.onNodeWithText("搜索").performClick()
        assertEquals(AppScreen.SEARCH, state.screen)
        composeRule.onNodeWithText("课程 B").performClick()
        composeRule.onAllNodesWithText("示例校区")[0].performClick()
        assertEquals(AppScreen.DETAIL, state.screen)
        assertTrue(state.handleBack())
        assertEquals(AppScreen.SEARCH, state.screen)
        composeRule.onNodeWithText("设置").performClick()
        clickRow("settings-appearance")
        assertEquals(AppScreen.APPEARANCE, state.screen)
        assertTrue(state.handleBack())
        assertEquals(AppScreen.SETTINGS, state.screen)
        assertEquals(AttendanceState.entries.size, 3)
    }
}
