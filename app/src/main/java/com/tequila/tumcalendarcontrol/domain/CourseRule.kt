/*
 * 职责：表达一门课程的 Fixplatz 准入、默认类别、截点和单实例覆盖。
 * 输入／输出：由未来编辑界面或同步层提供，供 AttendanceResolver 解析。
 * 外部副作用：无，数据仅在内存中表示。
 * 不负责：从 TUMonline 识别 Fixplatz，也不生成重复实例。
 */
package com.tequila.tumcalendarcontrol.domain

import java.time.LocalDate

data class CourseRule(
    val sourceUid: String,
    val hasFixedPlus: Boolean,
    val defaultState: AttendanceState,
    val stopAttendingFrom: LocalDate? = null,
    val instanceOverrides: Map<String, AttendanceState> = emptyMap(),
)
