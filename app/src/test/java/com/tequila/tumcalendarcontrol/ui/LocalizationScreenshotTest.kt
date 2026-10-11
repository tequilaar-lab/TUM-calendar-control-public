/*
 * Aufgabe: echten Sprachwechsel und lokalisierte Compose-Ansichten synthetisch prüfen.
 * Ein-/Ausgabe: drei Sprachen und feste Größen -> UI-Assertions und lokale PNG-Bilder.
 * Nebenwirkungen: nur ausdrückliche Roborazzi-Aufnahme; keine Geräte, Konten oder Kalender.
 * Nicht zuständig: reale Kalenderausgabe oder Veröffentlichung eines APK.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.AppLanguage
import com.tequila.tumcalendarcontrol.i18n.AppLanguagePreferences

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.tequila.tumcalendarcontrol.domain.SampleCourses
import com.tequila.tumcalendarcontrol.domain.CourseLocalOverride
import com.tequila.tumcalendarcontrol.domain.CourseMeetingTime
import com.tequila.tumcalendarcontrol.domain.CourseWeekday
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocalizationScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private fun render(language: AppLanguage, state: CalendarControlState) {
        val selected = mutableStateOf(language)
        composeRule.setContent {
            CompositionLocalProvider(LocalAppLanguage provides LanguageSelection(selected.value) { selected.value = it }) {
                CalendarControlSurface(state)
            }
        }
        composeRule.waitForIdle()
    }
    private fun capture(name: String) { composeRule.onRoot().captureRoboImage("src/test/screenshots/localization_$name.png") }

    @Test @Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
    fun switchChineseGermanEnglishWithoutRebuildingCourseState() {
        val state = CalendarControlState().also { it.open(AppScreen.SETTINGS) }
        render(AppLanguage.CHINESE, state)
        composeRule.onNodeWithText("界面语言").assertExists()
        composeRule.onNodeWithText("Deutsch").performClick()
        composeRule.onNodeWithText("Sprache").assertExists()
        composeRule.onNode(hasText("Kurse lesen") and hasClickAction()).assertExists()
        composeRule.onNode(hasText("Einstellungen") and hasClickAction()).assertExists()
        capture("settings_de_412x892")
        composeRule.onNodeWithText("English").performClick()
        composeRule.onNodeWithText("Language").assertExists()
        composeRule.onNode(hasText("Read courses") and hasClickAction()).assertExists()
        capture("settings_en_412x892")
        composeRule.onNodeWithText("中文").performClick()
        composeRule.onNode(hasText("读取课程") and hasClickAction()).assertExists()
        capture("settings_zh_412x892")
    }

    @Test @Config(sdk = [36], qualifiers = "w840dp-h892dp-420dpi")
    fun germanWideSettings() {
        render(AppLanguage.GERMAN, CalendarControlState().also { it.open(AppScreen.SETTINGS) })
        composeRule.onNodeWithText("Sprache").assertExists()
        capture("settings_de_840x892")
    }

    @Test @Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
    fun germanOverviewKeepsChineseUserCourseName() {
        val userCourse = SampleCourses.all().first().copy(title = "用户课程：取消", location = "用户教室")
        render(AppLanguage.GERMAN, CalendarControlState(initialCourses = listOf(userCourse)))
        composeRule.onNodeWithText("用户课程：取消").assertExists()
        composeRule.onNodeWithText("Übersicht").assertExists()
        capture("overview_de_412x892")
    }

    @Test @Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
    fun germanSourceRangeLongText() {
        render(AppLanguage.GERMAN, CalendarControlState().also { it.openSourceRange() })
        composeRule.onAllNodes(hasText("Lesezeitraum"))[0].assertExists()
        capture("source_range_de_412x892")
    }

    @Test @Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
    fun germanDetailsAndAttendanceLocalizeNestedWeekdayAndCancelButton() {
        val course = SampleCourses.all().first().copy(localOverride = CourseLocalOverride(
            meetingTime = CourseMeetingTime(CourseWeekday.MONDAY, 8, 5, 10, 0)))
        val state = CalendarControlState(initialCourses = listOf(course)).also { it.openCourse(course.id) }
        render(AppLanguage.GERMAN, state)
        composeRule.onNodeWithText("Montag 08:05–10:00", substring = true).assertExists()
        composeRule.onNodeWithText("Fixplatz", substring = true).assertExists()
        capture("details_de_412x892")
        composeRule.runOnIdle { state.openAttendance() }
        composeRule.onNode(hasText("Abbrechen") and hasClickAction()).assertExists()
        capture("attendance_de_412x892")
    }
}
