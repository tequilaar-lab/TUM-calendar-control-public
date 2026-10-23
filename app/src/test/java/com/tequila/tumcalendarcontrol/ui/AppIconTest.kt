/*
 * Aufgabe: Wahl des Startsymbols prüfen: Standard, Wechsel genau eines aktiven Alias und Auswahl über die Darstellungsseite.
 * Ein-/Ausgabe: Robolectric-PackageManager und synthetischer Zustand -> Aktivierungszustände, UI-Prüfungen, PNG-Dateien.
 * Nebenwirkungen: Nur Testprozess und lokale Testbilder.
 * Nicht zuständig: Verhalten echter Startbildschirme (nur auf dem Gerät prüfbar).
 */
package com.tequila.tumcalendarcontrol.ui

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
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
class AppIconTest {
    @get:Rule val composeRule = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun enabledAliases(): List<AppIconStyle> = AppIconStyle.entries.filter { style ->
        val setting = context.packageManager.getComponentEnabledSetting(ComponentName(context.packageName, "com.tequila.tumcalendarcontrol" + style.alias))
        setting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (setting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && style == AppIconStyle.GLASS)
    }

    @Test fun `默认为玻璃蓝且切换后只有一个启动入口`() {
        assertEquals(AppIconStyle.GLASS, AppIconSwitcher.current(context))
        AppIconSwitcher.select(context, AppIconStyle.NIGHT)
        assertEquals(AppIconStyle.NIGHT, AppIconSwitcher.current(context))
        assertEquals(listOf(AppIconStyle.NIGHT), enabledAliases())
        AppIconSwitcher.select(context, AppIconStyle.GLASS)
        assertEquals(listOf(AppIconStyle.GLASS), enabledAliases())
    }

    /** Antippen allein ändert nichts; erst die Bestätigung wechselt das Symbol. */
    @Test fun `外观页选择图标需确认`() {
        val state = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT; it.lightPaletteStyle = LightPaletteStyle.WARM_WHITE; it.open(AppScreen.APPEARANCE) }
        composeRule.setContent { CalendarControlSurface(state) }
        composeRule.onNodeWithTag("appearance-content").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/app_icons_412x892.png")
        composeRule.onNodeWithTag("app-icon-ORBIT").performClick()
        composeRule.onNodeWithTag("confirm-title").assertIsDisplayed()
        assertEquals(AppIconStyle.GLASS, AppIconSwitcher.current(context))
        composeRule.onRoot().captureRoboImage("src/test/screenshots/app_icon_confirm_412x892.png")
        composeRule.onNodeWithTag("confirm-accept").performClick()
        composeRule.waitForIdle()
        assertEquals(AppIconStyle.ORBIT, AppIconSwitcher.current(context))
    }
}
