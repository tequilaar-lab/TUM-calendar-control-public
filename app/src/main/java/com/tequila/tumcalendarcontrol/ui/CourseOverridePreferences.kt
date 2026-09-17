/*
 * 职责：在应用私有偏好中保存课程收藏及名称、时间和地点的本机覆盖。
 * 输入／输出：输入合成或已读取的来源课程；输出按稳定课程 UID 恢复的本机展示元数据。
 * 外部副作用：只写入应用沙盒 SharedPreferences。
 * 不负责：保存订阅、读取 TUM、修改原始课程或写入任何日历。
 */
package com.tequila.tumcalendarcontrol.ui

import android.content.Context
import com.tequila.tumcalendarcontrol.domain.Course
import com.tequila.tumcalendarcontrol.domain.CourseLocalOverride
import com.tequila.tumcalendarcontrol.domain.CourseMeetingTime
import com.tequila.tumcalendarcontrol.domain.CourseWeekday

class CourseOverridePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun applyTo(courses: List<Course>): List<Course> = courses.map { course ->
        val presentation = presentationFor(course.id)
        course.copy(
            isFavorite = presentation.isFavorite ?: course.isFavorite,
            localOverride = presentation.localOverride,
        )
    }

    /** 只返回本机展示元数据；不会把来源标题或任何 iCal 正文带入偏好。 */
    fun loadPresentations(): Map<String, CoursePresentation> = preferences.all.keys
        .mapNotNull(::courseIdFromKey)
        .toSet()
        .associateWith(::presentationFor)

    fun save(courses: List<Course>): Boolean = preferences.edit().also { editor ->
            courses.forEach { course ->
                editor.putBoolean(key(course.id, "favorite"), course.isFavorite)
                editor.putString(key(course.id, "title"), course.localOverride.title)
                editor.putString(key(course.id, "schedule"), course.localOverride.schedule)
                editor.putString(key(course.id, "location"), course.localOverride.location)
                course.localOverride.meetingTime?.let { time ->
                    editor.putString(key(course.id, "weekday"), time.weekday.name)
                    editor.putInt(key(course.id, "start_hour"), time.startHour)
                    editor.putInt(key(course.id, "start_minute"), time.startMinute)
                    editor.putInt(key(course.id, "end_hour"), time.endHour)
                    editor.putInt(key(course.id, "end_minute"), time.endMinute)
                } ?: removeMeetingTime(editor, course.id)
            }
        }.commit()

    private fun presentationFor(courseId: String): CoursePresentation = CoursePresentation(
        isFavorite = preferences.takeIf { it.contains(key(courseId, "favorite")) }
            ?.getBoolean(key(courseId, "favorite"), false),
        localOverride = CourseLocalOverride(
            title = preferences.getString(key(courseId, "title"), null),
            schedule = preferences.getString(key(courseId, "schedule"), null),
            location = preferences.getString(key(courseId, "location"), null),
            meetingTime = meetingTime(courseId),
        ),
    )

    private fun removeMeetingTime(editor: android.content.SharedPreferences.Editor, courseId: String) {
        listOf("weekday", "start_hour", "start_minute", "end_hour", "end_minute").forEach { field ->
            editor.remove(key(courseId, field))
        }
    }

    private fun courseIdFromKey(preferenceKey: String): String? {
        val prefix = "course_override_"
        val suffix = FIELDS.firstOrNull { preferenceKey.endsWith("_$it") } ?: return null
        return preferenceKey.removePrefix(prefix).removeSuffix("_$suffix").takeIf { preferenceKey.startsWith(prefix) && it.isNotBlank() }
    }

    private fun key(courseId: String, field: String) = "course_override_${courseId}_$field"
    private fun meetingTime(courseId: String): CourseMeetingTime? = runCatching {
        val weekday = preferences.getString(key(courseId, "weekday"), null) ?: return null
        CourseMeetingTime(CourseWeekday.valueOf(weekday), preferences.getInt(key(courseId, "start_hour"), -1), preferences.getInt(key(courseId, "start_minute"), -1), preferences.getInt(key(courseId, "end_hour"), -1), preferences.getInt(key(courseId, "end_minute"), -1))
    }.getOrNull()
    private companion object {
        const val FILE_NAME = "course_overrides"
        val FIELDS = setOf("favorite", "title", "schedule", "location", "weekday", "start_hour", "start_minute", "end_hour", "end_minute")
    }
}

/** 仅保存用户本机展示选择；`null` 表示沿用课程默认收藏值。 */
data class CoursePresentation(
    val isFavorite: Boolean? = null,
    val localOverride: CourseLocalOverride = CourseLocalOverride(),
)
