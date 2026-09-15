/*
 * 职责：通过 Google Calendar REST API 定位／创建应用专用次级日历，并受映射约束增改删事件。
 * 输入／输出：输入短生命周期访问令牌、目标标识和期望事件；输出专用日历引用或事件 ID。
 * 外部副作用：仅在用户明确执行同步且已授权时，通过 HTTPS 调用 Google Calendar API。
 * 不负责：启动 OAuth、保存令牌、访问主日历、遍历用户其他日历或解析来源 iCal。
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZoneId
import javax.net.ssl.HttpsURLConnection

private const val GOOGLE_API_BASE = "https://www.googleapis.com/calendar/v3"
private const val GOOGLE_OWNERSHIP_DESCRIPTION = "Created and owned by TUM Calendar Control. marker=tum-calendar-control"
private const val GOOGLE_EVENT_IDENTITY_KEY = "tumEventKey"

data class GoogleRemoteCalendar(val id: String, val description: String?)

interface GoogleCalendarApi {
    fun getCalendar(calendarId: String): GoogleRemoteCalendar?
    fun createCalendar(summary: String, description: String): GoogleRemoteCalendar
    fun makeCalendarVisible(calendarId: String)
    fun findEventIdsByIdentity(calendarId: String, identityKey: String): List<String>
    fun createEvent(calendarId: String, event: DesiredCalendarEvent): String
    fun updateEvent(calendarId: String, eventId: String, event: DesiredCalendarEvent)
    fun deleteEvent(calendarId: String, eventId: String)
}

/** 目标 ID 只接受先前创建且带固定标记的次级日历；失效时才创建新的专用日历。 */
class GoogleCalendarTarget(private val api: GoogleCalendarApi) {
    fun ensure(existingCalendarId: String, calendarName: String): CalendarTargetResolution = runCatching {
        val existing = existingCalendarId.takeIf(String::isNotBlank)?.let(api::getCalendar)
        val calendar = if (existing?.description == GOOGLE_OWNERSHIP_DESCRIPTION) existing else api.createCalendar(calendarName, GOOGLE_OWNERSHIP_DESCRIPTION)
        // Google 新建次级日历可能默认未在侧栏选中；显式请求可见。
        api.makeCalendarVisible(calendar.id)
        CalendarTargetResolution.Success(OwnedCalendarRef(OutputTarget.GOOGLE_CALENDAR, calendar.id, AppOwnedCalendarContract.OWNERSHIP_MARKER))
    }.getOrElse { error -> CalendarTargetResolution.Failure(error.googleSafeSummary("Google 专用日历不可用")) }
}

/** 每次事件操作前验证专用日历的固定所有权标记，拒绝主日历和未标记日历。 */
class GoogleCalendarGateway(private val api: GoogleCalendarApi) : OwnedCalendarGateway {
    override fun verifyOwnership(calendar: OwnedCalendarRef): Boolean = calendar.target == OutputTarget.GOOGLE_CALENDAR &&
        calendar.ownershipMarker == AppOwnedCalendarContract.OWNERSHIP_MARKER &&
        api.getCalendar(calendar.calendarId)?.description == GOOGLE_OWNERSHIP_DESCRIPTION

    override fun findEventIdsByIdentity(calendar: OwnedCalendarRef, identity: ManagedEventIdentity): List<String> {
        check(verifyOwnership(calendar)) { "拒绝读取非应用拥有的 Google 日历。" }
        return api.findEventIdsByIdentity(calendar.calendarId, identity.opaqueKey())
    }

    override fun create(calendar: OwnedCalendarRef, event: DesiredCalendarEvent): String {
        check(verifyOwnership(calendar)) { "拒绝向非应用拥有的 Google 日历写入。" }
        return api.createEvent(calendar.calendarId, event)
    }

    override fun update(calendar: OwnedCalendarRef, eventId: String, event: DesiredCalendarEvent) {
        check(verifyOwnership(calendar)) { "拒绝向非应用拥有的 Google 日历更新。" }
        api.updateEvent(calendar.calendarId, eventId, event)
    }

    override fun remove(calendar: OwnedCalendarRef, eventId: String) {
        check(verifyOwnership(calendar)) { "拒绝从非应用拥有的 Google 日历删除。" }
        api.deleteEvent(calendar.calendarId, eventId)
    }
}

/** 短令牌仅由构造参数接收并留在进程内；错误摘要不包含 URL、响应体、账户或令牌。 */
class GoogleCalendarRestApi(
    private val accessToken: String,
    private val transport: GoogleCalendarHttpTransport = GoogleCalendarHttpTransport(),
) : GoogleCalendarApi {
    override fun getCalendar(calendarId: String): GoogleRemoteCalendar? {
        val response = transport.execute("GET", "/calendars/${calendarId.encodePath()}", null, accessToken)
        if (response.statusCode == HttpURLConnection.HTTP_NOT_FOUND) return null
        response.requireSuccess()
        val json = JSONObject(response.body)
        return GoogleRemoteCalendar(json.getString("id"), json.optString("description").takeIf(String::isNotBlank))
    }

    override fun createCalendar(summary: String, description: String): GoogleRemoteCalendar {
        val response = transport.execute("POST", "/calendars", JSONObject().put("summary", summary).put("description", description).toString(), accessToken)
        response.requireSuccess()
        val json = JSONObject(response.body)
        return GoogleRemoteCalendar(json.getString("id"), json.optString("description").takeIf(String::isNotBlank))
    }

    override fun makeCalendarVisible(calendarId: String) {
        transport.execute(
            "PATCH",
            "/users/me/calendarList/${calendarId.encodePath()}",
            JSONObject().put("selected", true).toString(),
            accessToken,
        ).requireSuccess()
    }

    override fun findEventIdsByIdentity(calendarId: String, identityKey: String): List<String> {
        val encoded = java.net.URLEncoder.encode("$GOOGLE_EVENT_IDENTITY_KEY=$identityKey", Charsets.UTF_8.name())
        val response = transport.execute("GET", "/calendars/${calendarId.encodePath()}/events?singleEvents=true&privateExtendedProperty=$encoded", null, accessToken)
        response.requireSuccess()
        return JSONObject(response.body).optJSONArray("items")?.let { items ->
            buildList { for (index in 0 until items.length()) add(items.getJSONObject(index).getString("id")) }
        }.orEmpty()
    }

    override fun createEvent(calendarId: String, event: DesiredCalendarEvent): String {
        val response = transport.execute("POST", "/calendars/${calendarId.encodePath()}/events?sendUpdates=none", event.jsonBody(), accessToken)
        response.requireSuccess()
        return JSONObject(response.body).getString("id")
    }

    override fun updateEvent(calendarId: String, eventId: String, event: DesiredCalendarEvent) {
        transport.execute("PUT", "/calendars/${calendarId.encodePath()}/events/${eventId.encodePath()}?sendUpdates=none", event.jsonBody(), accessToken).requireSuccess()
    }

    override fun deleteEvent(calendarId: String, eventId: String) {
        transport.execute("DELETE", "/calendars/${calendarId.encodePath()}/events/${eventId.encodePath()}?sendUpdates=none", null, accessToken).requireSuccess()
    }

    private fun DesiredCalendarEvent.jsonBody(): String = JSONObject().apply {
        put("summary", title)
        put("location", location)
        put("description", AppOwnedCalendarContract.eventDescription())
        put("extendedProperties", JSONObject().put("private", JSONObject().put(GOOGLE_EVENT_IDENTITY_KEY, identity.opaqueKey())))
        put("colorId", attendanceState.googleColorId())
        put("start", JSONObject().put("dateTime", startsAt.toString()).put("timeZone", ZoneId.of("Europe/Berlin").id))
        put("end", JSONObject().put("dateTime", endsAt.toString()).put("timeZone", ZoneId.of("Europe/Berlin").id))
    }.toString()

    private fun AttendanceState.googleColorId(): String = when (this) {
        AttendanceState.MUST_ATTEND -> "10"
        AttendanceState.CHOOSE_TO_ATTEND -> "9"
        AttendanceState.STOP_ATTENDING -> "8"
    }

    private fun String.encodePath(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
}

data class GoogleHttpResponse(val statusCode: Int, val body: String) {
    fun requireSuccess(): GoogleHttpResponse {
        if (statusCode !in 200..299) throw GoogleCalendarRequestException(statusCode)
        return this
    }
}

/** 只携带状态码，避免 Google 响应正文、账户或令牌进入界面和日志。 */
class GoogleCalendarRequestException(val statusCode: Int) : IllegalStateException("Google Calendar HTTP $statusCode")

internal fun Throwable.googleSafeSummary(prefix: String): String =
    if (this is GoogleCalendarRequestException) "$prefix（HTTP $statusCode；请检查授权、Calendar API 与网络）" else "$prefix；请检查授权和网络后重试"

/** 最小 HTTPS 传输实现；限制响应大小，且不将请求、令牌或响应原文写入日志。 */
class GoogleCalendarHttpTransport(private val maxResponseBytes: Int = 512 * 1024) {
    fun execute(method: String, path: String, body: String?, accessToken: String): GoogleHttpResponse {
        require(path.startsWith('/')) { "Google API 路径必须以 / 开头。" }
        val connection = (URL(GOOGLE_API_BASE + path).openConnection() as HttpsURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        return try {
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes().also { data -> require(data.size <= maxResponseBytes) { "Google 响应超过大小限制" } } } ?: ByteArray(0)
            GoogleHttpResponse(connection.responseCode, bytes.toString(Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }
}
