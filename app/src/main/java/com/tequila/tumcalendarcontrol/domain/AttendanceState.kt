/*
 * 职责：定义用户可见的三类出席颜色与文案。
 * 输入／输出：规则解析与界面消费此枚举；输出为稳定的业务状态。
 * 外部副作用：无。
 * 不负责：判断 Fixplatz、日期截点或写入日历。
 */
package com.tequila.tumcalendarcontrol.domain

enum class AttendanceState(
    val label: String,
    val colorLabel: String,
    val description: String,
) {
    MUST_ATTEND("一定出席", "绿", "需要优先出席"),
    CHOOSE_TO_ATTEND("选择出席", "蓝", "可根据实际情况决定是否出席"),
    STOP_ATTENDING("从此不去", "灰", "从指定实例起不再出席；过去不变"),
}
