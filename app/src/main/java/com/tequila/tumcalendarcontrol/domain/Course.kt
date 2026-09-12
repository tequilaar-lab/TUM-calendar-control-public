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
)
