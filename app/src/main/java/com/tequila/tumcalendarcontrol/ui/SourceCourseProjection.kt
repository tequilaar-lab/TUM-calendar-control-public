/*
 * 职责：把运行内 iCal 来源目录投影为首页、搜索、收藏与详情共用的界面课程。
 * 输入／输出：输入来源 UID 目录、来源实例、三态规则及旧界面课程；输出按 UID 稳定合并后的课程列表。
 * 外部副作用：无；不读取网络、不持久化来源正文、不申请权限也不写入任何日历。
 * 不负责：来源解析、Fixplatz 判断、规则编辑、日历差异计算或同步执行。
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.domain.Course
import com.tequila.tumcalendarcontrol.domain.CourseWeekday
import com.tequila.tumcalendarcontrol.domain.TumEventStatus
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceCourse
import com.tequila.tumcalendarcontrol.sync.SyncRule
import com.tequila.tumcalendarcontrol.sync.SyncRuleResolver
import java.time.DayOfWeek
import java.time.ZoneId

/** 来源标题只是展示资料；只有 sourceUid 能把本机收藏与覆盖安全带到下一次读取。 */
internal object SourceCourseProjection {
    private val sourceZone: ZoneId = ZoneId.of("Europe/Berlin")

    fun courses(
        sourceCourses: List<SourceCourse>,
        occurrences: List<SourceCalendarOccurrence>,
        rules: List<SyncRule>,
        previousCourses: List<Course>,
        savedPresentations: Map<String, CoursePresentation> = emptyMap(),
    ): List<Course> {
        val previousByUid = previousCourses.associateBy(Course::id)
        val rulesByUid = rules.associateBy(SyncRule::sourceUid)
        return occurrences.sortedBy { it.startsAt }.map { occurrence ->
            val source = sourceCourses.first { it.sourceUid == occurrence.sourceUid }
            val previous = previousByUid[source.sourceUid]
            val presentation = previous?.let { CoursePresentation(it.isFavorite, it.localOverride) }
                ?: savedPresentations[source.sourceUid]
                ?: CoursePresentation()
            Course(
                id = source.sourceUid,
                title = occurrence.title,
                schedule = occurrence.toDisplaySchedule(),
                location = occurrence.location,
                attendance = occurrence.let { item ->
                    rulesByUid[source.sourceUid]?.let { SyncRuleResolver.resolve(it, item, sourceZone) }
                } ?: previous?.attendance ?: AttendanceState.CHOOSE_TO_ATTEND,
                isFavorite = presentation.isFavorite ?: false,
                // iCal 本身没有可靠的 TUM 报名状态字段；课程显示不得因旧 Fixplatz 筛选而丢失。
                hasFixedPlus = true,
                sourceStatus = if (occurrence.isCancelled) TumEventStatus.CANCELLED else TumEventStatus.FIX,
                localOverride = presentation.localOverride,
                instanceKey = occurrence.instanceKey,
                startsAt = occurrence.startsAt,
                endsAt = occurrence.endsAt,
            )
        }
    }

    private fun SourceCalendarOccurrence.toDisplaySchedule(): String {
        val start = startsAt.atZone(sourceZone)
        val end = endsAt.atZone(sourceZone)
        val timeFormat = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val endDate = if (start.toLocalDate() == end.toLocalDate()) "" else "${end.toLocalDate()} "
        return "${start.toLocalDate()} ${start.dayOfWeek.toCourseWeekday().label} ${start.format(timeFormat)}–$endDate${end.format(timeFormat)}"
    }

    private fun DayOfWeek.toCourseWeekday(): CourseWeekday = when (this) {
        DayOfWeek.MONDAY -> CourseWeekday.MONDAY
        DayOfWeek.TUESDAY -> CourseWeekday.TUESDAY
        DayOfWeek.WEDNESDAY -> CourseWeekday.WEDNESDAY
        DayOfWeek.THURSDAY -> CourseWeekday.THURSDAY
        DayOfWeek.FRIDAY -> CourseWeekday.FRIDAY
        DayOfWeek.SATURDAY -> CourseWeekday.SATURDAY
        DayOfWeek.SUNDAY -> CourseWeekday.SUNDAY
    }
}
