/*
 * 职责：保存离线原型的屏幕栈、主题、选课、搜索、收藏及待确认出席状态。
 * 输入／输出：接收用户的本地点击；输出 Compose 可观察状态和更新后的合成课程列表。
 * 外部副作用：无，进程结束后所有变更会消失。
 * 不负责：数据库、日期选择器、真实日历写入或网络同步。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.domain.Course
import com.tequila.tumcalendarcontrol.domain.SampleCourses

enum class AppScreen {
    OVERVIEW, DETAIL, ATTENDANCE, SEARCH, FAVORITES, MORE, SETTINGS,
}

enum class ThemeMode(val label: String) {
    LIGHT("浅色模式"),
    DARK("深色模式"),
    SYSTEM("跟随系统"),
}

enum class NavigationDirection {
    FORWARD, BACKWARD,
}

class CalendarControlState(initialCourses: List<Course> = SampleCourses.all()) {
    var courses by mutableStateOf(initialCourses)
        private set
    var screen by mutableStateOf(AppScreen.OVERVIEW)
        private set
    var selectedCourseId by mutableStateOf(initialCourses.first().id)
        private set
    var searchQuery by mutableStateOf("")
    var isMenuOpen by mutableStateOf(false)
    var pendingAttendance by mutableStateOf(initialCourses.first().attendance)
    var feedback by mutableStateOf<String?>(null)
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    var navigationDirection by mutableStateOf(NavigationDirection.FORWARD)
        private set
    private var detailReturnScreen by mutableStateOf(AppScreen.OVERVIEW)
    private var moreReturnScreen by mutableStateOf(AppScreen.OVERVIEW)

    val selectedCourse: Course
        get() = courses.first { it.id == selectedCourseId }

    val searchedCourses: List<Course>
        get() = courses.filter { it.hasFixedPlus && it.title.contains(searchQuery, ignoreCase = true) }

    val favoriteCourses: List<Course>
        get() = courses.filter { it.isFavorite && it.hasFixedPlus }

    /** 主页面之间的切换会成为后续详情、更多操作的返回来源。 */
    fun open(screen: AppScreen) {
        isMenuOpen = false
        navigationDirection = NavigationDirection.FORWARD
        this.screen = screen
    }

    fun openCourse(courseId: String) {
        detailReturnScreen = screen
        selectedCourseId = courseId
        pendingAttendance = selectedCourse.attendance
        open(AppScreen.DETAIL)
    }

    fun openAttendance() {
        pendingAttendance = selectedCourse.attendance
        open(AppScreen.ATTENDANCE)
    }

    fun openMore() {
        moreReturnScreen = screen
        open(AppScreen.MORE)
    }

    fun confirmAttendance() {
        courses = courses.map { course ->
            if (course.id == selectedCourseId) course.copy(attendance = pendingAttendance) else course
        }
        feedback = "已在本地原型中更新“${selectedCourse.title}”的状态"
        open(AppScreen.DETAIL)
    }

    fun toggleFavorite(courseId: String) {
        courses = courses.map { course ->
            if (course.id == courseId) course.copy(isFavorite = !course.isFavorite) else course
        }
    }

    fun requestSync() {
        feedback = "尚未连接 TUM 或 Google 日历；本轮不会写入任何真实日程。"
    }

    /** 处理应用内层级返回；只有主页面才交还给系统执行退出。 */
    fun handleBack(): Boolean {
        if (isMenuOpen) {
            isMenuOpen = false
            return true
        }
        val destination = when (screen) {
            AppScreen.ATTENDANCE -> AppScreen.DETAIL
            AppScreen.DETAIL -> detailReturnScreen
            AppScreen.MORE -> moreReturnScreen
            else -> return false
        }
        navigationDirection = NavigationDirection.BACKWARD
        screen = destination
        return true
    }

    fun canHandleBack(): Boolean = isMenuOpen || screen in setOf(
        AppScreen.DETAIL,
        AppScreen.ATTENDANCE,
        AppScreen.MORE,
    )
}
