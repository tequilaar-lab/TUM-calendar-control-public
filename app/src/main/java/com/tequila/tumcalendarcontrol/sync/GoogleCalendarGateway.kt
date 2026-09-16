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
internal const val GOOGLE_OWNERSHIP_DESCRIPTION = "Created and owned by TUM Calendar Control. marker=tum-calendar-control"
private const val GOOGLE_EVENT_IDENTITY_KEY = "tumEventKey"

data class GoogleRemoteCalendar(val id: String, val description: String?)

/**
 * 职责：为 Google Calendar 请求提供不含 URL、日历 ID 或令牌的稳定阶段标签。
 * 输入／输出：网络适配器写入阶段；界面只显示中文阶段说明。
 * 外部副作用：无。
 * 不负责：决定是否发起请求、保存诊断或读取响应正文。
 */
enum class GoogleCalendarRequestStage(val label: String) {
    CHECK_OWNED_CALENDAR("检查应用专用日历"),
    CREATE_OWNED_CALENDAR("创建应用专用日历"),
    FIND_MANAGED_EVENTS("核对应用已写入的日程"),
    FIND_ALL_MANAGED_EVENTS("核对全部应用已写入的日程"),
    CREATE_MANAGED_EVENT("写入新的应用日程"),
    UPDATE_MANAGED_EVENT("更新应用已写入的日程"),
    DELETE_MANAGED_EVENT("删除应用已写入的日程"),
}

/** 只从标准 WWW-Authenticate 头提取固定错误关键字；不读取或保存响应正文。 */
enum class GoogleAuthorizationSignal(val label: String) {
    INVALID_TOKEN("令牌无效"),
    INSUFFICIENT_SCOPE("权限范围不足"),
    INVALID_REQUEST("授权请求无效"),
    UNSPECIFIED("Google 未提供更具体的授权原因"),
}

interface GoogleCalendarApi {
    fun getCalendar(calendarId: String): GoogleRemoteCalendar?
    fun createCalendar(summary: String, description: String): GoogleRemoteCalendar
    fun findEventIdsByIdentity(calendarId: String, identityKey: String): List<String>
    fun findAllManagedEventIds(calendarId: String): List<String>
    fun createEvent(calendarId: String, event: DesiredCalendarEvent): String
    fun updateEvent(calendarId: String, eventId: String, event: DesiredCalendarEvent)
    fun deleteEvent(calendarId: String, eventId: String): ManagedEventRemovalOutcome
    fun readEventFingerprint(calendarId: String, eventId: String, identity: ManagedEventIdentity): String? = null
    fun readManagedEvent(calendarId: String, eventId: String, identity: ManagedEventIdentity): DesiredCalendarEvent? = null
}

/** 目标 ID 只接受先前创建且带固定标记的次级日历；失效时才创建新的专用日历，不修改侧栏显示设置。 */
class GoogleCalendarTarget(private val api: GoogleCalendarApi) {
    fun ensure(existingCalendarId: String, calendarName: String): CalendarTargetResolution = runCatching {
        val existing = existingCalendarId.takeIf(String::isNotBlank)?.let(api::getCalendar)
        val calendar = if (existing?.description == GOOGLE_OWNERSHIP_DESCRIPTION) existing else api.createCalendar(calendarName, GOOGLE_OWNERSHIP_DESCRIPTION)
        CalendarTargetResolution.Success(OwnedCalendarRef(OutputTarget.GOOGLE_CALENDAR, calendar.id, AppOwnedCalendarContract.OWNERSHIP_MARKER))
    }.getOrElse { error ->
        CalendarTargetResolution.Failure(
            summary = error.googleSafeSummary("Google 专用日历不可用"),
            failureCode = error.targetFailureCode(),
        )
    }

    /** 显式清理只复用缓存的已拥有日历；空／失效 ID 只能报错，绝不创建新日历。 */
    fun resolveExisting(existingCalendarId: String): CalendarTargetResolution = runCatching {
        val calendar = existingCalendarId.takeIf(String::isNotBlank)?.let(api::getCalendar)
        require(calendar?.description == GOOGLE_OWNERSHIP_DESCRIPTION) { "找不到 Google 应用专用日历；没有创建新日历。" }
        CalendarTargetResolution.Success(OwnedCalendarRef(OutputTarget.GOOGLE_CALENDAR, calendar.id, AppOwnedCalendarContract.OWNERSHIP_MARKER))
    }.getOrElse { error ->
        CalendarTargetResolution.Failure(
            summary = error.googleSafeSummary("Google 应用专用日历不可用；没有创建新日历"),
            failureCode = error.targetFailureCode(),
        )
    }
}

/** 每次事件操作前验证专用日历的固定所有权标记，拒绝主日历和未标记日历。 */
class GoogleCalendarGateway(private val api: GoogleCalendarApi) : OwnedCalendarGateway {
    override fun readEvent(calendar: OwnedCalendarRef, eventId: String, identity: ManagedEventIdentity): DesiredCalendarEvent? {
        check(verifyOwnership(calendar))
        return api.readManagedEvent(calendar.calendarId, eventId, identity)
    }
    override fun readFingerprint(calendar: OwnedCalendarRef, eventId: String, identity: ManagedEventIdentity): String? {
        check(verifyOwnership(calendar))
        return api.readEventFingerprint(calendar.calendarId, eventId, identity)
    }
    override fun verifyOwnership(calendar: OwnedCalendarRef): Boolean = calendar.target == OutputTarget.GOOGLE_CALENDAR &&
        calendar.ownershipMarker == AppOwnedCalendarContract.OWNERSHIP_MARKER &&
        api.getCalendar(calendar.calendarId)?.description == GOOGLE_OWNERSHIP_DESCRIPTION

    override fun findEventIdsByIdentity(calendar: OwnedCalendarRef, identity: ManagedEventIdentity): List<String> {
        check(verifyOwnership(calendar)) { "拒绝读取非应用拥有的 Google 日历。" }
        return api.findEventIdsByIdentity(calendar.calendarId, identity.opaqueKey())
    }

    override fun findAllManagedEventIds(calendar: OwnedCalendarRef): List<String> {
        check(verifyOwnership(calendar)) { "拒绝读取非应用拥有的 Google 日历。" }
        return api.findAllManagedEventIds(calendar.calendarId)
    }

    override fun create(calendar: OwnedCalendarRef, event: DesiredCalendarEvent): String {
        check(verifyOwnership(calendar)) { "拒绝向非应用拥有的 Google 日历写入。" }
        return api.createEvent(calendar.calendarId, event)
    }

    override fun update(calendar: OwnedCalendarRef, eventId: String, event: DesiredCalendarEvent) {
        check(verifyOwnership(calendar)) { "拒绝向非应用拥有的 Google 日历更新。" }
        api.updateEvent(calendar.calendarId, eventId, event)
    }

    override fun remove(calendar: OwnedCalendarRef, eventId: String): ManagedEventRemovalOutcome {
        check(verifyOwnership(calendar)) { "拒绝从非应用拥有的 Google 日历删除。" }
        return api.deleteEvent(calendar.calendarId, eventId)
    }
}

/** 短令牌仅由构造参数接收并留在进程内；错误摘要不包含 URL、响应体、账户或令牌。 */
class GoogleCalendarRestApi(
    private val accessToken: String,
    private val transport: GoogleCalendarHttpTransport = GoogleCalendarHttpTransport(),
) : GoogleCalendarApi {
    override fun readEventFingerprint(calendarId: String, eventId: String, identity: ManagedEventIdentity): String? {
        return readManagedEvent(calendarId, eventId, identity)?.let { SyncDiffPlanner().run { it.fingerprint() } }
    }

    override fun readManagedEvent(calendarId: String, eventId: String, identity: ManagedEventIdentity): DesiredCalendarEvent? {
        val response = transport.execute(GoogleCalendarRequestStage.FIND_MANAGED_EVENTS, "GET",
            "/calendars/${calendarId.encodePath()}/events/${eventId.encodePath()}?fields=status,summary,location,start,end,colorId,extendedProperties", null, accessToken)
        if (response.statusCode == 404 || response.statusCode == 410) throw ManagedEventMissingException()
        response.requireSuccess()
        return googleManagedEvent(response.body, identity)
    }
    override fun getCalendar(calendarId: String): GoogleRemoteCalendar? {
        val response = transport.execute(GoogleCalendarRequestStage.CHECK_OWNED_CALENDAR, "GET", "/calendars/${calendarId.encodePath()}", null, accessToken)
        if (response.statusCode == HttpURLConnection.HTTP_NOT_FOUND) return null
        response.requireSuccess()
        val json = JSONObject(response.body)
        return GoogleRemoteCalendar(json.getString("id"), json.optString("description").takeIf(String::isNotBlank))
    }

    override fun createCalendar(summary: String, description: String): GoogleRemoteCalendar {
        val response = transport.execute(GoogleCalendarRequestStage.CREATE_OWNED_CALENDAR, "POST", "/calendars", JSONObject().put("summary", summary).put("description", description).toString(), accessToken)
        response.requireSuccess()
        val json = JSONObject(response.body)
        return GoogleRemoteCalendar(json.getString("id"), json.optString("description").takeIf(String::isNotBlank))
    }

    override fun findEventIdsByIdentity(calendarId: String, identityKey: String): List<String> {
        val encoded = java.net.URLEncoder.encode("$GOOGLE_EVENT_IDENTITY_KEY=$identityKey", Charsets.UTF_8.name())
        val response = transport.execute(GoogleCalendarRequestStage.FIND_MANAGED_EVENTS, "GET", "/calendars/${calendarId.encodePath()}/events?singleEvents=true&privateExtendedProperty=$encoded", null, accessToken)
        response.requireSuccess()
        return JSONObject(response.body).optJSONArray("items")?.let { items ->
            buildList { for (index in 0 until items.length()) add(items.getJSONObject(index).getString("id")) }
        }.orEmpty()
    }

    /** 全量清理只解析事件 ID 与不可见应用标记；日历正文不会进入日志、资料库或 UI。 */
    override fun findAllManagedEventIds(calendarId: String): List<String> {
        var pageToken: String? = null
        val ids = linkedSetOf<String>()
        val visitedPageTokens = mutableSetOf<String>()
        do {
            pageToken?.let { token ->
                check(visitedPageTokens.add(token)) { "Google 日历分页令牌重复，已停止清理前核对。" }
            }
            val page = transport.execute(
                GoogleCalendarRequestStage.FIND_ALL_MANAGED_EVENTS,
                "GET",
                googleManagedEventsPath(calendarId, pageToken),
                null,
                accessToken,
            ).requireSuccess().body.parseGoogleManagedEventPage()
            ids += page.eventIds
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return ids.toList()
    }

    override fun createEvent(calendarId: String, event: DesiredCalendarEvent): String {
        val response = transport.execute(GoogleCalendarRequestStage.CREATE_MANAGED_EVENT, "POST", "/calendars/${calendarId.encodePath()}/events?sendUpdates=none", event.jsonBody(), accessToken)
        response.requireSuccess()
        return JSONObject(response.body).getString("id")
    }

    override fun updateEvent(calendarId: String, eventId: String, event: DesiredCalendarEvent) {
        val response = transport.execute(GoogleCalendarRequestStage.UPDATE_MANAGED_EVENT, "PUT", "/calendars/${calendarId.encodePath()}/events/${eventId.encodePath()}?sendUpdates=none", event.jsonBody(), accessToken)
        if (response.statusCode == HttpURLConnection.HTTP_NOT_FOUND) throw ManagedEventMissingException()
        response.requireSuccess()
    }

    override fun deleteEvent(calendarId: String, eventId: String): ManagedEventRemovalOutcome {
        val response = transport.execute(GoogleCalendarRequestStage.DELETE_MANAGED_EVENT, "DELETE", "/calendars/${calendarId.encodePath()}/events/${eventId.encodePath()}?sendUpdates=none", null, accessToken)
        return response.toManagedEventRemovalOutcome()
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

/** 只比较显式应用标记的真实字段；未知颜色／全天格式不得被判为“保持不变”。 */
internal fun googleEventFingerprint(body: String, identity: ManagedEventIdentity): String? {
    return googleManagedEvent(body, identity)?.let { SyncDiffPlanner().run { it.fingerprint() } }
}

private fun googleManagedEvent(body: String, identity: ManagedEventIdentity): DesiredCalendarEvent? {
    val event = JSONObject(body)
    if (event.optString("status") == "cancelled") throw ManagedEventMissingException()
    check(event.optJSONObject("extendedProperties")?.optJSONObject("private")?.optString(GOOGLE_EVENT_IDENTITY_KEY) == identity.opaqueKey()) { "目标事件已不属于该课次，拒绝覆盖" }
    val state = when (event.optString("colorId")) {
        "10" -> AttendanceState.MUST_ATTEND
        "9" -> AttendanceState.CHOOSE_TO_ATTEND
        "8" -> AttendanceState.STOP_ATTENDING
        else -> return null
    }
    val start = event.optJSONObject("start")?.optString("dateTime")?.takeIf(String::isNotBlank) ?: return null
    val end = event.optJSONObject("end")?.optString("dateTime")?.takeIf(String::isNotBlank) ?: return null
    return DesiredCalendarEvent(identity, event.optString("summary"), java.time.OffsetDateTime.parse(start).toInstant(), java.time.OffsetDateTime.parse(end).toInstant(), state, event.optString("location"))
}

/** 全量读取仅在应用专用目标内分页；不会解析标题、地点、描述或日程时间。 */
internal fun googleManagedEventsPath(calendarId: String, pageToken: String?): String = buildString {
    append("/calendars/${java.net.URLEncoder.encode(calendarId, Charsets.UTF_8.name())}/events?singleEvents=true&showDeleted=false&maxResults=250&fields=nextPageToken,items(id,extendedProperties)")
    pageToken?.let { append("&pageToken=${java.net.URLEncoder.encode(it, Charsets.UTF_8.name())}") }
}

internal data class GoogleManagedEventPage(val eventIds: List<String>, val nextPageToken: String?)

internal fun String.parseGoogleManagedEventPage(): GoogleManagedEventPage {
    val json = JSONObject(this)
    val ids = json.optJSONArray("items")?.let { items ->
        buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                if (item.optJSONObject("extendedProperties")?.optJSONObject("private")?.has(GOOGLE_EVENT_IDENTITY_KEY) == true) {
                    add(item.getString("id"))
                }
            }
        }
    }.orEmpty()
    return GoogleManagedEventPage(ids, json.optString("nextPageToken").takeIf(String::isNotBlank))
}

data class GoogleHttpResponse(
    val stage: GoogleCalendarRequestStage,
    val statusCode: Int,
    val body: String,
    val authorizationSignal: GoogleAuthorizationSignal = GoogleAuthorizationSignal.UNSPECIFIED,
) {
    fun requireSuccess(): GoogleHttpResponse {
        if (statusCode !in 200..299) throw GoogleCalendarRequestException(stage, statusCode, authorizationSignal)
        return this
    }
}

/** 仅对应用已映射事件的删除：404／410 表示目标本已不存在，清理目标已经达成。 */
internal fun GoogleHttpResponse.toManagedEventRemovalOutcome(): ManagedEventRemovalOutcome {
    require(stage == GoogleCalendarRequestStage.DELETE_MANAGED_EVENT) { "删除结果只能来自删除请求" }
    return when {
        statusCode in 200..299 -> ManagedEventRemovalOutcome.REMOVED_NOW
        statusCode == HttpURLConnection.HTTP_NOT_FOUND || statusCode == HttpURLConnection.HTTP_GONE -> ManagedEventRemovalOutcome.ALREADY_ABSENT
        else -> {
            requireSuccess()
            error("不可达")
        }
    }
}

/** 只携带状态码，避免 Google 响应正文、账户或令牌进入界面和日志。 */
class GoogleCalendarRequestException(
    val stage: GoogleCalendarRequestStage = GoogleCalendarRequestStage.CHECK_OWNED_CALENDAR,
    val statusCode: Int,
    val authorizationSignal: GoogleAuthorizationSignal = GoogleAuthorizationSignal.UNSPECIFIED,
) : IllegalStateException("Google Calendar HTTP $statusCode")

/** 只有 Google 明确拒绝访问令牌时才触发重新授权；403 等其他问题不清缓存。 */
internal fun Throwable.targetFailureCode(): TargetFailureCode? =
    if (this is GoogleCalendarRequestException && statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) TargetFailureCode.GOOGLE_AUTHORIZATION_EXPIRED else null

internal fun Throwable.googleSafeSummary(prefix: String): String =
    if (this is GoogleCalendarRequestException) "$prefix（${stage.label}时收到 HTTP $statusCode；${authorizationSignal.label}）" else "$prefix；请检查授权和网络后重试"

/** 最小 HTTPS 传输实现；限制响应大小，且不将请求、令牌或响应原文写入日志。 */
class GoogleCalendarHttpTransport(private val maxResponseBytes: Int = 512 * 1024) {
    fun execute(stage: GoogleCalendarRequestStage, method: String, path: String, body: String?, accessToken: String): GoogleHttpResponse {
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
            val statusCode = connection.responseCode
            val bytes = if (statusCode in 200..299) connection.inputStream.use {
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val count = it.read(chunk)
                    if (count < 0) break
                    require(buffer.size() + count <= maxResponseBytes) { "Google 响应超过大小限制" }
                    buffer.write(chunk, 0, count)
                }
                buffer.toByteArray()
            } else {
                ByteArray(0)
            }
            GoogleHttpResponse(stage, statusCode, bytes.toString(Charsets.UTF_8), connection.getHeaderField("WWW-Authenticate").toGoogleAuthorizationSignal())
        } finally {
            connection.disconnect()
        }
    }

    /** 头部仅识别 RFC 常见错误码，任何其他字段均丢弃。 */
    private fun String?.toGoogleAuthorizationSignal(): GoogleAuthorizationSignal = when {
        this?.contains("invalid_token", ignoreCase = true) == true -> GoogleAuthorizationSignal.INVALID_TOKEN
        this?.contains("insufficient_scope", ignoreCase = true) == true -> GoogleAuthorizationSignal.INSUFFICIENT_SCOPE
        this?.contains("invalid_request", ignoreCase = true) == true -> GoogleAuthorizationSignal.INVALID_REQUEST
        else -> GoogleAuthorizationSignal.UNSPECIFIED
    }
}
