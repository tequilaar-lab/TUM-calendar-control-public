/*
 * 职责：定义规则、收藏及本机课程覆盖的单次持久化边界。
 * 输入／输出：输入下一份 RuleLedger 与课程展示资料；成功输出实际落盘的合并资料库，失败输出 null。
 * 外部副作用：接口本身无；Android 实现只写应用私有偏好。
 * 不负责：网络、日历写入、来源解析或危险操作确认。
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.domain.Course
import com.tequila.tumcalendarcontrol.sync.RuleLedger

fun interface UserDataPersistence {
    fun commit(ledger: RuleLedger, courses: List<Course>): RuleLedger?

    companion object {
        /** 纯状态测试默认使用内存，不产生 Android 存储副作用。 */
        val InMemory = UserDataPersistence { ledger, _ -> ledger }
    }
}
