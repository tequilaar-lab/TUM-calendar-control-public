/*
 * 职责：把一门课程的本地规则解析为某次实例的可见出席状态。
 * 输入／输出：输入 CourseRule 与同源 CourseOccurrence；无导入资格时输出 null，否则输出三类状态。
 * 外部副作用：无，适合 JVM 单元测试与未来同步预览。
 * 不负责：读取 iCal、展开 RRULE、持久化规则或写入 Google／荣耀日历。
 */
package com.tequila.tumcalendarcontrol.domain

object AttendanceResolver {
    /** 只有确认 Fixplatz 且 UID 一致的实例，才可进入下游日历。 */
    fun resolve(rule: CourseRule, occurrence: CourseOccurrence): AttendanceState? {
        require(rule.sourceUid == occurrence.sourceUid) {
            "课程规则与实例必须共享同一源 UID。"
        }
        if (!rule.hasFixedPlus) return null

        // 单实例人工选择优先，允许未来恢复某次例外出席。
        rule.instanceOverrides[occurrence.instanceKey]?.let { return it }

        // 截点只向未来生效；此前实例保留原先的绿／蓝分类。
        if (rule.stopAttendingFrom != null && !occurrence.start.toLocalDate().isBefore(rule.stopAttendingFrom)) {
            return AttendanceState.STOP_ATTENDING
        }
        return rule.defaultState
    }
}
