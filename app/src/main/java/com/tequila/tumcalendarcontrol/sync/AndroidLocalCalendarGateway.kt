/*
 * Aufgabe: Eigenen lokalen Android-Kalender suchen/erstellen und ausschließlich darin schreiben.
 * Ein-/Ausgabe: Private Ziel-ID, Eigentumsmarker und Solltermine -> Zielreferenz oder Termin-ID.
 * Nebenwirkungen: Nur nach erteilten Kalenderrechten und bestätigter Ausführung eigene Provider-Ziele lesen/schreiben.
 * Nicht zuständig: Berechtigungsanfragen, fremde Kalender, Google-Synchronisierung, Quellen-Parsing oder Zuordnungsspeicherung.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.i18n.message

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.ZoneId

object AppOwnedCalendarContract {
    const val OWNERSHIP_MARKER = "tum-calendar-control"
    const val LOCAL_ACCOUNT_NAME = "TUM Calendar Control"
    const val LOCAL_CALENDAR_NAME = "tum-calendar-control-local"
    const val LOCAL_DISPLAY_NAME = "TUM 日历控制"
    private const val EVENT_DESCRIPTION = "由 TUM 日历控制创建。"

    /** Beschreibung nennt nur App-Zugehörigkeit; Synchronisierungsidentität bleibt in unsichtbaren Feldern und privaten Zuordnungen. */
    fun eventDescription(): String = EVENT_DESCRIPTION
}

sealed interface CalendarTargetResolution {
    data class Success(val calendar: OwnedCalendarRef) : CalendarTargetResolution
    data class Failure(val summary: String, val failureCode: TargetFailureCode? = null) : CalendarTargetResolution
}

/** Erst nach erteilter Synchronisierungsberechtigung aufrufen; ungültiges Ziel durch eigenen lokalen Kalender ersetzen. */
class AndroidLocalCalendarTarget(private val resolver: ContentResolver) {
    fun ensure(existingCalendarId: String): CalendarTargetResolution = runCatching {
        val existing = existingCalendarId.takeIf(String::isNotBlank)
        if (existing != null && AndroidLocalCalendarGateway(resolver).verifyOwnership(localRef(existing))) {
            return CalendarTargetResolution.Success(localRef(existing))
        }
        findExisting()?.let { return CalendarTargetResolution.Success(localRef(it)) }
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, AppOwnedCalendarContract.LOCAL_ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, AppOwnedCalendarContract.LOCAL_CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, AppOwnedCalendarContract.LOCAL_DISPLAY_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF0065BD.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, AppOwnedCalendarContract.LOCAL_ACCOUNT_NAME)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }
        val uri = resolver.insert(asSyncAdapter(CalendarContract.Calendars.CONTENT_URI), values)
            ?: error(message(35))
        CalendarTargetResolution.Success(localRef(ContentUris.parseId(uri).toString()))
    }.getOrElse { CalendarTargetResolution.Failure(message(36)) }

    /** Bereinigung erstellt niemals Ersatzkalender; nur weiterhin nachweisbar eigene Ziele verwenden. */
    fun resolveExisting(existingCalendarId: String): CalendarTargetResolution = runCatching {
        val resolvedId = existingCalendarId.takeIf(String::isNotBlank)
            ?.takeIf { AndroidLocalCalendarGateway(resolver).verifyOwnership(localRef(it)) }
            ?: findExisting()
            ?: return CalendarTargetResolution.Failure(message(37))
        CalendarTargetResolution.Success(localRef(resolvedId))
    }.getOrElse { CalendarTargetResolution.Failure(message(38)) }

    private fun findExisting(): String? = resolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        arrayOf(CalendarContract.Calendars._ID),
        "${CalendarContract.Calendars.ACCOUNT_NAME}=? AND ${CalendarContract.Calendars.ACCOUNT_TYPE}=? AND ${CalendarContract.Calendars.NAME}=?",
        arrayOf(AppOwnedCalendarContract.LOCAL_ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL, AppOwnedCalendarContract.LOCAL_CALENDAR_NAME),
        null,
    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0).toString() else null }

    private fun localRef(id: String) = OwnedCalendarRef(OutputTarget.LOCAL_CALENDAR, id, AppOwnedCalendarContract.OWNERSHIP_MARKER)

    private fun asSyncAdapter(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, AppOwnedCalendarContract.LOCAL_ACCOUNT_NAME)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()
}

/** Termin-API prüft erneut Eigentum; keine Löschentscheidung nach Titel, Farbe oder Ort. */
class AndroidLocalCalendarGateway(private val resolver: ContentResolver) : OwnedCalendarGateway {
    /** Gateway lebt nur für eine Ausgabe; bestätigtes Eigentum wird darin nicht vor jedem Aufruf erneut abgefragt. */
    private val verifiedCalendars = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    override fun verifyOwnership(calendar: OwnedCalendarRef): Boolean {
        if (calendar.calendarId in verifiedCalendars && calendar.target == OutputTarget.LOCAL_CALENDAR &&
            calendar.ownershipMarker == AppOwnedCalendarContract.OWNERSHIP_MARKER) return true
        return queryOwnership(calendar).also { if (it) verifiedCalendars += calendar.calendarId }
    }

    private fun queryOwnership(calendar: OwnedCalendarRef): Boolean {
        if (calendar.target != OutputTarget.LOCAL_CALENDAR || calendar.ownershipMarker != AppOwnedCalendarContract.OWNERSHIP_MARKER) return false
        val id = calendar.calendarId.toLongOrNull() ?: return false
        return resolver.query(
            ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id),
            arrayOf(CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.Calendars.NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            cursor.moveToFirst() && cursor.getString(0) == AppOwnedCalendarContract.LOCAL_ACCOUNT_NAME &&
                cursor.getString(1) == CalendarContract.ACCOUNT_TYPE_LOCAL &&
                cursor.getString(2) == AppOwnedCalendarContract.LOCAL_CALENDAR_NAME
        } == true
    }

    override fun create(calendar: OwnedCalendarRef, event: DesiredCalendarEvent): String {
        check(verifyOwnership(calendar)) { message(39) }
        val uri = resolver.insert(asSyncAdapter(CalendarContract.Events.CONTENT_URI), eventValues(calendar, event)) ?: error(message(40))
        return ContentUris.parseId(uri).toString()
    }

    /** Ein Abfragedurchlauf über alle App-markierten Termine des eigenen Kalenders (Identitätsschlüssel -> Einträge). */
    override fun snapshotManagedEvents(calendar: OwnedCalendarRef): Map<String, List<ManagedEventSnapshot>> {
        check(verifyOwnership(calendar)) { message(41) }
        val result = mutableMapOf<String, MutableList<ManagedEventSnapshot>>()
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID, CalendarContract.Events.SYNC_DATA1, CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND, CalendarContract.Events.EVENT_LOCATION, CalendarContract.Events.EVENT_COLOR),
            "${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.DELETED}=0 AND ${CalendarContract.Events.SYNC_DATA1} IS NOT NULL",
            arrayOf(calendar.calendarId), null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val state = AttendanceState.entries.firstOrNull { it.androidColor() == cursor.getInt(6) }
                val content = state?.let {
                    ManagedEventContent(cursor.getString(2).orEmpty(), java.time.Instant.ofEpochMilli(cursor.getLong(3)), java.time.Instant.ofEpochMilli(cursor.getLong(4)), it, cursor.getString(5).orEmpty())
                }
                result.getOrPut(cursor.getString(1)) { mutableListOf() } += ManagedEventSnapshot(cursor.getLong(0).toString(), content)
            }
        } ?: error(message(42))
        return result
    }

    override fun findEventIdsByIdentity(calendar: OwnedCalendarRef, identity: ManagedEventIdentity): List<String> {
        check(verifyOwnership(calendar)) { message(41) }
        return resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.DELETED}=0 AND ${CalendarContract.Events.SYNC_DATA1}=?",
            arrayOf(calendar.calendarId, identity.opaqueKey()),
            null,
        )?.use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getLong(0).toString()) } } ?: error(message(42))
    }

    /** Nur nach ausdrücklichem „Alle löschen“; liest ausschließlich Synchronisierungsfelder, keine Titel, Orte oder Zeiten. */
    override fun findAllManagedEventIds(calendar: OwnedCalendarRef): List<String> {
        check(verifyOwnership(calendar)) { message(41) }
        return resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.DELETED}=0 AND ${CalendarContract.Events.SYNC_DATA1} IS NOT NULL AND ${CalendarContract.Events.DESCRIPTION}=?",
            arrayOf(calendar.calendarId, AppOwnedCalendarContract.eventDescription()),
            null,
        )?.use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getLong(0).toString()) } } ?: error(message(43))
    }

    override fun update(calendar: OwnedCalendarRef, eventId: String, event: DesiredCalendarEvent) {
        check(verifyOwnership(calendar)) { message(44) }
        val updated = resolver.update(asSyncAdapter(CalendarContract.Events.CONTENT_URI), eventValues(calendar, event),
            "${CalendarContract.Events._ID}=? AND ${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.DELETED}=0 AND ${CalendarContract.Events.SYNC_DATA1}=?",
            arrayOf(eventId, calendar.calendarId, event.identity.opaqueKey()))
        if (updated == 0) throw ManagedEventMissingException()
        check(updated == 1) { message(45) }
    }

    override fun readEvent(calendar: OwnedCalendarRef, eventId: String, identity: ManagedEventIdentity): DesiredCalendarEvent? {
        check(verifyOwnership(calendar))
        return resolver.query(CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND, CalendarContract.Events.EVENT_LOCATION, CalendarContract.Events.EVENT_COLOR),
            "${CalendarContract.Events._ID}=? AND ${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.DELETED}=0 AND ${CalendarContract.Events.SYNC_DATA1}=?",
            arrayOf(eventId, calendar.calendarId, identity.opaqueKey()), null)?.use { cursor ->
            if (!cursor.moveToFirst()) throw ManagedEventMissingException()
            val state = AttendanceState.entries.firstOrNull { it.androidColor() == cursor.getInt(4) } ?: return@use null
            DesiredCalendarEvent(identity, cursor.getString(0).orEmpty(), java.time.Instant.ofEpochMilli(cursor.getLong(1)), java.time.Instant.ofEpochMilli(cursor.getLong(2)), state, cursor.getString(3).orEmpty())
        }
    }

    override fun remove(calendar: OwnedCalendarRef, eventId: String): ManagedEventRemovalOutcome {
        check(verifyOwnership(calendar)) { message(46) }
        val removed = resolver.delete(asSyncAdapter(CalendarContract.Events.CONTENT_URI),
            "${CalendarContract.Events._ID}=? AND ${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.SYNC_DATA1} IS NOT NULL AND ${CalendarContract.Events.DESCRIPTION}=?",
            arrayOf(eventId, calendar.calendarId, AppOwnedCalendarContract.eventDescription()))
        check(removed in 0..1) { message(47) }
        return if (removed == 1) ManagedEventRemovalOutcome.REMOVED_NOW else ManagedEventRemovalOutcome.ALREADY_ABSENT
    }

    private fun eventValues(calendar: OwnedCalendarRef, event: DesiredCalendarEvent) = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calendar.calendarId.toLong())
        put(CalendarContract.Events.TITLE, event.title)
        put(CalendarContract.Events.DTSTART, event.startsAt.toEpochMilli())
        put(CalendarContract.Events.DTEND, event.endsAt.toEpochMilli())
        put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.of("Europe/Berlin").id)
        put(CalendarContract.Events.EVENT_LOCATION, event.location)
        put(CalendarContract.Events.DESCRIPTION, AppOwnedCalendarContract.eventDescription())
        put(CalendarContract.Events.SYNC_DATA1, event.identity.opaqueKey())
        put(CalendarContract.Events.EVENT_COLOR, event.attendanceState.androidColor())
    }

    private fun AttendanceState.androidColor(): Int = when (this) {
        AttendanceState.MUST_ATTEND -> 0xFF34A853.toInt()
        AttendanceState.CHOOSE_TO_ATTEND -> 0xFF4285F4.toInt()
        AttendanceState.STOP_ATTENDING -> 0xFF9AA0A6.toInt()
    }

    private fun asSyncAdapter(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, AppOwnedCalendarContract.LOCAL_ACCOUNT_NAME)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()
}
