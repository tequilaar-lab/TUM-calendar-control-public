/*
 * Aufgabe: Schreibfortschritt in der Oberfläche prüfen: Beschriftung „Schreibe x/y · Ziel“, Hinweis, Anhalten-Schaltfläche,
 *   Anhaltewunsch erreicht den Ausgabefaden, Abschluss setzt alles zurück.
 * Ein-/Ausgabe: Synthetische gespeicherte Termine -> Schreibvorschau, simulierte Meldungen, Klicks und PNG-Datei.
 * Nebenwirkungen: Nur lokale Testbilder; es wird nichts geschrieben (der Ausgabefaden läuft nur in der App).
 * Nicht zuständig: Ausführer und Prüfpunkte (WriteProgressTest).
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.tequila.tumcalendarcontrol.sync.ManagedSyncResult
import com.tequila.tumcalendarcontrol.sync.OutputTarget
import com.tequila.tumcalendarcontrol.sync.PrivateSyncConfiguration
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
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
class WriteProgressPagesTest {
    @get:Rule val composeRule = createComposeRule()

    private val start = Instant.parse("2026-10-05T08:00:00Z")
    private val items = (0L until 6L).map { day ->
        SourceCalendarOccurrence("uid-$day", "inst-$day", "Synthetik $day", start.plusSeconds(day * 86_400), start.plusSeconds(day * 86_400 + 5_400), false, "Raum")
    }

    @Test fun `写入中显示进度并可停止`() {
        val state = CalendarControlState(
            initialSourceSnapshots = SavedSourceSnapshots(latest = SavedSourceSnapshot(start, items.size, emptyMap(), items)),
            initialSyncDestinations = SyncDestinations(localCalendarEnabled = true),
        ).also { it.themeMode = ThemeMode.LIGHT; it.lightPaletteStyle = LightPaletteStyle.WARM_WHITE }
        state.openSourceCourseSelector()
        state.selectAllSourceOccurrences()
        // Berechtigung vor der Vorschau: eine nachträgliche Freigabe verwirft die Vorschau absichtlich.
        state.resolveLocalCalendarPermission(true)
        state.prepareCalendarWrite()
        state.requestManagedSyncExecution()
        assertTrue(state.managedSyncInProgress)
        // Ausgabefaden simulieren: Meldungen sofort übernehmen (post = direkt ausführen).
        val control = state.writeProgress.begin { it() }
        composeRule.setContent { CalendarControlSurface(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("write-confirm").assertTextContains("正在写入日历", substring = true)
        control.report(OutputTarget.LOCAL_CALENDAR, 6, 6)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("write-confirm").assertTextContains("正在写入 6/6", substring = true)
        composeRule.onNodeWithTag("write-progress-note").assertIsDisplayed()
        capture("write_progress_412x892")
        composeRule.onNodeWithTag("write-stop").performClick()
        composeRule.waitForIdle()
        assertTrue(control.stopRequested)
        composeRule.onNodeWithTag("write-confirm").assertTextContains("正在停止", substring = true)

        state.resolveManagedOperation(state.currentManagedOperationToken(), ManagedSyncResult(emptyList(), emptyList()), PrivateSyncConfiguration())
        composeRule.waitForIdle()
        assertFalse(state.writeProgress.active)
        assertEquals(0, composeRule.onAllNodes(hasTestTag("write-progress-note")).fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("write-confirm").assertTextContains("确认并写入日历", substring = true)
    }

    private fun capture(name: String) = composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
}
