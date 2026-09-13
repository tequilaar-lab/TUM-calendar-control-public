/*
 * 职责：验证离线 UI 状态机的课程选择、主题、层级返回、收藏及同步目标开关。
 * 输入／输出：输入合成课程与本地操作；输出内存状态变化断言。
 * 外部副作用：无。
 * 不负责：Compose 视觉渲染、持久化、网络或真实日历验证。
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.domain.CourseLocalOverride
import com.tequila.tumcalendarcontrol.domain.TumEventStatus
import com.tequila.tumcalendarcontrol.sync.PrivateSyncConfiguration
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

    @Test
    fun `保存同步配置后返回设置且不隐式打开输出`() {
        val state = CalendarControlState()

        state.open(AppScreen.SETTINGS)
        state.openSyncConfiguration()
        state.updatePrivateSyncConfiguration(
            PrivateSyncConfiguration(tumSubscriptionUrl = "https://calendar.example.test/subscription"),
        )

        assertTrue(state.handleBack())
        assertEquals(AppScreen.SETTINGS, state.screen)
        assertFalse(state.syncDestinations.localCalendarEnabled)
        assertFalse(state.syncDestinations.googleCalendarEnabled)
    }

    @Test
    fun `未配置来源时不发起只读读取`() {
        val state = CalendarControlState()

        state.requestSourcePreview()

        assertFalse(state.sourceReadRequested)
        assertFalse(state.sourceReadInProgress)
        assertTrue(state.feedback!!.contains("未发起网络请求"))
    }

    @Test
    fun `本机课程覆盖不改写来源字段 且来源筛选不改变Fixplatz资格`() {
        val state = CalendarControlState()
        state.openCourse("course-b")

        state.updateSelectedCourseOverride(CourseLocalOverride(title = "本机名称"))
        state.toggleSourceStatusVisible(TumEventStatus.PLANNED)

        assertEquals("课程 B", state.selectedCourse.title)
        assertEquals("本机名称", state.selectedCourse.localOverride.title)
        assertFalse(state.searchedCourses.any { it.id == "course-b" })
        assertTrue(state.selectedCourse.hasFixedPlus)
    }

    @Test
    fun `本机日历权限拒绝保持关闭 同意只允许未来输出`() {
        val state = CalendarControlState()

        state.requestLocalCalendarOutput()
        assertTrue(state.localCalendarPermissionRequestPending)
        state.resolveLocalCalendarPermission(false)
        assertFalse(state.syncDestinations.localCalendarEnabled)

        state.requestLocalCalendarOutput()
        state.resolveLocalCalendarPermission(true)
        assertTrue(state.syncDestinations.localCalendarEnabled)
        assertTrue(state.feedback!!.contains("不会创建或写入"))
    }

    @Test
    fun `四种范围都可写入规则资料库 且指定日期格式无效不会确认`() {
        val state = CalendarControlState()
        AttendanceEditScope.entries.forEach { scope ->
            state.openCourse("course-a")
            state.openAttendance()
            state.pendingAttendanceScope = scope
            state.pendingEffectiveDate = if (scope == AttendanceEditScope.SPECIFIED_AND_FUTURE) "2026-10-10" else ""
            state.confirmAttendance()
            assertTrue(state.ruleLedger.rules.any { it.sourceUid == "course-a" })
        }
        state.openAttendance()
        state.pendingAttendanceScope = AttendanceEditScope.SPECIFIED_AND_FUTURE
        state.pendingEffectiveDate = "无效日期"
        state.confirmAttendance()
        assertEquals(AppScreen.ATTENDANCE, state.screen)
        assertEquals("请输入 YYYY-MM-DD 格式的指定日期。", state.feedback)
    }
}
