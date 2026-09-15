/*
 * 职责：验证每个稳定 AppScreen 枚举值都在应用内容路由中登记。
 * 输入／输出：输入编译期 AppScreen 枚举与路由清单；输出合成单元测试断言结果。
 * 外部副作用：无；不渲染真实页面、不读取 iCal、不连接账户或日历。
 * 不负责：验证具体页面布局、导航栈或同步业务；这些由既有状态／截图测试覆盖。
 */
package com.tequila.tumcalendarcontrol.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppScreenRouterTest {
    @Test
    fun `every stable screen has an explicit content route`() {
        assertEquals(AppScreen.entries.toSet(), routedAppScreens)
    }
}
