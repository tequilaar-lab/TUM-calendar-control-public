/*
 * Aufgabe: Eingaben für Kurznamen prüfen: Kursbearbeitung speichert den Kurznamen getrennt von lokalen Anzeigeänderungen;
 *   die Widget-Seite listet die Kurse des Widgets „Woche“ und speichert beim Tippen; ohne geschriebene Termine ein Hinweis.
 * Ein-/Ausgabe: synthetische Kurse und geschriebene Termine -> gespeicherte Kurznamen, lokale Änderungen, Seitenbild.
 * Nebenwirkungen: Nur private Test-Präferenzen und lokale Testbilder.
 * Nicht zuständig: Darstellung im Widget (CourseShortNamesTest).
 */
package com.tequila.tumcalendarcontrol.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.domain.CourseLocalOverride
import com.tequila.tumcalendarcontrol.i18n.AppLanguage
import com.tequila.tumcalendarcontrol.i18n.AppLanguagePreferences
import com.tequila.tumcalendarcontrol.sync.DesiredCalendarEvent
import com.tequila.tumcalendarcontrol.sync.ManagedEventIdentity
import com.tequila.tumcalendarcontrol.sync.OutputTarget
import com.tequila.tumcalendarcontrol.sync.RuleLedger
import com.tequila.tumcalendarcontrol.sync.RuleLedgerPreferences
import com.tequila.tumcalendarcontrol.sync.StoredEventMapping
import com.tequila.tumcalendarcontrol.widget.CourseShortNames
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
class CourseShortNamePagesTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    /** Feste Vorschauzeit (Mittwoch 10:30 in Berlin). */
    private val fixedClock = WidgetPreviewClock({ Instant.parse("2026-10-21T08:30:00Z") }, { ZoneId.of("Europe/Berlin") })

    @Before fun chinese() = AppLanguagePreferences(context).save(AppLanguage.CHINESE)

    private fun lightState() = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT; it.lightPaletteStyle = LightPaletteStyle.WARM_WHITE }

    @Test fun `课程编辑页保存简写`() {
        val state = lightState().also { it.openCourse("course-a"); it.openCourseEdit() }
        composeRule.setContent { CalendarControlSurface(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit-short-name").performScrollTo()
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("edit-short-name"))).performTextInput("SyA")
        composeRule.onNodeWithTag("edit-save").performClick()
        composeRule.waitForIdle()
        assertEquals("SyA", CourseShortNames(context).get("course-a"))
        assertEquals("Kurzname ist keine lokale Anzeigeänderung", CourseLocalOverride(), state.courses.first { it.id == "course-a" }.localOverride)
    }

    /** Widget-Seite: Kurse der Woche aus geschriebenen Terminen; Eingabe speichert sofort. */
    @Test fun `本周组件设置里填写简写`() {
        val identity = ManagedEventIdentity("uid-syn", "1")
        val written = DesiredCalendarEvent(identity, "Synthetik Signalverarbeitung", Instant.parse("2026-10-22T08:00:00Z"), Instant.parse("2026-10-22T09:30:00Z"), AttendanceState.MUST_ATTEND, "Raum 1")
        RuleLedgerPreferences(context).save(RuleLedger(mappings = listOf(StoredEventMapping(OutputTarget.LOCAL_CALENDAR, identity, "e1", "f1", writtenEvent = written))))
        val state = lightState().also { it.open(AppScreen.WIDGETS) }
        composeRule.setContent { CompositionLocalProvider(LocalWidgetPreviewClock provides fixedClock) { CalendarControlSurface(state) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("widgets-content").performScrollToNode(hasTestTag("widgets-short-uid-syn"))
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("widgets-short-uid-syn"))).performTextInput("SVS")
        composeRule.waitForIdle()
        assertEquals("SVS", CourseShortNames(context).get("uid-syn"))
        composeRule.onRoot().captureRoboImage("src/test/screenshots/widgets_short_names_412x892.png")
    }

    @Test fun `没有写入时显示说明`() {
        val state = lightState().also { it.open(AppScreen.WIDGETS) }
        composeRule.setContent { CompositionLocalProvider(LocalWidgetPreviewClock provides fixedClock) { CalendarControlSurface(state) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("widgets-content").performScrollToNode(hasTestTag("widgets-short-empty"))
        composeRule.onNodeWithTag("widgets-short-empty").assertExists()
    }
}
