/*
 * 职责：提供界面演示用的合成课程与解析结果。
 * 输入／输出：无外部输入；输出为只含合成标题、UID 和日期的展示模型。
 * 外部副作用：无，不读取真实日历或账户。
 * 不负责：持久化样例、用户编辑或同步。
 */
package com.tequila.tumcalendarcontrol.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class PreviewEvent(
    val title: String,
    val dateTime: LocalDateTime,
    val state: AttendanceState,
)

object SampleSchedule {
    fun events(): List<PreviewEvent> {
        val rule = CourseRule(
            sourceUid = "sample-fixed-course",
            hasFixedPlus = true,
            defaultState = AttendanceState.CHOOSE_TO_ATTEND,
            stopAttendingFrom = LocalDate.of(2026, 10, 19),
            instanceOverrides = mapOf("2026-10-12T10:00" to AttendanceState.MUST_ATTEND),
        )
        val instances = listOf(
            occurrence("2026-10-05T10:00"),
            occurrence("2026-10-12T10:00"),
            occurrence("2026-10-19T10:00"),
        )
        return instances.map { occurrence ->
            PreviewEvent(
                title = "合成课程：规则演示",
                dateTime = occurrence.start,
                state = checkNotNull(AttendanceResolver.resolve(rule, occurrence)),
            )
        }
    }

    private fun occurrence(instanceKey: String): CourseOccurrence = CourseOccurrence(
        sourceUid = "sample-fixed-course",
        instanceKey = instanceKey,
        title = "合成课程：规则演示",
        start = LocalDateTime.parse(instanceKey),
    )
}
