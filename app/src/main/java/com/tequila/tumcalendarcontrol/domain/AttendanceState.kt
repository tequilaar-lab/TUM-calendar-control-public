/*
 * 职责：定义用户可见的三类出席颜色与文案。
 * 输入／输出：规则解析与界面消费此枚举；输出为稳定的业务状态。
 * 外部副作用：无。
 * 不负责：判断 Fixplatz、日期截点或写入日历。
 */
package com.tequila.tumcalendarcontrol.domain

enum class AttendanceState(val label: String) {
    MUST_ATTEND("必去（绿）"),
    CHOOSE_TO_ATTEND("选择出席（蓝）"),
    STOP_ATTENDING("以后不去（灰）"),
}
