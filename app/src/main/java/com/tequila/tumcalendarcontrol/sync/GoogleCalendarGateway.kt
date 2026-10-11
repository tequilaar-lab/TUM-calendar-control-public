/*
 * Aufgabe: Eigenen Google-Zweitkalender über REST suchen/erstellen und zugeordnete Termine verwalten.
 * Ein-/Ausgabe: Kurzlebiger Token, Ziel-ID und Solltermine -> Zielreferenz, Termin-ID oder bereinigter Fehler.
 * Nebenwirkungen: HTTPS-Zugriffe nur im ausdrücklich bestätigten eigenen Ziel.
 * Nicht zuständig: OAuth-Start, Tokenspeicherung, Hauptkalender, Auflistung fremder Kalender oder Quellen-Parsing.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.i18n.message

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

/** Aufgabe: stabile Google-Anfragephasen ohne URL, Kalender-ID oder Token. Ein-/Ausgabe: Adapterphase -> Anzeigehinweis. Keine Nebenwirkungen; keine Anfrageentscheidung, Diagnoseablage oder Antworttexte. */
enum class GoogleCalendarRequestStage(val label: String) {
    CHECK_OWNED_CALENDAR(message(48)),
    CREATE_OWNED_CALENDAR(message(49)),
    FIND_MANAGED_EVENTS(message(50)),
    FIND_ALL_MANAGED_EVENTS(message(51)),
    CREATE_MANAGED_EVENT(message(52)),
    UPDATE_MANAGED_EVENT(message(53)),
    DELETE_MANAGED_EVENT(message(54)),
}

/** Nur feste Fehlerbegriffe aus WWW-Authenticate lesen; Antworttext weder lesen noch speichern. */
enum class GoogleAuthorizationSignal(val label: String) {
    INVALID_TOKEN(message(55)),
    INSUFFICIENT_SCOPE(message(56)),
    INVALID_REQUEST(message(57)),
    UNSPECIFIED(message(58)),
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

/** Nur zuvor erstellte Zweitkalender mit festem Marker akzeptieren; bei Ausfall eigenes Ziel erstellen, Seitenleistenanzeige unverändert. */
class GoogleCalendarTarget(private val api: GoogleCalendarApi) {
    fun ensure(existingCalendarId: String, calendarName: String): CalendarTargetResolution = runCatching {
        val existing = existingCalendarId.takeIf(String::isNotBlank)?.let(api::getCalendar)
        val calendar = if (existing?.description == GOOGLE_OWNERSHIP_DESCRIPTION) existing else api.createCalendar(calendarName, GOOGLE_OWNERSHIP_DESCRIPTION)
        CalendarTargetResolution.Success(OwnedCalendarRef(OutputTarget.GOOGLE_CALENDAR, calendar.id, AppOwnedCalendarContract.OWNERSHIP_MARKER))
    }.getOrElse { error ->
        CalendarTargetResolution.Failure(
            summary = error.googleSafeSummary(message(59)),
            failureCode = error.targetFailureCode(),
        )
    }

    /** Bereinigung verwendet nur vorhandene eigene Kalender; leere/ungültige ID führt zum Fehler, nie zur Neuerstellung. */
    fun resolveExisting(existingCalendarId: String): CalendarTargetResolution = runCatching {
        val calendar = existingCalendarId.takeIf(String::isNotBlank)?.let(api::getCalendar)
        require(calendar?.description == GOOGLE_OWNERSHIP_DESCRIPTION) { message(60) }
        CalendarTargetResolution.Success(OwnedCalendarRef(OutputTarget.GOOGLE_CALENDAR, calendar.id, AppOwnedCalendarContract.OWNERSHIP_MARKER))
    }.getOrElse { error ->
        CalendarTargetResolution.Failure(
            summary = error.googleSafeSummary(message(61)),
            failureCode = error.targetFailureCode(),
        )
    }
}

/** Vor jeder Aktion festen Eigentumsmarker prüfen; Hauptkalender und unmarkierte Kalender ablehnen. */
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
        check(verifyOwnership(calendar)) { message(62) }
        return api.findEventIdsByIdentity(calendar.calendarId, identity.opaqueKey())
    }

    override fun findAllManagedEventIds(calendar: OwnedCalendarRef): List<String> {
        check(verifyOwnership(calendar)) { message(62) }
        return api.findAllManagedEventIds(calendar.calendarId)
    }

    override fun create(calendar: OwnedCalendarRef, event: DesiredCalendarEvent): String {
        check(verifyOwnership(calendar)) { message(63) }
        return api.createEvent(calendar.calendarId, event)
    }

    override fun update(calendar: OwnedCalendarRef, eventId: String, event: DesiredCalendarEvent) {
        check(verifyOwnership(calendar)) { message(64) }
        api.updateEvent(calendar.calendarId, eventId, event)
    }

    override fun remove(calendar: OwnedCalendarRef, eventId: String): ManagedEventRemovalOutcome {
        check(verifyOwnership(calendar)) { message(65) }
        return api.deleteEvent(calendar.calendarId, eventId)
    }
}

/** Kurzlebiger Token nur aus Konstruktor, nur im Prozess; Fehlermeldung ohne URL, Antworttext, Konto oder Token. */
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

    /** Vollständige Bereinigung liest nur Termin-IDs und unsichtbare App-Marker; Kalenderinhalt nicht in Protokoll, Datenbank oder Oberfläche. */
    override fun findAllManagedEventIds(calendarId: String): List<String> {
        var pageToken: String? = null
        val ids = linkedSetOf<String>()
        val visitedPageTokens = mutableSetOf<String>()
        do {
            pageToken?.let { token ->
                check(visitedPageTokens.add(token)) { message(66) }
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

/** Nur echte markierte Zielfelder vergleichen; unbekannte Farbe/Ganztagsformate gelten nicht als unverändert. */
internal fun googleEventFingerprint(body: String, identity: ManagedEventIdentity): String? {
    return googleManagedEvent(body, identity)?.let { SyncDiffPlanner().run { it.fingerprint() } }
}

private fun googleManagedEvent(body: String, identity: ManagedEventIdentity): DesiredCalendarEvent? {
    val event = JSONObject(body)
    if (event.optString("status") == "cancelled") throw ManagedEventMissingException()
    check(event.optJSONObject("extendedProperties")?.optJSONObject("private")?.optString(GOOGLE_EVENT_IDENTITY_KEY) == identity.opaqueKey()) { message(67) }
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

/** Vollständige Seitennavigation nur im eigenen Ziel; Titel, Ort, Beschreibung und Zeiten nicht auswerten. */
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

/** Bei zugeordneten App-Terminen bedeutet 404/410: bereits abwesend; Bereinigungsziel erreicht. */
internal fun GoogleHttpResponse.toManagedEventRemovalOutcome(): ManagedEventRemovalOutcome {
    require(stage == GoogleCalendarRequestStage.DELETE_MANAGED_EVENT) { message(68) }
    return when {
        statusCode in 200..299 -> ManagedEventRemovalOutcome.REMOVED_NOW
        statusCode == HttpURLConnection.HTTP_NOT_FOUND || statusCode == HttpURLConnection.HTTP_GONE -> ManagedEventRemovalOutcome.ALREADY_ABSENT
        else -> {
            requireSuccess()
            error(message(69))
        }
    }
}

/** Nur Statuscode weitergeben; Google-Antworttext, Konto und Token nicht in Anzeige oder Protokoll. */
class GoogleCalendarRequestException(
    val stage: GoogleCalendarRequestStage = GoogleCalendarRequestStage.CHECK_OWNED_CALENDAR,
    val statusCode: Int,
    val authorizationSignal: GoogleAuthorizationSignal = GoogleAuthorizationSignal.UNSPECIFIED,
) : IllegalStateException("Google Calendar HTTP $statusCode")

/** Neuauthorisierung nur bei ausdrücklich abgelehntem Token; 403 und andere Fehler leeren keinen Cache. */
internal fun Throwable.targetFailureCode(): TargetFailureCode? =
    if (this is GoogleCalendarRequestException && statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) TargetFailureCode.GOOGLE_AUTHORIZATION_EXPIRED else null

internal fun Throwable.googleSafeSummary(prefix: String): String =
    if (this is GoogleCalendarRequestException) message(70, (prefix), (stage.label), (statusCode), (authorizationSignal.label)) else message(71, (prefix))

/** Minimale HTTPS-Übertragung mit Größenlimit; keine Anfrage-, Token- oder Antwortprotokollierung. */
class GoogleCalendarHttpTransport(private val maxResponseBytes: Int = 512 * 1024) {
    fun execute(stage: GoogleCalendarRequestStage, method: String, path: String, body: String?, accessToken: String): GoogleHttpResponse {
        require(path.startsWith('/')) { message(72) }
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
                    require(buffer.size() + count <= maxResponseBytes) { message(73) }
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

    /** Nur übliche RFC-Fehlercodes aus Header erkennen; andere Felder verwerfen. */
    private fun String?.toGoogleAuthorizationSignal(): GoogleAuthorizationSignal = when {
        this?.contains("invalid_token", ignoreCase = true) == true -> GoogleAuthorizationSignal.INVALID_TOKEN
        this?.contains("insufficient_scope", ignoreCase = true) == true -> GoogleAuthorizationSignal.INSUFFICIENT_SCOPE
        this?.contains("invalid_request", ignoreCase = true) == true -> GoogleAuthorizationSignal.INVALID_REQUEST
        else -> GoogleAuthorizationSignal.UNSPECIFIED
    }
}
