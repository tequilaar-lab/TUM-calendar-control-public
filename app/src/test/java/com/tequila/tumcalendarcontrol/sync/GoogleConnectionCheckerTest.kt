/*
 * 职责：验证 Google 只读安全连接诊断的所有权、缺失与脱敏失败结果。
 * 输入／输出：输入合成专用日历或异常；输出可供界面显示的诊断分类。
 * 外部副作用：无；不访问网络、OAuth、账户、日历或事件。
 * 不负责：Android 授权、HTTP 传输、创建目标日历或执行同步。
 */
package com.tequila.tumcalendarcontrol.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleConnectionCheckerTest {
    @Test
    fun `没有保存的目标时不调用 API`() {
        val api = DiagnosticGoogleApi(calendar = GoogleRemoteCalendar("synthetic", GOOGLE_OWNERSHIP_DESCRIPTION))

        assertEquals(GoogleConnectionCheckResult.NoManagedCalendar, GoogleConnectionChecker(api).check(""))
        assertEquals(0, api.readRequests)
    }

    @Test
    fun `带完整应用标记的目标可用`() {
        val result = GoogleConnectionChecker(
            DiagnosticGoogleApi(calendar = GoogleRemoteCalendar("synthetic", GOOGLE_OWNERSHIP_DESCRIPTION)),
        ).check("saved-target")

        assertEquals(GoogleConnectionCheckResult.Available, result)
    }

    @Test
    fun `目标缺失和非应用标记会被安全区分`() {
        assertEquals(GoogleConnectionCheckResult.Missing, GoogleConnectionChecker(DiagnosticGoogleApi(calendar = null)).check("saved-target"))
        assertEquals(
            GoogleConnectionCheckResult.OwnershipUnverified,
            GoogleConnectionChecker(DiagnosticGoogleApi(calendar = GoogleRemoteCalendar("synthetic", "other marker"))).check("saved-target"),
        )
    }

    @Test
    fun `授权失败只返回阶段状态与标准原因`() {
        val result = GoogleConnectionChecker(
            DiagnosticGoogleApi(
                failure = GoogleCalendarRequestException(
                    stage = GoogleCalendarRequestStage.CHECK_OWNED_CALENDAR,
                    statusCode = 401,
                    authorizationSignal = GoogleAuthorizationSignal.INVALID_TOKEN,
                ),
            ),
        ).check("saved-target") as GoogleConnectionCheckResult.Failed

        assertTrue(result.summary.contains("检查应用专用日历"))
        assertTrue(result.summary.contains("HTTP 401"))
        assertTrue(result.summary.contains("令牌无效"))
    }

    private class DiagnosticGoogleApi(
        private val calendar: GoogleRemoteCalendar? = null,
        private val failure: Throwable? = null,
    ) : GoogleCalendarApi {
        var readRequests = 0
        override fun getCalendar(calendarId: String): GoogleRemoteCalendar? {
            readRequests += 1
            failure?.let { throw it }
            return calendar
        }

        override fun createCalendar(summary: String, description: String): GoogleRemoteCalendar = error("not used")
        override fun findEventIdsByIdentity(calendarId: String, identityKey: String): List<String> = error("not used")
        override fun findAllManagedEventIds(calendarId: String): List<String> = error("not used")
        override fun createEvent(calendarId: String, event: DesiredCalendarEvent): String = error("not used")
        override fun updateEvent(calendarId: String, eventId: String, event: DesiredCalendarEvent) = error("not used")
        override fun deleteEvent(calendarId: String, eventId: String) = error("not used")
    }
}
