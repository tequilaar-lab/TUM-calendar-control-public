/*
 * Aufgabe: Bedienung und Bildbasis der Einstellungs- und Werkzeugseiten festhalten (Einstellungen, Darstellung, Profil,
 *   Verbindung, Hintergrundprüfung, Sicherung mit Glasbestätigung, Campustermine).
 * Ein-/Ausgabe: Synthetischer Zustand -> Compose-Interaktionen, Zustandsprüfungen und PNG-Dateien.
 * Nebenwirkungen: Nur lokale Testbilder; keine Konten, Dateien, Kalender oder Netzwerke.
 * Nicht zuständig: echte Dateiauswahl, Autorisierung oder Hintergrundarbeit.
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.github.takahirom.roborazzi.captureRoboImage
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.i18n.AppLanguage
import com.tequila.tumcalendarcontrol.sync.RuleLedger
import com.tequila.tumcalendarcontrol.sync.RuleLedgerBackupCodec
import com.tequila.tumcalendarcontrol.sync.SyncRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
class SettingsPagesTest {
    @get:Rule val composeRule = createComposeRule()

    private fun state(screen: AppScreen = AppScreen.SETTINGS) = CalendarControlState().also {
        it.themeMode = ThemeMode.LIGHT
        it.lightPaletteStyle = LightPaletteStyle.WARM_WHITE
        it.open(screen)
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

    @Test fun `设置页分组列表并可进入外观`() {
        val state = state()
        render(state)
        capture("settings_redesign_412x892")
        scrollToEnd("settings-list")
        capture("settings_redesign_end_412x892")
        // Klickaktion direkt auslösen: geprüft wird die Navigation, nicht die Lage nach dem Scrollen.
        composeRule.onNode(hasClickAction() and hasAnyAncestor(hasTestTag("settings-appearance")), useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(AppScreen.APPEARANCE, state.screen)
    }

    /** Standardziel ändern erst nach Glasbestätigung; Abbrechen lässt es unverändert. */
    @Test fun `默认写入位置更改需确认`() {
        val state = state()
        render(state)
        composeRule.onNodeWithTag("settings-destination-GOOGLE_CALENDAR").performClick()
        composeRule.onNodeWithTag("confirm-title").assertIsDisplayed()
        capture("settings_destination_confirm_412x892")
        composeRule.onNodeWithTag("confirm-cancel").performClick()
        assertFalse(state.syncDestinations.googleCalendarEnabled)
    }

    @Test fun `设置页德语`() {
        render(state(), AppLanguage.GERMAN)
        composeRule.onNodeWithText("Sprache").assertExists()
        capture("settings_de_redesign_412x892")
    }

    @Test @Config(sdk = [36], qualifiers = "w840dp-h892dp-420dpi")
    fun `设置页展开屏`() {
        render(state())
        capture("settings_wide_redesign_840x892")
    }

    @Test fun `外观页配色与模式即时生效`() {
        val state = state(AppScreen.APPEARANCE)
        render(state)
        composeRule.onNodeWithTag("appearance-preview").assertExists()
        capture("appearance_redesign_412x892")
        composeRule.onNodeWithTag("palette-DEEP_BLUE").performClick()
        composeRule.onNodeWithText("深色模式").performClick()
        composeRule.waitForIdle()
        assertEquals(DarkPaletteStyle.DEEP_BLUE, state.darkPaletteStyle)
        assertEquals(ThemeMode.DARK, state.themeMode)
        capture("appearance_dark_redesign_412x892")
    }

    @Test fun `个人资料保存后返回`() {
        val state = state(AppScreen.SETTINGS).also { it.openProfile() }
        render(state)
        capture("profile_redesign_412x892")
        composeRule.onNodeWithTag("profile-tone-${AvatarTone.SEA_GREEN.name}").performClick()
        composeRule.onNodeWithTag("profile-save").performClick()
        assertEquals(AvatarTone.SEA_GREEN, state.profile.avatarTone)
    }

    @Test fun `订阅地址输入保持遮蔽`() {
        val state = state(AppScreen.SETTINGS).also { it.openSyncConfiguration() }
        render(state)
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("private-address"))).performTextInput("https://synthetic.example.test/a.ics")
        composeRule.waitForIdle()
        capture("connection_redesign_412x892")
    }

    @Test fun `后台检查`() {
        render(state(AppScreen.SETTINGS).also { it.openBackgroundCheck() })
        composeRule.onNodeWithTag("background-status").assertIsDisplayed()
        capture("background_redesign_412x892")
    }

    /** Wiederherstellen erst nach Glasbestätigung; Abbrechen verwirft die gelesene Datei. */
    @Test fun `恢复备份需确认`() {
        val state = state(AppScreen.SETTINGS).also { it.openRuleBackup() }
        state.resolveRuleBackupImport(RuleLedgerBackupCodec.encode(RuleLedger(rules = listOf(SyncRule("synthetic", false, AttendanceState.MUST_ATTEND)))))
        render(state)
        composeRule.onNodeWithTag("confirm-title").assertIsDisplayed()
        capture("backup_confirm_412x892")
        composeRule.onNodeWithTag("confirm-cancel").performClick()
        assertNull(state.pendingRuleBackup)
        assertEquals(0, state.ruleLedger.rules.size)
    }

    /** Datum fest setzen, damit das Bild nicht vom Tag abhängt; Speichern legt einen Termin an. */
    @Test fun `校园事项保存`() {
        val state = state(AppScreen.SETTINGS).also { it.openManualEvents() }
        render(state)
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("events-date"))).performTextReplacement("2026-10-26")
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("events-title"))).performTextInput("Synthetik Lerngruppe")
        composeRule.onNodeWithTag("events-attendance-MUST_ATTEND").performClick()
        capture("events_redesign_412x892")
        composeRule.onNodeWithTag("events-save").performClick()
        composeRule.waitForIdle()
        assertEquals(1, state.ruleLedger.manualEvents.size)
    }
}
