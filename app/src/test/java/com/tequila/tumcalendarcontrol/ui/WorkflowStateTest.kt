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
    fun `子页面始终从右侧进入且返回向右侧退出`() {
        val navigation = NavigationState()

        navigation.open(AppScreen.SETTINGS)
        navigation.openSyncConfiguration()

        assertEquals(NavigationDirection.FORWARD, navigation.direction)
        assertTrue(navigation.handleBack())
        assertEquals(AppScreen.SETTINGS, navigation.screen)
        assertEquals(NavigationDirection.BACKWARD, navigation.direction)
    }

    @Test
    fun `设置工作流的普通子页面入口不会继承主标签的旧方向`() {
        val navigation = NavigationState()
        navigation.open(AppScreen.SETTINGS)
        val entries = listOf(
            AppScreen.SYNC_CONFIGURATION to navigation::openSyncConfiguration,
            AppScreen.SYNC_PREVIEW to navigation::openSyncPreview,
            AppScreen.SOURCE_RANGE to navigation::openSourceRange,
            AppScreen.MANUAL_EVENTS to navigation::openManualEvents,
            AppScreen.MANAGED_SYNC_PREVIEW to navigation::openManagedSyncPreview,
            AppScreen.OUTPUT_CLEANUP to navigation::openOutputCleanup,
            AppScreen.BACKGROUND_CHECK to navigation::openBackgroundCheck,
            AppScreen.RULE_BACKUP to navigation::openRuleBackup,
        )

        entries.forEach { (screen, open) ->
            open()
            assertEquals(screen, navigation.screen)
            assertEquals(NavigationDirection.FORWARD, navigation.direction)
            assertTrue(navigation.handleBack())
            assertEquals(AppScreen.SETTINGS, navigation.screen)
            assertEquals(NavigationDirection.BACKWARD, navigation.direction)
        }
    }

    @Test
    fun `个人资料从左进入 并向左退出回到进入前页面`() {
        listOf(AppScreen.OVERVIEW, AppScreen.SEARCH, AppScreen.FAVORITES, AppScreen.SETTINGS).forEach { origin ->
            val navigation = NavigationState()

            navigation.open(origin)
            navigation.openProfile()
            assertEquals(AppScreen.PROFILE, navigation.screen)
            assertEquals(NavigationDirection.PROFILE_ENTER, navigation.direction)

            assertTrue(navigation.handleBack())
            assertEquals(origin, navigation.screen)
            assertEquals(NavigationDirection.PROFILE_EXIT, navigation.direction)
        }
    }

    @Test
    fun `课程详情和状态层级始终正向进入反向返回`() {
        val navigation = NavigationState()

        navigation.openDetail()
        assertEquals(AppScreen.DETAIL, navigation.screen)
        assertEquals(NavigationDirection.FORWARD, navigation.direction)
        navigation.openCourseEdit()
        assertEquals(AppScreen.COURSE_EDIT, navigation.screen)
        assertEquals(NavigationDirection.FORWARD, navigation.direction)
        assertTrue(navigation.handleBack())
        assertEquals(AppScreen.DETAIL, navigation.screen)
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

    @Test
    fun `Google 只读检查要求本次授权并忽略断开后的旧结果`() {
        val workflow = ManagedOutputWorkflowState()

        assertEquals("请先连接 Google 日历，再执行只读连接检查。", workflow.requestGoogleConnectionCheck())
        workflow.resolveGoogleAuthorization(GoogleAuthorizationOutcome.AccessToken("synthetic-token"))
        assertEquals(null, workflow.requestGoogleConnectionCheck())
        val token = workflow.googleConnectionCheckToken
        assertTrue(workflow.googleConnectionCheckInProgress)
        assertEquals(GoogleConnectionVerification.CHECKING, workflow.googleConnectionVerification)

        workflow.disconnectGoogle()

        assertFalse(workflow.resolveGoogleConnectionCheck(token, com.tequila.tumcalendarcontrol.sync.GoogleConnectionCheckResult.Missing))
        assertFalse(workflow.googleConnectionCheckInProgress)
        assertEquals(GoogleConnectionVerification.NOT_CHECKED, workflow.googleConnectionVerification)
        assertTrue(workflow.googleConnectionCheckDetail.contains("不会创建日历"))
    }
}
