/*
 * Aufgabe: Oberfläche der Quelländerungen prüfen: Karte in der Kursauswahl, Kennzeichen am Termin, Hinweis auf
 *   betroffene Auswahl, Unterschiedsseite (auch aus den Einstellungen) und Einstiegsziele aus Hinweis/Widget.
 * Ein-/Ausgabe: Synthetische frühere/neueste Lesung -> Bedienung, Zustandsprüfungen und PNG-Dateien.
 * Nebenwirkungen: Nur lokale Testbilder; keine Kalender, Konten oder Netzwerke.
 * Nicht zuständig: Vergleichslogik im Detail (SourceChangeSummaryTest) oder Hintergrundprüfung auf dem Gerät.
 */
package com.tequila.tumcalendarcontrol.ui

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceOccurrenceIdentity
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class SourceChangePagesTest {
    @get:Rule val composeRule = createComposeRule()

    private val start = Instant.parse("2026-10-05T08:00:00Z")
    private fun occ(uid: String, inst: String, day: Long, title: String, location: String = "Raum 1", shift: Long = 0, cancelled: Boolean = false) =
        SourceCalendarOccurrence(uid, inst, title, start.plusSeconds(day * 86_400 + shift), start.plusSeconds(day * 86_400 + shift + 5_400), cancelled, location)

    private val previous = listOf(
        occ("uid-m", "m1", 0, "Synthetik Messtechnik"), occ("uid-m", "m2", 7, "Synthetik Messtechnik"),
        occ("uid-r", "r1", 1, "Synthetik Regelung"), occ("uid-s", "s1", 2, "Synthetik Signale"),
    )
    private val latest = listOf(
        occ("uid-m", "m1", 0, "Synthetik Messtechnik", shift = 1_800), occ("uid-m", "m2", 7, "Synthetik Messtechnik", cancelled = true),
        occ("uid-r", "r1", 1, "Synthetik Regelung", location = "Raum 2"), occ("uid-s", "s2", 9, "Synthetik Signale"),
    )

    private fun state() = CalendarControlState(
        initialSourceSnapshots = SavedSourceSnapshots(
            latest = SavedSourceSnapshot(start, latest.size, emptyMap(), latest),
            previous = SavedSourceSnapshot(start.minusSeconds(86_400), previous.size, emptyMap(), previous),
        ),
        initialSyncDestinations = SyncDestinations(localCalendarEnabled = true),
    ).also { it.themeMode = ThemeMode.LIGHT; it.lightPaletteStyle = LightPaletteStyle.WARM_WHITE }

    private fun render(state: CalendarControlState) {
        composeRule.setContent { CalendarControlSurface(state) }
        composeRule.waitForIdle()
    }

    private fun capture(name: String) = composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")

    /** Karte zeigt Gesamtzahl; gewählter, geänderter Termin löst den Hinweis aus; aufgeklappt führt sie zur Detailseite. */
    @Test fun `选课页显示变化卡片并进入明细`() {
        val state = state().also { it.openSourceCourseSelector() }
        state.includeSourceOccurrences(setOf(SourceOccurrenceIdentity("uid-m", "m1")))
        render(state)
        composeRule.onNodeWithTag("source-changes-toggle").assertTextContains("5 处变化", substring = true)
        composeRule.onNodeWithTag("source-changes-affected").assertTextContains("1 处变化", substring = true)
        composeRule.onNodeWithTag("source-changes-toggle").performClick()
        composeRule.waitForIdle()
        capture("source_changes_card_412x892")
        composeRule.onNode(hasClickAction() and hasAnyAncestor(hasTestTag("source-changes-open")), useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(AppScreen.SOURCE_CHANGES, state.screen)
        composeRule.onAllNodesWithText("已取消", substring = true)[0].assertIsDisplayed()
        composeRule.onNodeWithText("地点：Raum 1 → Raum 2").assertExists()
        capture("source_changes_details_412x892")
        assertTrue(state.handleBack())
        assertEquals(AppScreen.SOURCE_PREVIEW, state.screen)
    }

    /** Geänderter Termin trägt das Kennzeichen in der aufgeklappten Kursgruppe. */
    @Test fun `课次行显示变化标记`() {
        val state = state().also { it.openSourceCourseSelector() }
        render(state)
        composeRule.onAllNodesWithText("Synthetik Messtechnik")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("时间已变").assertExists()
        // Abgesagte Termine sind nicht wählbar und erscheinen daher nur auf Karte und Detailseite.
    }

    /** Ältere Version aktiv: keine Karte/Kennzeichen; Detailseite über die Einstellungen bleibt erreichbar. */
    @Test fun `旧版本时不显示卡片 设置可查看差异`() {
        val state = state()
        state.selectSourceSnapshot(SourceSnapshotSlot.PREVIOUS)
        state.openSourceCourseSelector()
        render(state)
        assertEquals(0, composeRule.onAllNodes(hasTestTag("source-changes")).fetchSemanticsNodes().size)
        state.open(AppScreen.SETTINGS)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-snapshot-changes"))
        composeRule.onNode(hasClickAction() and hasAnyAncestor(hasTestTag("settings-snapshot-changes")), useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(AppScreen.SOURCE_CHANGES, state.screen)
        assertEquals(5, state.sourceVersionChanges.changes.size)
    }

    @Test fun `无上一次读取时卡片隐藏`() {
        val state = CalendarControlState(
            initialSourceSnapshots = SavedSourceSnapshots(latest = SavedSourceSnapshot(start, latest.size, emptyMap(), latest)),
        ).also { it.openSourceCourseSelector() }
        render(state)
        assertEquals(0, composeRule.onAllNodes(hasTestTag("source-changes")).fetchSemanticsNodes().size)
        assertTrue(state.sourceChanges.isEmpty)
    }

    /** Einstiegsziele: Hin- und Rückweg über Intent; Fremdwerte ergeben kein Ziel. */
    @Test fun `入口意图可往返解析`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(AppEntry.SourceRange, AppEntryIntents.parse(AppEntryIntents.intent(context, AppEntry.SourceRange)))
        assertEquals(AppEntry.Course("uid-m"), AppEntryIntents.parse(AppEntryIntents.intent(context, AppEntry.Course("uid-m"))))
        assertNull(AppEntryIntents.parse(Intent().putExtra("com.tequila.tumcalendarcontrol.entry", "unbekannt")))
        assertNull(AppEntryIntents.parse(null))
    }
}
