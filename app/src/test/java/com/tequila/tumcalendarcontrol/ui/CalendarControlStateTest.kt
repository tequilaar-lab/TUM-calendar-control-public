/*
 * 职责：验证离线 UI 状态机的课程选择、主题、层级返回、收藏及同步目标开关。
 * 输入／输出：输入合成课程与本地操作；输出内存状态变化断言。
 * 外部副作用：无。
 * 不负责：Compose 视觉渲染、持久化、网络或真实日历验证。
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarControlStateTest {
    @Test
    fun `确认状态会更新所选课程且回到详情`() {
        val state = CalendarControlState()

        state.openCourse("course-b")
        state.openAttendance()
        state.pendingAttendance = AttendanceState.MUST_ATTEND
        state.confirmAttendance()

        assertEquals(AppScreen.DETAIL, state.screen)
        assertEquals(AttendanceState.MUST_ATTEND, state.selectedCourse.attendance)
    }

    @Test
    fun `收藏切换只影响对应课程`() {
        val state = CalendarControlState()

        state.toggleFavorite("course-a")

        assertTrue(state.courses.first { it.id == "course-a" }.isFavorite)
        assertFalse(state.courses.first { it.id == "course-c" }.isFavorite)
    }

    @Test
    fun `更多操作会返回进入前的主页面`() {
        val state = CalendarControlState()

        state.open(AppScreen.FAVORITES)
        state.openMore()

        assertEquals(AppScreen.MORE, state.screen)
        assertTrue(state.handleBack())
        assertEquals(AppScreen.FAVORITES, state.screen)
        assertEquals(NavigationDirection.BACKWARD, state.navigationDirection)
    }

    @Test
    fun `主标签页按相对位置使用相反方向动效`() {
        val state = CalendarControlState()

        state.open(AppScreen.SETTINGS)
        assertEquals(NavigationDirection.FORWARD, state.navigationDirection)
        state.open(AppScreen.FAVORITES)

        assertEquals(NavigationDirection.BACKWARD, state.navigationDirection)
    }

    @Test
    fun `更多操作打开时保留原页面作为稳定背景`() {
        val state = CalendarControlState()

        state.open(AppScreen.SEARCH)
        state.openMore()

        assertTrue(state.isMoreOpen)
        assertEquals(AppScreen.SEARCH, state.contentScreen)
        assertTrue(state.handleBack())
        assertFalse(state.isMoreOpen)
        assertEquals(AppScreen.SEARCH, state.contentScreen)
    }

    @Test
    fun `状态页和详情页会按层级返回而非直接退出`() {
        val state = CalendarControlState()

        state.open(AppScreen.SEARCH)
        state.openCourse("course-a")
        state.openAttendance()

        assertTrue(state.handleBack())
        assertEquals(AppScreen.DETAIL, state.screen)
        assertTrue(state.handleBack())
        assertEquals(AppScreen.SEARCH, state.screen)
        assertFalse(state.canHandleBack())
    }

    @Test
    fun `主题模式可在本次运行内切换`() {
        val state = CalendarControlState()

        state.themeMode = ThemeMode.DARK

        assertEquals(ThemeMode.DARK, state.themeMode)
    }

    @Test
    fun `个人资料保存后返回原来的主页面`() {
        val state = CalendarControlState(initialProfile = UserProfile("初始", AvatarTone.SEA_GREEN))

        state.open(AppScreen.FAVORITES)
        state.openProfile()
        state.updateProfile(UserProfile("新昵称", AvatarTone.LAVENDER))

        assertTrue(state.handleBack())
        assertEquals(AppScreen.FAVORITES, state.screen)
        assertEquals("新昵称", state.profile.displayName)
        assertEquals(AvatarTone.LAVENDER, state.profile.avatarTone)
    }

    @Test
    fun `同步目标开关彼此独立且默认关闭`() {
        val state = CalendarControlState()

        assertFalse(state.syncDestinations.localCalendarEnabled)
        assertFalse(state.syncDestinations.googleCalendarEnabled)

        state.setSyncDestinationEnabled(SyncDestination.LOCAL_CALENDAR, true)

        assertTrue(state.syncDestinations.localCalendarEnabled)
        assertFalse(state.syncDestinations.googleCalendarEnabled)

        state.setSyncDestinationEnabled(SyncDestination.GOOGLE_CALENDAR, true)

        assertTrue(state.syncDestinations.localCalendarEnabled)
        assertTrue(state.syncDestinations.googleCalendarEnabled)
    }
}
