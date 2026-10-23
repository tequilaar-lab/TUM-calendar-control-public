/*
 * Aufgabe: Bedienung und Bildbasis der Ausgabeseiten (Schreibbestätigung, Bereinigung mit Glasdialog, Offline-Beispiel)
 *   sowie des Neulese-Dialogs festhalten.
 * Ein-/Ausgabe: Synthetischer Datenstand und Regeln -> Compose-Interaktionen, Zustandsprüfungen und PNG-Dateien.
 * Nebenwirkungen: Nur lokale Testbilder; keine Kalender, Konten oder Netzwerke.
 * Nicht zuständig: echte Ausgabe, Zielabgleich oder Calendar Provider.
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.github.takahirom.roborazzi.captureRoboImage
import com.tequila.tumcalendarcontrol.i18n.AppLanguage
import com.tequila.tumcalendarcontrol.sync.PrivateSyncConfiguration
import com.tequila.tumcalendarcontrol.sync.RuleLedger
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SyncRunSummary
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
class OutputPagesTest {
    @get:Rule val composeRule = createComposeRule()

    private val start = Instant.parse("2026-10-05T08:00:00Z")
    private val items = (0L until 4L).map { day ->
        SourceCalendarOccurrence("uid-${day % 2}", "inst-$day", if (day % 2 == 0L) "Synthetik Messtechnik" else "Synthetik Regelung",
            start.plusSeconds(day * 86_400), start.plusSeconds(day * 86_400 + 5_400), false, "Raum")
    }

    /** Fester Zeitpunkt des Ergebnisses, damit Bilder nicht vom Tag abhängen. */
    private val lastSummary = SyncRunSummary(Instant.parse("2026-10-20T10:00:00Z"), 3, 1, 0, 2, failureSummary = "Synthetik: Google-Kalender nicht erreichbar")

    private fun state(dark: Boolean = false) = CalendarControlState(
        initialSourceSnapshots = SavedSourceSnapshots(latest = SavedSourceSnapshot(start, items.size, emptyMap(), items)),
        initialRuleLedger = RuleLedger(lastSummary = lastSummary),
        initialSyncDestinations = SyncDestinations(localCalendarEnabled = true),
    ).also {
        it.themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT
        it.lightPaletteStyle = LightPaletteStyle.WARM_WHITE
    }

    /** Beide Ziele, nichts autorisiert: Verbindungsschaltflächen sichtbar, Hauptaktion meldet fehlende Rechte. */
    private fun writeState(dark: Boolean = false) = state(dark).also {
        it.openSourceCourseSelector()
        it.selectAllSourceOccurrences()
        it.setSyncDestinationEnabled(SyncDestination.GOOGLE_CALENDAR, true)
        it.prepareCalendarWrite()
    }

    private fun render(state: CalendarControlState, language: AppLanguage = AppLanguage.CHINESE) {
        val selected = mutableStateOf(language)
        composeRule.setContent {
            CompositionLocalProvider(LocalAppLanguage provides LanguageSelection(selected.value) { selected.value = it }) { CalendarControlSurface(state) }
        }
        composeRule.waitForIdle()
    }

    private fun capture(name: String) = composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")

    private fun scrollToEnd(tag: String) {
        composeRule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        composeRule.waitForIdle()
    }

    @Test fun `写入确认 两本日历分别显示并保留上次结果`() {
        val state = writeState()
        render(state)
        assertEquals(AppScreen.MANAGED_SYNC_PREVIEW, state.screen)
        composeRule.onNodeWithTag("write-count").assertIsDisplayed()
        capture("write_redesign_412x892")
        scrollToEnd("write-content")
        composeRule.onNodeWithTag("write-connect-google").assertIsDisplayed()
        composeRule.onNodeWithTag("write-last-result").assertIsDisplayed()
        capture("write_redesign_end_412x892")
        // Ohne Rechte schreibt die Hauptaktion nichts, sondern meldet den fehlenden Schritt.
        composeRule.onNodeWithTag("write-confirm").performClick()
        assertFalse(state.managedSyncInProgress)
    }

    @Test fun `写入确认 德语与深色`() {
        render(writeState(dark = true), AppLanguage.GERMAN)
        capture("write_de_dark_412x892")
    }

    @Test fun `没有待写内容时只显示说明`() {
        val state = state()
        state.open(AppScreen.MANAGED_SYNC_PREVIEW)
        render(state)
        composeRule.onNodeWithTag("write-last-result").assertIsDisplayed()
        capture("write_empty_412x892")
    }

    /** Zweite Bestätigung als Glasdialog; Abbrechen löscht nichts und schließt den Dialog. */
    @Test fun `清理 需二次确认且取消不执行`() {
        val state = state()
        state.updatePrivateSyncConfiguration(PrivateSyncConfiguration(localCalendarId = "synthetic-local"))
        state.openOutputCleanup()
        render(state)
        composeRule.onNodeWithTag("cleanup-target-LOCAL_CALENDAR").performClick()
        composeRule.onNodeWithTag("cleanup-mode-ALL").performClick()
        capture("cleanup_redesign_412x892")
        composeRule.onNodeWithTag("cleanup-continue").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("confirm-title").assertIsDisplayed()
        capture("cleanup_confirm_412x892")
        composeRule.onNodeWithTag("confirm-cancel").performClick()
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithTagCount("confirm-dialog"))
        assertFalse(state.managedSyncInProgress)
    }

    @Test fun `清理确认框 深色`() {
        val state = state(dark = true)
        state.updatePrivateSyncConfiguration(PrivateSyncConfiguration(localCalendarId = "synthetic-local"))
        state.openOutputCleanup()
        render(state)
        composeRule.onNodeWithTag("cleanup-target-LOCAL_CALENDAR").performClick()
        composeRule.onNodeWithTag("cleanup-mode-ALL").performClick()
        composeRule.onNodeWithTag("cleanup-continue").performClick()
        composeRule.onNodeWithTag("confirm-title").assertIsDisplayed()
        capture("cleanup_confirm_dark_412x892")
    }

    @Test fun `离线示例只显示合成数据`() {
        val state = state()
        state.open(AppScreen.SYNC_PREVIEW)
        render(state)
        composeRule.onNodeWithTag("offline-summary").assertIsDisplayed()
        capture("offline_preview_redesign_412x892")
    }

    @Test fun `重新读取使用玻璃确认框 取消后不读取`() {
        val state = state()
        state.updatePrivateSyncConfiguration(PrivateSyncConfiguration(tumSubscriptionUrl = "https://synthetic.example.test/calendar"))
        state.openSourceRange()
        render(state)
        scrollToEnd("source-range-content")
        composeRule.onNodeWithTag("source-reload").performClick()
        composeRule.onNodeWithTag("confirm-title").assertIsDisplayed()
        capture("source_reload_confirm_412x892")
        composeRule.onNodeWithTag("confirm-cancel").performClick()
        assertFalse(state.sourceReloadConfirmationPending)
        assertFalse(state.sourceReadRequested)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}
