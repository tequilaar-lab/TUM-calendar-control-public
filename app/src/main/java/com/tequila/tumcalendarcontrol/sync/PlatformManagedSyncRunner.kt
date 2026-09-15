/*
 * 职责：将已确认的目标开关、短令牌与专用日历解析结果接到受控同步／撤销引擎。
 * 输入／输出：输入目标集合、候选、映射和私有配置；输出下一份配置、映射和脱敏结果。
 * 外部副作用：仅在调用 execute 或 rollback 时访问 Calendar Provider／Google HTTPS；不会自行启动。
 * 不负责：读取 iCal、申请权限、OAuth UI、保存结果、展示进度或访问用户主日历。
 */
package com.tequila.tumcalendarcontrol.sync

import android.content.Context

data class PlatformManagedSyncOutcome(
    val result: ManagedSyncResult,
    val configuration: PrivateSyncConfiguration,
) {
    /** 只由 Google HTTP 401 置位；供 UI 清理失效短令牌且要求用户显式重新连接。 */
    val requiresGoogleReauthorization: Boolean get() = result.targetResults.any {
        it.target == OutputTarget.GOOGLE_CALENDAR && it.failureCode == TargetFailureCode.GOOGLE_AUTHORIZATION_EXPIRED
    }
}

class PlatformManagedSyncRunner(private val context: Context, private val engine: ManagedSyncEngine = ManagedSyncEngine()) {
    fun execute(
        desired: List<DesiredCalendarEvent>,
        mappings: List<StoredEventMapping>,
        targets: Set<OutputTarget>,
        configuration: PrivateSyncConfiguration,
        googleAccessToken: String?,
    ): PlatformManagedSyncOutcome = run(desired, mappings, targets, configuration, googleAccessToken, rollback = false)

    fun rollback(
        mappings: List<StoredEventMapping>,
        targets: Set<OutputTarget>,
        configuration: PrivateSyncConfiguration,
        googleAccessToken: String?,
    ): PlatformManagedSyncOutcome = run(emptyList(), mappings, targets, configuration, googleAccessToken, rollback = true)

    private fun run(
        desired: List<DesiredCalendarEvent>,
        mappings: List<StoredEventMapping>,
        targets: Set<OutputTarget>,
        configuration: PrivateSyncConfiguration,
        googleAccessToken: String?,
        rollback: Boolean,
    ): PlatformManagedSyncOutcome {
        var nextConfiguration = configuration
        val bindings = mutableListOf<TargetBinding>()
        val failures = mutableListOf<TargetSyncResult>()
        if (OutputTarget.LOCAL_CALENDAR in targets) {
            when (val resolution = AndroidLocalCalendarTarget(context.contentResolver).ensure(configuration.localCalendarId)) {
                is CalendarTargetResolution.Success -> {
                    nextConfiguration = nextConfiguration.copy(localCalendarId = resolution.calendar.calendarId)
                    bindings += TargetBinding(resolution.calendar, AndroidLocalCalendarGateway(context.contentResolver))
                }
                is CalendarTargetResolution.Failure -> failures += TargetSyncResult(
                    OutputTarget.LOCAL_CALENDAR,
                    mappings,
                    emptyList(),
                    resolution.summary,
                    resolution.failureCode,
                )
            }
        }
        if (OutputTarget.GOOGLE_CALENDAR in targets) {
            if (googleAccessToken.isNullOrBlank()) {
                failures += TargetSyncResult(OutputTarget.GOOGLE_CALENDAR, mappings, emptyList(), "Google 未获本次运行授权；未执行写入")
            } else {
                val api = GoogleCalendarRestApi(googleAccessToken)
                when (val resolution = GoogleCalendarTarget(api).ensure(configuration.googleCalendarId, configuration.googleCalendarName)) {
                    is CalendarTargetResolution.Success -> {
                        nextConfiguration = nextConfiguration.copy(googleCalendarId = resolution.calendar.calendarId)
                        bindings += TargetBinding(resolution.calendar, GoogleCalendarGateway(api))
                    }
                    is CalendarTargetResolution.Failure -> failures += TargetSyncResult(
                        OutputTarget.GOOGLE_CALENDAR,
                        mappings,
                        emptyList(),
                        resolution.summary,
                        resolution.failureCode,
                    )
                }
            }
        }
        val completed = if (rollback) engine.rollback(mappings, bindings) else engine.execute(desired, mappings, bindings)
        return PlatformManagedSyncOutcome(
            result = completed.copy(targetResults = completed.targetResults + failures),
            configuration = nextConfiguration,
        )
    }
}
