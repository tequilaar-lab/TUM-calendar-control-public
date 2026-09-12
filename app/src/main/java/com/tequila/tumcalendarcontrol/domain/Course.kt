/*
 * 职责：定义课程总览与详情共用的离线展示模型。
 * 输入／输出：输入合成课程字段；输出稳定的课程、出席状态和收藏标记。
 * 外部副作用：无。
 * 不负责：确认真实 Fixplatz、保存用户编辑或同步任意日历。
 */
package com.tequila.tumcalendarcontrol.domain

data class Course(
    val id: String,
    val title: String,
    val schedule: String,
    val location: String,
    val attendance: AttendanceState,
    val isFavorite: Boolean,
    val hasFixedPlus: Boolean = true,
    val sourceStatus: TumEventStatus = TumEventStatus.FIX,
    val localOverride: CourseLocalOverride = CourseLocalOverride(),
)

/** 来源状态只用于显示筛选；它不能替代用户确认的 Fixplatz 输出资格。 */
enum class TumEventStatus(val label: String) {
    FIX("fix"), PLANNED("geplant"), MOVED("verschoben"), CANCELLED("abgesagt"), REJECTED("abgelehnt"), DELETED("gelöscht"),
}

/** 本机覆盖不改写来源字段，未来来源刷新可继续保留原始数据。 */
data class CourseLocalOverride(
    val title: String? = null,
    val schedule: String? = null,
    val location: String? = null,
)

val Course.displayTitle: String get() = localOverride.title ?: title
val Course.displaySchedule: String get() = localOverride.schedule ?: schedule
val Course.displayLocation: String get() = localOverride.location ?: location
