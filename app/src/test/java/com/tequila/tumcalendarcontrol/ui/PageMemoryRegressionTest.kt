/*
 * Aufgabe: synthetische Regressionstests für PageMemoryRegression.
 * Ein-/Ausgabe: künstliche Zustände und Fixtures -> JUnit-Prüfergebnisse.
 * Nebenwirkungen: nur lokaler Testprozess und gegebenenfalls Robolectric-Präferenzen.
 * Nicht zuständig: echte Konten, private Quellen, Geräte oder reale Kalenderausgaben.
 */
package com.tequila.tumcalendarcontrol.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.sync.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PageMemoryRegressionTest {
    private val time = Instant.parse("2026-10-01T08:00:00Z")
    private val items = listOf("a", "b").map { SourceCalendarOccurrence(it, "one", "Synthetic $it", time, time.plusSeconds(3600), false) }
    private fun state() = CalendarControlState(initialSourceSnapshots = SavedSourceSnapshots(latest = SavedSourceSnapshot(time, 2, emptyMap(), items)))

    @Test fun reentryRestoresActualValuesModeScopeAndDoesNotRewrite() {
        val state = state()
        state.openSourceAttendanceEditor(items.map { it.identity }.toSet())
        state.individualAttendanceMode = true
        state.pendingSourceAttendanceScope = AttendanceEditScope.SPECIFIED_AND_FUTURE
        state.pendingSourceEffectiveDate = "2026-10-01"
        state.individualAttendanceDrafts = state.individualAttendanceGroups.associate { group -> group.key to
            IndividualAttendanceDraft(if (group.title.endsWith("a")) AttendanceState.MUST_ATTEND else AttendanceState.STOP_ATTENDING, SourceFavoriteAction.ADD) }
        state.confirmSourceAttendance()
        val saved = state.ruleLedger
        state.openSourceAttendanceEditor()
        assertTrue(state.individualAttendanceMode)
        assertEquals(AttendanceEditScope.SPECIFIED_AND_FUTURE, state.pendingSourceAttendanceScope)
        assertEquals("2026-10-01", state.pendingSourceEffectiveDate)
        assertEquals(setOf(AttendanceState.MUST_ATTEND, AttendanceState.STOP_ATTENDING), state.individualAttendanceDrafts.values.map { it.attendance }.toSet())
        assertTrue(state.individualAttendanceDrafts.values.all { it.favorite == SourceFavoriteAction.ADD })
        state.confirmSourceAttendance()
        assertEquals(saved, state.ruleLedger)
    }

    @Test fun favoriteOnlyDoesNotFlattenMixedAttendance() {
        val state = state()
        state.openSourceAttendanceEditor(setOf(items.first().identity))
        state.pendingSourceAttendance = AttendanceState.MUST_ATTEND
        state.confirmSourceAttendance()
        val before = state.ruleLedger
        state.openSourceAttendanceEditor(items.map { it.identity }.toSet())
        state.pendingSourceFavoriteAction = SourceFavoriteAction.ADD
        state.confirmSourceAttendance()
        assertEquals(before, state.ruleLedger)
        assertTrue(state.courses.all { it.isFavorite })
    }

    @Test fun memoryRoundTripRetainsScopeAndCleanupConditionsWithoutActions() {
        val preferences = PageMemoryPreferences(ApplicationProvider.getApplicationContext<Context>())
        val memory = PageMemory(individual = true, scope = AttendanceEditScope.CURRENT_AND_FUTURE,
            selection = setOf(items.first().identity, SourceOccurrenceIdentity("absent", "one")), cleanupMode = "COURSES",
            cleanupCourses = setOf("a"), cleanupTargets = setOf("LOCAL_CALENDAR"), cleanupStart = "2026-10-01",
            visibleSourceStatuses = setOf("UNKNOWN", "CANCELLED"))
        preferences.save(memory)
        assertEquals(memory, preferences.load())
        val state = state()
        state.restorePageMemory(preferences.load())
        assertEquals(setOf(items.first().identity), state.selectedSourceOccurrences)
        state.openSourceAttendanceEditor()
        assertTrue(state.individualAttendanceMode)
        assertEquals(AttendanceEditScope.CURRENT_AND_FUTURE, state.pendingSourceAttendanceScope)
        assertEquals(setOf(com.tequila.tumcalendarcontrol.domain.TumEventStatus.UNKNOWN, com.tequila.tumcalendarcontrol.domain.TumEventStatus.CANCELLED), state.visibleSourceStatuses)
        assertFalse(state.managedSyncRequested)
        assertFalse(state.managedRollbackRequested)
    }

    @Test fun cancelDoesNotReplaceSavedRangeOrRules() {
        val state = state()
        state.restorePageMemory(PageMemory(scope = AttendanceEditScope.ONLY_THIS_OCCURRENCE))
        state.openSourceAttendanceEditor(setOf(items.first().identity))
        state.pendingSourceAttendanceScope = AttendanceEditScope.ALL_OCCURRENCES
        state.pendingSourceAttendance = AttendanceState.STOP_ATTENDING
        state.handleBack()
        state.openSourceAttendanceEditor()
        assertEquals(AttendanceEditScope.ONLY_THIS_OCCURRENCE, state.pendingSourceAttendanceScope)
        assertTrue(state.ruleLedger.rules.isEmpty())
    }

    @Test fun detailReentryWithoutChangesDoesNotCreateWholeCourseRule() {
        val state = state()
        state.openCourse("a")
        state.openAttendance()
        val before = state.ruleLedger
        state.confirmAttendance()
        assertEquals(before, state.ruleLedger)
    }
}
