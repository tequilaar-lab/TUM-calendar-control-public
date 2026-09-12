/*
 * 职责：表示源日历中已展开的一次课程实例。
 * 输入／输出：以源 UID 与原始实例键作为稳定身份，供规则和日历写入层使用。
 * 外部副作用：无。
 * 不负责：解析 ICS、推断实例身份或按标题去重。
 */
package com.tequila.tumcalendarcontrol.domain

import java.time.LocalDateTime

data class CourseOccurrence(
    val sourceUid: String,
    val instanceKey: String,
    val title: String,
    val start: LocalDateTime,
)
