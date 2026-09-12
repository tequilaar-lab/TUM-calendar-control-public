/*
 * 职责：提供仅用于离线 UI 原型的合成 Fixplatz 课程。
 * 输入／输出：无输入；输出五门没有真实来源、账户或标识信息的合成课程。
 * 外部副作用：无。
 * 不负责：模拟 TUM 数据、推断 Fixplatz 或作为任何同步输入。
 */
package com.tequila.tumcalendarcontrol.domain

object SampleCourses {
    fun all(): List<Course> = listOf(
        Course("course-a", "课程 A", "周二 10:00–12:00", "示例校区", AttendanceState.MUST_ATTEND, false),
        Course("course-b", "课程 B", "周三 14:00–16:00", "示例校区", AttendanceState.CHOOSE_TO_ATTEND, true),
        Course("course-c", "课程 C", "周四 08:00–10:00", "示例校区", AttendanceState.STOP_ATTENDING, false),
        Course("course-d", "课程 D", "周四 16:00–18:00", "示例校区", AttendanceState.CHOOSE_TO_ATTEND, false),
        Course("course-e", "课程 E", "周五 10:00–12:00", "示例校区", AttendanceState.MUST_ATTEND, false),
    )
}
