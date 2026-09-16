/*
 * 职责：验证子类型别名、缓存继续与按课程设置的安全边界。
 * 输入／输出：合成快照与偏好，输出确定性断言。
 * 外部副作用：仅 Robolectric 沙盒偏好，不访问设备、网络或日历。
 * 不负责：真机布局与日历端验收。
 */
package com.tequila.tumcalendarcontrol.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tequila.tumcalendarcontrol.domain.*
import com.tequila.tumcalendarcontrol.sync.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CourseEditingRegressionTest {
    private val time = Instant.parse("2026-10-01T08:00:00Z")
    private fun item(uid: String, title: String, key: String = "one") = SourceCalendarOccurrence(uid, key, title, time, time.plusSeconds(3600), false, "room")
    private fun state(vararg items: SourceCalendarOccurrence) = CalendarControlState(initialSourceSnapshots = SavedSourceSnapshots(latest = SavedSourceSnapshot(time, items.size, emptyMap(), items.toList())))
    private fun course(title: String) = Course(title, title, "", "", AttendanceState.CHOOSE_TO_ATTEND, false)

    @Test fun recognizeTypesWithoutOverridingExisting() {
        mapOf("Synthetic VI, Standardgruppe" to "VI", "Synthetic (VI)" to "VI", "VI Synthetic" to "VI", "Praktikum Synthetic" to "PR", "projektpraktikum Synthetic" to "PR", "Praktikum Synthetic UE" to "UE", "Civil" to "其他课次", "Praktikumsbericht" to "其他课次").forEach { (title, expected) ->
            assertEquals(title, expected, CourseDisplayGroupModel.groups(listOf(course(title))).single().teachingTypes.single().label)
        }
    }
    @Test fun aliasesPersistAndNeverMergeGroupsOrChangeCourses() {
        val courses = listOf(course("Synthetic VO"), course("Synthetic UE"))
        val original = CourseDisplayGroupModel.groups(courses).single()
        val aliases = original.teachingTypes.associate { it.key to "自定义" }
        val context = ApplicationProvider.getApplicationContext<Context>()
        CourseTypeNamesPreferences(context).save(aliases)
        val changed = CourseDisplayGroupModel.groups(courses, CourseTypeNamesPreferences(context).load()).single()
        assertEquals(2, changed.teachingTypes.size)
        assertEquals(original.courses.toSet(), changed.courses.toSet())
        assertEquals(original.teachingTypes.map { it.key }.toSet(), changed.teachingTypes.map { it.key }.toSet())
        assertTrue(changed.teachingTypes.all { it.label == "自定义" })
        CourseTypeNamesPreferences(context).save(emptyMap())
        assertEquals(original, CourseDisplayGroupModel.groups(courses, CourseTypeNamesPreferences(context).load()).single())
        assertFalse(validCourseTypeName("  "))
        assertFalse(validCourseTypeName("a\nb"))
        assertFalse(validCourseTypeName("x".repeat(31)))
    }
    @Test fun cachedNextSavesRangeWithoutReading() {
        val state = state(item("a", "Synthetic VO"))
        state.openSourceRange()
        val before = state.sourcePreview
        assertTrue(state.continueWithSavedCourses("2026-10-01", "2026-10-31").isEmpty())
        assertEquals(AppScreen.SOURCE_PREVIEW, state.screen)
        assertSame(before, state.sourcePreview)
        assertFalse(state.sourceReadRequested)
        assertEquals("2026-10-01", state.privateSyncConfiguration.sourceRangeStartDate)
    }
    @Test fun cachedNextRejectsInvalidRangeAndMissingCache() {
        val state = state(item("a", "Synthetic VO"))
        state.openSourceRange()
        assertTrue(state.continueWithSavedCourses("invalid", "").isNotEmpty())
        assertEquals(AppScreen.SOURCE_RANGE, state.screen)
        assertTrue(CalendarControlState().continueWithSavedCourses("", "").isNotEmpty())
    }
    @Test fun individualRulesAffectOnlySelectedInstancesAndSelectedFavorites() {
        val a = item("a", "Synthetic A VO")
        val b = item("b", "Synthetic B VO")
        val state = state(a, a.copy(instanceKey = "two"), b)
        state.openSourceAttendanceEditor(setOf(a.identity, b.identity))
        state.individualAttendanceMode = true
        state.pendingSourceAttendanceScope = AttendanceEditScope.ONLY_THIS_OCCURRENCE
        state.individualAttendanceDrafts = state.individualAttendanceGroups.associate { group -> group.key to if (group.title.contains("A")) IndividualAttendanceDraft(AttendanceState.MUST_ATTEND, SourceFavoriteAction.ADD) else IndividualAttendanceDraft() }
        state.confirmSourceAttendance()
        val rule = state.ruleLedger.rules.single { it.sourceUid == "a" }
        assertEquals(setOf("one"), rule.instanceOverrides.keys)
        assertTrue(state.courses.filter { it.id == "a" }.all { it.isFavorite })
        assertFalse(state.courses.single { it.id == "b" }.isFavorite)
        assertFalse(state.managedSyncRequested)
    }
    @Test fun invalidIndividualDateLeavesAllRulesAndFavoritesUnchanged() {
        val a = item("a", "Synthetic A")
        val state = state(a)
        state.openSourceAttendanceEditor(setOf(a.identity))
        state.individualAttendanceMode = true
        state.pendingSourceAttendanceScope = AttendanceEditScope.SPECIFIED_AND_FUTURE
        state.pendingSourceEffectiveDate = "invalid"
        state.individualAttendanceDrafts = mapOf(state.individualAttendanceGroups.single().key to IndividualAttendanceDraft(AttendanceState.MUST_ATTEND, SourceFavoriteAction.ADD))
        val before = state.ruleLedger
        state.confirmSourceAttendance()
        assertEquals(before, state.ruleLedger)
        assertFalse(state.courses.single().isFavorite)
    }

    @Test fun separateCoursesReceiveDifferentStates() {
        val a = item("a", "Synthetic A")
        val b = item("b", "Synthetic B")
        val untouched = item("c", "Synthetic C")
        val state = state(a, b, untouched)
        state.openSourceAttendanceEditor(setOf(a.identity, b.identity))
        state.individualAttendanceMode = true
        state.individualAttendanceDrafts = state.individualAttendanceGroups.associate { group ->
            group.key to IndividualAttendanceDraft(if (group.title.endsWith("A")) AttendanceState.MUST_ATTEND else AttendanceState.CHOOSE_TO_ATTEND, SourceFavoriteAction.ADD)
        }
        state.confirmSourceAttendance()
        assertEquals(AttendanceState.MUST_ATTEND, state.sourceAttendanceFor("a"))
        assertEquals(AttendanceState.CHOOSE_TO_ATTEND, state.sourceAttendanceFor("b"))
        assertFalse(state.courses.single { it.id == "c" }.isFavorite)
        assertTrue(state.courses.filter { it.id != "c" }.all { it.isFavorite })
    }

    @Test fun cancellingIndividualDraftDoesNotSave() {
        val a = item("a", "Synthetic A")
        val state = state(a)
        state.openSourceAttendanceEditor(setOf(a.identity))
        state.individualAttendanceMode = true
        state.individualAttendanceDrafts = mapOf(state.individualAttendanceGroups.single().key to IndividualAttendanceDraft(AttendanceState.MUST_ATTEND, SourceFavoriteAction.ADD))
        state.handleBack()
        assertTrue(state.ruleLedger.rules.isEmpty())
        assertFalse(state.courses.single().isFavorite)
        state.openSourceAttendanceEditor(setOf(a.identity))
        assertTrue(state.individualAttendanceDrafts.isEmpty())
    }
}
