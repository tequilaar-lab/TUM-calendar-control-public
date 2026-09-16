/*
 * 职责：验证 Google 专用目标的所有权标记、创建与拒绝路径。
 * 输入／输出：输入合成 API；输出目标引用及写入次数断言。
 * 外部副作用：无；不请求 Google、OAuth 或真实网络。
 * 不负责：Android 授权界面、HTTP 传输或真人账户验证。
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.net.HttpURLConnection
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GoogleCalendarGatewayTest {
    @Test
    fun `缓存的带标记日历复用 无标记目标拒绝`() {
        val api = FakeGoogleCalendarApi("owned", "Created and owned by TUM Calendar Control. marker=tum-calendar-control")
        val resolution = GoogleCalendarTarget(api).ensure("owned", "合成日历")
        val calendar = (resolution as CalendarTargetResolution.Success).calendar
        val gateway = GoogleCalendarGateway(api)

        assertEquals("owned", calendar.calendarId)
        assertTrue(gateway.verifyOwnership(calendar))
        assertFalse(gateway.verifyOwnership(calendar.copy(calendarId = "other")))
    }

    @Test
    fun `无缓存目标创建专用日历 事件只写入已验证目标`() {
        val api = FakeGoogleCalendarApi(null, null)
        val calendar = (GoogleCalendarTarget(api).ensure("", "合成日历") as CalendarTargetResolution.Success).calendar
        val gateway = GoogleCalendarGateway(api)
        val event = DesiredCalendarEvent(ManagedEventIdentity("uid", "instance"), "合成", Instant.parse("2026-10-01T08:00:00Z"), Instant.parse("2026-10-01T09:00:00Z"), AttendanceState.MUST_ATTEND)

        gateway.create(calendar, event)

        assertEquals(1, api.createdCalendars)
        assertEquals(1, api.createdEvents)
    }

    @Test
    fun `Google 401 会成为结构化的重新授权失败码`() {
        val api = FakeGoogleCalendarApi(
            initialId = "owned",
            initialDescription = "Created and owned by TUM Calendar Control. marker=tum-calendar-control",
            failure = GoogleCalendarRequestException(statusCode = 401),
        )

        val resolution = GoogleCalendarTarget(api).ensure("owned", "合成日历") as CalendarTargetResolution.Failure

        assertEquals(TargetFailureCode.GOOGLE_AUTHORIZATION_EXPIRED, resolution.failureCode)
        assertTrue(resolution.summary.contains("HTTP 401"))
    }

    @Test
    fun `删除结果区分本次删除与此前已不存在`() {
        fun response(stage: GoogleCalendarRequestStage, status: Int) = GoogleHttpResponse(stage, status, "")

        assertEquals(ManagedEventRemovalOutcome.REMOVED_NOW, response(GoogleCalendarRequestStage.DELETE_MANAGED_EVENT, HttpURLConnection.HTTP_NO_CONTENT).toManagedEventRemovalOutcome())
        assertEquals(ManagedEventRemovalOutcome.ALREADY_ABSENT, response(GoogleCalendarRequestStage.DELETE_MANAGED_EVENT, HttpURLConnection.HTTP_NOT_FOUND).toManagedEventRemovalOutcome())
        assertEquals(ManagedEventRemovalOutcome.ALREADY_ABSENT, response(GoogleCalendarRequestStage.DELETE_MANAGED_EVENT, HttpURLConnection.HTTP_GONE).toManagedEventRemovalOutcome())
        assertTrue(runCatching { response(GoogleCalendarRequestStage.DELETE_MANAGED_EVENT, HttpURLConnection.HTTP_UNAUTHORIZED).toManagedEventRemovalOutcome() }.isFailure)
        assertTrue(runCatching { response(GoogleCalendarRequestStage.CREATE_MANAGED_EVENT, HttpURLConnection.HTTP_GONE).toManagedEventRemovalOutcome() }.isFailure)
    }

    @Test
    fun `全量应用标记页只解析标记 ID 并保留下一页令牌`() {
        val page = """{"items":[{"id":"owned","extendedProperties":{"private":{"tumEventKey":"opaque"}}},{"id":"other"}],"nextPageToken":"synthetic-next"}""".parseGoogleManagedEventPage()

        assertEquals(listOf("owned"), page.eventIds)
        assertEquals("synthetic-next", page.nextPageToken)
        assertTrue(googleManagedEventsPath("calendar/id", page.nextPageToken).contains("pageToken=synthetic-next"))
    }

    private class FakeGoogleCalendarApi(
        initialId: String?,
        initialDescription: String?,
        private val failure: Throwable? = null,
    ) : GoogleCalendarApi {
        private val calendars = initialId?.let { mutableMapOf(it to GoogleRemoteCalendar(it, initialDescription)) } ?: mutableMapOf()
        var createdCalendars = 0
        var createdEvents = 0
        override fun getCalendar(calendarId: String): GoogleRemoteCalendar? {
            failure?.let { throw it }
            return calendars[calendarId]
        }
        override fun createCalendar(summary: String, description: String): GoogleRemoteCalendar {
            createdCalendars += 1
            return GoogleRemoteCalendar("created", description).also { calendars[it.id] = it }
        }
        override fun findEventIdsByIdentity(calendarId: String, identityKey: String): List<String> = emptyList()
        override fun findAllManagedEventIds(calendarId: String): List<String> = emptyList()
        override fun createEvent(calendarId: String, event: DesiredCalendarEvent): String { check(calendars.containsKey(calendarId)); createdEvents += 1; return "event-$createdEvents" }
        override fun updateEvent(calendarId: String, eventId: String, event: DesiredCalendarEvent) = Unit
        override fun deleteEvent(calendarId: String, eventId: String) = ManagedEventRemovalOutcome.REMOVED_NOW
    }
}
