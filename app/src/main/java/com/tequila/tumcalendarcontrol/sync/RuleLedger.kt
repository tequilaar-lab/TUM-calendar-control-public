/*
 * 职责：定义应用私有规则资料库及其稳定输出映射、手动事项和脱敏同步摘要。
 * 输入／输出：输入合成或未来已授权的规则资料；输出可持久化且不含连接凭据的不可变资料库。
 * 外部副作用：无。
 * 不负责：保存凭据、读取 iCal、访问 Android Calendar Provider 或写入 Google。
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.Instant

data class RuleLedger(
    val rules: List<SyncRule> = emptyList(),
    val manualEvents: List<ManualScheduleItem> = emptyList(),
    val mappings: List<StoredEventMapping> = emptyList(),
    val lastSummary: SyncRunSummary? = null,
)

/** 手动校园事项拥有应用生成的稳定 ID，不与来源 UID 混淆。 */
data class ManualScheduleItem(
    val id: String,
    val title: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val attendanceState: AttendanceState,
)

enum class OutputTarget { LOCAL_CALENDAR, GOOGLE_CALENDAR }

/** 映射只记录应用已拥有的目标事件，不记录日历正文或账户信息。 */
data class StoredEventMapping(
    val target: OutputTarget,
    val identity: ManagedEventIdentity,
    val targetEventId: String,
    val fingerprint: String,
    val calendarId: String? = null,
    /** 仅存应用私有资料库，供来源消失后按课程／日期清理；不导出备份。 */
    val writtenEvent: DesiredCalendarEvent? = null,
)

/** 失败信息只能是可显示的脱敏摘要，不能存放响应、账户或令牌。 */
data class SyncRunSummary(
    val completedAt: Instant,
    val createdCount: Int,
    val updatedCount: Int,
    val removedCount: Int,
    val skippedCount: Int,
    val failureSummary: String? = null,
    /** 脱敏的按目标结果，例如“Google 已删除 N 条、复核剩余 0 条”。 */
    val detailSummary: String? = null,
)
