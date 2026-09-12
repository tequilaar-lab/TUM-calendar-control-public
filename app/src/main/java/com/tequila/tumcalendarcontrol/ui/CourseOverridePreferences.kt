/*
 * 职责：在应用私有偏好中保存课程名称、时间和地点的本机覆盖。
 * 输入／输出：输入合成或未来已授权课程；输出保留来源字段的课程副本。
 * 外部副作用：只写入应用沙盒 SharedPreferences。
 * 不负责：保存订阅、读取 TUM、修改原始课程或写入任何日历。
 */
package com.tequila.tumcalendarcontrol.ui

import android.content.Context
import com.tequila.tumcalendarcontrol.domain.Course
import com.tequila.tumcalendarcontrol.domain.CourseLocalOverride

class CourseOverridePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun applyTo(courses: List<Course>): List<Course> = courses.map { course ->
        course.copy(localOverride = CourseLocalOverride(
            title = preferences.getString(key(course.id, "title"), null),
            schedule = preferences.getString(key(course.id, "schedule"), null),
            location = preferences.getString(key(course.id, "location"), null),
        ))
    }

    fun save(courses: List<Course>) {
        preferences.edit().also { editor ->
            courses.forEach { course ->
                editor.putString(key(course.id, "title"), course.localOverride.title)
                editor.putString(key(course.id, "schedule"), course.localOverride.schedule)
                editor.putString(key(course.id, "location"), course.localOverride.location)
            }
        }.apply()
    }

    private fun key(courseId: String, field: String) = "course_override_${courseId}_$field"
    private companion object { const val FILE_NAME = "course_overrides" }
}
