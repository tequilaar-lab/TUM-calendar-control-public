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
    /** 浏览行独立于课程 UID；单次操作必须携带来源原始实例键。 */
    val instanceKey: String? = null,
    val startsAt: java.time.Instant? = null,
    val endsAt: java.time.Instant? = null,
)

val Course.browseKey: String get() = "$id\u0000${instanceKey.orEmpty()}"

/** 来源状态只用于显示筛选；它不能替代用户确认的 Fixplatz 输出资格。 */
enum class TumEventStatus(val label: String) {
    FIX("fix"), PLANNED("geplant"), MOVED("verschoben"), CANCELLED("abgesagt"), REJECTED("abgelehnt"), DELETED("gelöscht"),
}

/** 本机覆盖不改写来源字段，未来来源刷新可继续保留原始数据。 */
data class CourseLocalOverride(
    val title: String? = null,
    val schedule: String? = null,
    val location: String? = null,
    val meetingTime: CourseMeetingTime? = null,
)

/** 结构化时间避免用户输入冒号；未来日历写入必须消费该值而非展示文本。 */
data class CourseMeetingTime(
    val weekday: CourseWeekday,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
) {
    init {
        require(startHour in 0..23 && endHour in 0..23) { "小时必须在 0 至 23。" }
        require(startMinute in 0..59 && endMinute in 0..59) { "分钟必须在 0 至 59。" }
        require(endHour * 60 + endMinute > startHour * 60 + startMinute) { "结束时间必须晚于开始时间。" }
    }
    fun display(): String = "${weekday.label} ${startHour.twoDigits()}:${startMinute.twoDigits()}–${endHour.twoDigits()}:${endMinute.twoDigits()}"
}

enum class CourseWeekday(val label: String) { MONDAY("周一"), TUESDAY("周二"), WEDNESDAY("周三"), THURSDAY("周四"), FRIDAY("周五"), SATURDAY("周六"), SUNDAY("周日") }
private fun Int.twoDigits(): String = toString().padStart(2, '0')

val Course.displayTitle: String get() = localOverride.title ?: title
val Course.displaySchedule: String get() {
    val meeting = localOverride.meetingTime ?: return localOverride.schedule ?: schedule
    val originalDate = startsAt?.atZone(java.time.ZoneId.of("Europe/Berlin"))?.toLocalDate() ?: return meeting.display()
    val editedDate = originalDate.plusDays((meeting.weekday.ordinal + 1 - originalDate.dayOfWeek.value).toLong())
    return "$editedDate ${meeting.display()}"
}
val Course.displayLocation: String get() = localOverride.location ?: location
