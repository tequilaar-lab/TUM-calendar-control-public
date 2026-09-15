/*
 * 职责：在 Android Calendar Provider 中定位／创建应用专用本地日历，并只写入该日历。
 * 输入／输出：输入应用私有目标 ID、拥有权标记和期望事件；输出目标引用或事件 ID。
 * 外部副作用：经用户授予 READ/WRITE_CALENDAR 且执行同步时才查询、创建、更新或删除本地日历事件。
 * 不负责：申请权限、读取用户其他日历、Google 同步、来源解析或保存映射。
 */
package com.tequila.tumcalendarcontrol.sync

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

    /** 日历描述只声明应用归属；同步身份仅保存在不可见同步字段与应用私有映射中。 */
    fun eventDescription(): String = EVENT_DESCRIPTION
}

sealed interface CalendarTargetResolution {
    data class Success(val calendar: OwnedCalendarRef) : CalendarTargetResolution
    data class Failure(val summary: String, val failureCode: TargetFailureCode? = null) : CalendarTargetResolution
}

/** 只有在同步已获权限的阶段调用；若缓存目标无效则新建一个本地专用日历。 */
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
            ?: error("本机专用日历未能创建")
        CalendarTargetResolution.Success(localRef(ContentUris.parseId(uri).toString()))
    }.getOrElse { CalendarTargetResolution.Failure("本机专用日历不可用；请检查日历权限后重试") }

    /** 显式清理绝不为“找不到旧日历”创建新的日历；只复用仍可验证的应用目标。 */
    fun resolveExisting(existingCalendarId: String): CalendarTargetResolution = runCatching {
        val resolvedId = existingCalendarId.takeIf(String::isNotBlank)
            ?.takeIf { AndroidLocalCalendarGateway(resolver).verifyOwnership(localRef(it)) }
            ?: findExisting()
            ?: return CalendarTargetResolution.Failure("找不到本机应用专用日历；没有创建新日历。")
        CalendarTargetResolution.Success(localRef(resolvedId))
    }.getOrElse { CalendarTargetResolution.Failure("本机专用日历不可用；没有创建新日历。") }

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

/** 事件 API 再次核对目标所有权；不会根据标题、颜色或位置删除任何事件。 */
class AndroidLocalCalendarGateway(private val resolver: ContentResolver) : OwnedCalendarGateway {
    override fun verifyOwnership(calendar: OwnedCalendarRef): Boolean {
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
        check(verifyOwnership(calendar)) { "拒绝向非应用拥有的本机日历写入。" }
        val uri = resolver.insert(asSyncAdapter(CalendarContract.Events.CONTENT_URI), eventValues(calendar, event)) ?: error("本机事件未能创建")
        return ContentUris.parseId(uri).toString()
    }

    override fun findEventIdsByIdentity(calendar: OwnedCalendarRef, identity: ManagedEventIdentity): List<String> {
        check(verifyOwnership(calendar)) { "拒绝读取非应用拥有的本机日历。" }
        return resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.SYNC_DATA1}=?",
            arrayOf(calendar.calendarId, identity.opaqueKey()),
            null,
        )?.use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getLong(0).toString()) } }.orEmpty()
    }

    /** 只用于用户明确选择“清除全部”；查询仅返回应用同步字段，不读取标题、地点或时间。 */
    override fun findAllManagedEventIds(calendar: OwnedCalendarRef): List<String> {
        check(verifyOwnership(calendar)) { "拒绝读取非应用拥有的本机日历。" }
        return resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.SYNC_DATA1} IS NOT NULL AND ${CalendarContract.Events.DESCRIPTION}=?",
            arrayOf(calendar.calendarId, AppOwnedCalendarContract.eventDescription()),
            null,
        )?.use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getLong(0).toString()) } }.orEmpty()
    }

    override fun update(calendar: OwnedCalendarRef, eventId: String, event: DesiredCalendarEvent) {
        check(verifyOwnership(calendar)) { "拒绝向非应用拥有的本机日历更新。" }
        val updated = resolver.update(ContentUris.withAppendedId(asSyncAdapter(CalendarContract.Events.CONTENT_URI), eventId.toLong()), eventValues(calendar, event), null, null)
        if (updated == 0) throw ManagedEventMissingException()
        check(updated == 1) { "本机事件未能更新" }
    }

    override fun remove(calendar: OwnedCalendarRef, eventId: String) {
        check(verifyOwnership(calendar)) { "拒绝从非应用拥有的本机日历删除。" }
        val removed = resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId.toLong()), null, null)
        check(removed in 0..1) { "本机事件未能删除" }
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
