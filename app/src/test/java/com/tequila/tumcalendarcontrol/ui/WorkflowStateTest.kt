/*
 * 职责：验证拆分后的导航、来源读取与受控输出状态模块。
 * 输入／输出：输入合成意图、令牌和失败回调；断言运行内状态及返回目标。
 * 外部副作用：无；不使用真实订阅、账户、权限、网络或日历。
 * 不负责：Compose 渲染、iCal 解析、OAuth 适配或真实同步验证。
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.sync.IcalReadOnlyImportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowStateTest {
    @Test
    fun `导航模块保留更多操作的稳定背景并可返回`() {
        val navigation = NavigationState()

        navigation.open(AppScreen.FAVORITES)
        navigation.openMore()

        assertEquals(AppScreen.FAVORITES, navigation.contentScreen)
        assertTrue(navigation.handleBack())
        assertEquals(AppScreen.FAVORITES, navigation.screen)
        assertEquals(NavigationDirection.BACKWARD, navigation.direction)
    }

    @Test
    fun `过期来源读取失败不会结束当前请求`() {
        val workflow = SourceReadWorkflowState()
        val request = workflow.requestDirectRead(hasSubscription = true) as SourceReadRequest.Requested

        assertEquals(SourceReadResolution.Ignored, workflow.resolveRead(request.token + 1, IcalReadOnlyImportResult.Failure("合成旧结果")))
        assertTrue(workflow.readInProgress)
        assertEquals(SourceReadResolution.Failure("合成超时"), workflow.resolveRead(request.token, IcalReadOnlyImportResult.Failure("合成超时")))
        assertFalse(workflow.readInProgress)
    }

    @Test
    fun `输出模块忽略过期完成并在当前令牌完成后复位`() {
        val workflow = ManagedOutputWorkflowState()
        val token = requireNotNull(workflow.beginSync())

        assertFalse(workflow.finishOperation(token + 1))
        assertTrue(workflow.inProgress)
        assertTrue(workflow.finishOperation(token))
        assertFalse(workflow.inProgress)
        assertFalse(workflow.syncRequested)
    }
}
