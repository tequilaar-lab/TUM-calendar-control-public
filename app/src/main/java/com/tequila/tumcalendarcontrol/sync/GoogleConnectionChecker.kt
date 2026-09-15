/*
 * 职责：在不创建或写入日历的前提下，核对已保存的应用专用 Google 日历是否可访问。
 * 输入／输出：输入短令牌与应用私有目标 ID；输出不含账户、令牌、日历名称或 URL 的诊断摘要。
 * 外部副作用：调用时只读取一个由应用保存的专用日历 ID；不会查询主日历、日历列表或事件。
 * 不负责：获取 OAuth 令牌、创建／修复目标日历、读写日程、保存诊断或替代用户确认。
 */
package com.tequila.tumcalendarcontrol.sync

sealed interface GoogleConnectionCheckResult {
    data object NoManagedCalendar : GoogleConnectionCheckResult
    data object Available : GoogleConnectionCheckResult
    data object Missing : GoogleConnectionCheckResult
    data object OwnershipUnverified : GoogleConnectionCheckResult
    data class Failed(val summary: String) : GoogleConnectionCheckResult

    val userSummary: String
        get() = when (this) {
            NoManagedCalendar -> "还没有本应用创建的 Google 日历；本次检查不会创建日历。首次确认写入后才能检查。"
            Available -> "Google 连接可用：已能读取应用专用日历。本次没有写入任何日程。"
            Missing -> "Google 连接可用，但此前的应用专用日历已不存在；本次没有创建或写入。"
            OwnershipUnverified -> "Google 连接可用，但保存的目标不再带有应用标记；为保护其他日历，本次没有写入。"
            is Failed -> summary
        }
}

class GoogleConnectionChecker(private val api: GoogleCalendarApi) {
    fun check(managedCalendarId: String): GoogleConnectionCheckResult {
        if (managedCalendarId.isBlank()) return GoogleConnectionCheckResult.NoManagedCalendar
        return runCatching { api.getCalendar(managedCalendarId) }
            .fold(
                onSuccess = { calendar ->
                    when {
                        calendar == null -> GoogleConnectionCheckResult.Missing
                        calendar.description == GOOGLE_OWNERSHIP_DESCRIPTION -> GoogleConnectionCheckResult.Available
                        else -> GoogleConnectionCheckResult.OwnershipUnverified
                    }
                },
                onFailure = { error ->
                    GoogleConnectionCheckResult.Failed(error.googleSafeSummary("Google 连接检查失败"))
                },
            )
    }
}
