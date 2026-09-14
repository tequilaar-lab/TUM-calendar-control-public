/*
 * 职责：验证目标日历可见描述不泄露同步身份。
 * 输入／输出：输入合成 UID 与实例键；输出通用应用归属描述断言。
 * 外部副作用：无；不访问 Android Provider、网络、账户或真实日历。
 * 不负责：日历创建、事件写入、映射持久化或来源解析。
 */
package com.tequila.tumcalendarcontrol.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOwnedCalendarContractTest {
    @Test
    fun `可见事件描述不包含来源UID或实例键`() {
        val syntheticUid = "synthetic-uid@example.test"
        val syntheticInstanceKey = "2026-10-13T14:45:00Z"

        val description = AppOwnedCalendarContract.eventDescription()

        assertTrue(description.contains("TUM 日历控制"))
        assertFalse(description.contains(syntheticUid))
        assertFalse(description.contains(syntheticInstanceKey))
    }
}
