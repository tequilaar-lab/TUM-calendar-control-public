/*
 * Aufgabe: Bedienung von Suche, Favoriten, Kursdetails, lokaler Bearbeitung und Teilnahmeauswahl im neuen Erscheinungsbild prüfen.
 * Ein-/Ausgabe: Synthetischer CalendarControlState -> Compose-Interaktionen, Zustandsprüfungen und PNG-Dateien.
 * Nebenwirkungen: Nur lokale Testbilder.
 * Nicht zuständig: Kursdaten-Lesen, Regelauflösung oder Kalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.tequila.tumcalendarcontrol.domain.AttendanceState
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
class CourseBrowsePagesTest {
    @get:Rule val composeRule = createComposeRule()

    private fun render(state: CalendarControlState) {
        composeRule.setContent { CalendarControlSurface(state) }
        composeRule.waitForIdle()
    }

    private fun lightState() = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT; it.lightPaletteStyle = LightPaletteStyle.WARM_WHITE }

    /** Suchbegriff filtert nur die Anzeige; der gewählte Kurs bleibt unverändert. */
    @Test fun searchFiltersWithoutChangingSelection() {
        val state = lightState().also { it.open(AppScreen.SEARCH) }
        val selectedBefore = state.selectedCourse.id
        render(state)
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("search-field"))).performTextInput("课程 C")
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithText("课程 A").fetchSemanticsNodes().size)
        composeRule.onNode(hasText("课程 C") and hasAnyAncestor(hasTestTag("course-browser-list"))).assertIsDisplayed()
        assertEquals(selectedBefore, state.selectedCourse.id)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/search_query_412x892.png")
    }

    /** Favoriten zeigen nur markierte Kurse mit Fixplatz. */
    @Test fun favoritesListOnlyFavoriteCourses() {
        val state = lightState().also { it.open(AppScreen.FAVORITES) }
        render(state)
        composeRule.onNodeWithText("课程 B").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("课程 A").fetchSemanticsNodes().size)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/favorites_412x892.png")
    }

    /** Details: Favoritenhäkchen schaltet nur die Markierung; die schwebende Aktion öffnet die Teilnahme. */
    @Test fun detailTogglesFavoriteAndOpensAttendance() {
        val state = lightState().also { it.openCourse("course-a") }
        render(state)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/detail_redesign_412x892.png")
        composeRule.onNodeWithTag("detail-favorite").performClick()
        composeRule.waitForIdle()
        assertTrue(state.selectedCourse.isFavorite)
        composeRule.onNodeWithTag("detail-attendance").performClick()
        composeRule.waitForIdle()
        assertEquals(AppScreen.ATTENDANCE, state.contentScreen)
    }

    /** Bearbeiten: ungültige Zeit wird gemeldet, nichts gespeichert, Seite bleibt offen. */
    @Test fun courseEditRejectsInvalidTime() {
        val state = lightState().also { it.openCourse("course-a"); it.openCourseEdit() }
        render(state)
        val startHour = composeRule.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("edit-content")))[2]
        startHour.performTextClearance()
        startHour.performTextInput("25")
        composeRule.onNodeWithTag("edit-save").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit-validation").performScrollTo().assertIsDisplayed()
        assertEquals(AppScreen.COURSE_EDIT, state.contentScreen)
        assertEquals(null, state.selectedCourse.localOverride.meetingTime)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/course_edit_invalid_412x892.png")
    }

    /** Teilnahme: Auswahl ändert nur den Entwurf; Abbrechen kehrt ohne Übernahme zu den Details zurück. */
    @Test fun attendanceCancelKeepsState() {
        val state = lightState().also { it.openCourse("course-a"); it.openAttendance() }
        render(state)
        composeRule.onNodeWithTag("attendance-STOP_ATTENDING").performClick()
        assertEquals(AttendanceState.STOP_ATTENDING, state.pendingAttendance)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/attendance_redesign_412x892.png")
        composeRule.onNodeWithTag("attendance-cancel").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(AppScreen.DETAIL, state.contentScreen)
        assertEquals(AttendanceState.MUST_ATTEND, state.selectedCourse.attendance)
    }
}
