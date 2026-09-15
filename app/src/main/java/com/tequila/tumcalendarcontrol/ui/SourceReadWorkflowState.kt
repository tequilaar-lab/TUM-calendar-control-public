/*
 * 职责：保存私有来源的只读请求、当前运行目录与课程选择状态。
 * 输入／输出：接收读取结果、精确课次选择及浏览条件；输出 UI 可观察的只读目录状态和请求令牌。
 * 外部副作用：无；不持久化地址、不解析网络响应、不访问网络、权限或日历。
 * 不负责：课程投影、出席规则写入、输出候选、账户连接或日历写入。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.sync.RuleEditCommand
import com.tequila.tumcalendarcontrol.sync.RuleLedger
import com.tequila.tumcalendarcontrol.sync.RuleLedgerEditor
import com.tequila.tumcalendarcontrol.sync.SyncRule
import com.tequila.tumcalendarcontrol.sync.IcalReadOnlyImportResult
import com.tequila.tumcalendarcontrol.sync.IcalReadOnlyPreview
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceCourse
import com.tequila.tumcalendarcontrol.sync.SourceOccurrenceIdentity

/** 读取动作的纯状态机结果；调用者据此决定反馈文案或后台任务。 */
internal sealed interface SourceReadRequest {
    data object Ignored : SourceReadRequest
    data object NeedsReloadConfirmation : SourceReadRequest
    data class Rejected(val message: String) : SourceReadRequest
    data class Requested(val token: Long) : SourceReadRequest
}

/** 读取完成后只返回运行内资料；调用方负责将资料投影到课程界面。 */
internal sealed interface SourceReadResolution {
    data object Ignored : SourceReadResolution
    data class Success(val preview: IcalReadOnlyPreview) : SourceReadResolution
    data class Failure(val message: String) : SourceReadResolution
}

/** 批量规则计算结果不含来源正文；调用方负责把规则和课程投影合并到 UI 门面。 */
internal sealed interface SourceAttendanceRuleResult {
    data class Invalid(val message: String) : SourceAttendanceRuleResult
    data class Applied(
        val rules: RuleLedger,
        val sourceUids: Set<String>,
        val favoriteAction: SourceFavoriteAction,
    ) : SourceAttendanceRuleResult
}

internal class SourceReadWorkflowState {
    var readRequested by mutableStateOf(false)
        private set
    var readInProgress by mutableStateOf(false)
        private set
    var preview by mutableStateOf<IcalReadOnlyPreview?>(null)
        private set
    var previewError by mutableStateOf<String?>(null)
        private set
    var courses by mutableStateOf<List<SourceCourse>>(emptyList())
        private set
    var catalogLoaded by mutableStateOf(false)
        private set
    var occurrences by mutableStateOf<List<SourceCalendarOccurrence>>(emptyList())
        private set
    /** 选择只在本次运行存在；每一项都精确指向一个 UID + 原始实例键。 */
    var selectedOccurrences by mutableStateOf<Set<SourceOccurrenceIdentity>>(emptySet())
        private set
    var browserQuery by mutableStateOf("")
    var browserFilter by mutableStateOf(SourceCourseFilter.ALL)
    var reloadConfirmationPending by mutableStateOf(false)
        private set
    var pendingAttendance by mutableStateOf(AttendanceState.CHOOSE_TO_ATTEND)
    var pendingAttendanceScope by mutableStateOf(AttendanceEditScope.ALL_OCCURRENCES)
    var pendingEffectiveDate by mutableStateOf("")
    var pendingFavoriteAction by mutableStateOf(SourceFavoriteAction.KEEP)
    var readToken by mutableStateOf(0L)
        private set

    /** 有旧目录时要求用户确认；无旧目录时直接建立只读请求。 */
    fun requestRead(hasSubscription: Boolean): SourceReadRequest = when {
        !hasSubscription -> SourceReadRequest.Rejected("请先保存课程订阅地址；尚未读取课程。")
        readInProgress -> SourceReadRequest.Ignored
        courses.isNotEmpty() -> {
            reloadConfirmationPending = true
            SourceReadRequest.NeedsReloadConfirmation
        }
        else -> requestDirectRead(hasSubscription)
    }

    fun resolveReloadConfirmation(accepted: Boolean, hasSubscription: Boolean): SourceReadRequest {
        reloadConfirmationPending = false
        return if (accepted) requestDirectRead(hasSubscription) else SourceReadRequest.Ignored
    }

    /** 令牌单调递增；过期后台结果会在 resolveRead 中被忽略。 */
    fun requestDirectRead(hasSubscription: Boolean): SourceReadRequest = when {
        !hasSubscription -> SourceReadRequest.Rejected("请先保存课程订阅地址；尚未读取课程。")
        readInProgress -> SourceReadRequest.Ignored
        else -> beginRead()
    }

    /** 调用方已完成配置校验时建立新请求；不因已有目录要求再次确认。 */
    fun beginRead(): SourceReadRequest.Requested {
        preview = null
        previewError = null
        readToken += 1
        readInProgress = true
        readRequested = true
        return SourceReadRequest.Requested(readToken)
    }

    fun resolveRead(token: Long, result: IcalReadOnlyImportResult): SourceReadResolution {
        if (token != readToken || !readInProgress) return SourceReadResolution.Ignored
        readRequested = false
        readInProgress = false
        return when (result) {
            is IcalReadOnlyImportResult.Success -> {
                preview = result.preview
                courses = result.preview.courses
                occurrences = result.preview.occurrences
                catalogLoaded = true
                selectedOccurrences = emptySet()
                browserQuery = ""
                browserFilter = SourceCourseFilter.ALL
                previewError = null
                SourceReadResolution.Success(result.preview)
            }
            is IcalReadOnlyImportResult.Failure -> {
                preview = null
                previewError = result.message
                SourceReadResolution.Failure(result.message)
            }
        }
    }

    fun toggleSelection(identity: SourceOccurrenceIdentity) {
        selectedOccurrences = if (identity in selectedOccurrences) selectedOccurrences - identity else selectedOccurrences + identity
    }

    fun selectAllOccurrences() { selectedOccurrences = occurrences.filterNot(SourceCalendarOccurrence::isCancelled).map(SourceCalendarOccurrence::identity).toSet() }
    fun clearSelection() { selectedOccurrences = emptySet() }

    /** 打开批量出席编辑器前重置表单，避免上次范围和收藏动作泄漏到下一批。 */
    fun prepareAttendanceEditor(selection: Set<SourceOccurrenceIdentity>, firstState: AttendanceState): Boolean {
        if (selection.isEmpty()) return false
        pendingAttendance = firstState
        pendingAttendanceScope = AttendanceEditScope.ALL_OCCURRENCES
        pendingEffectiveDate = ""
        pendingFavoriteAction = SourceFavoriteAction.KEEP
        selectedOccurrences = selection
        return true
    }

    /** 将课次选择转换为稳定 UID 规则；“仅本次”严格保留每项的原始实例键。 */
    fun applyAttendanceRules(currentLedger: RuleLedger): SourceAttendanceRuleResult {
        val selected = occurrences.filter { it.identity in selectedOccurrences }
        if (selected.isEmpty()) return SourceAttendanceRuleResult.Invalid("课次列表已过期；重新读取后再修改。")
        val sourceUids = selected.map(SourceCalendarOccurrence::sourceUid).toSet()
        val command = when (pendingAttendanceScope) {
            AttendanceEditScope.ALL_OCCURRENCES -> RuleEditCommand.WholeCourse(sourceUids, pendingAttendance)
            AttendanceEditScope.CURRENT_AND_FUTURE -> RuleEditCommand.FromDate(sourceUids, java.time.LocalDate.now(), pendingAttendance)
            AttendanceEditScope.SPECIFIED_AND_FUTURE -> AttendanceEffectiveDateParser.parseOrNull(pendingEffectiveDate)
                ?.let { RuleEditCommand.FromDate(sourceUids, it, pendingAttendance) }
                ?: return SourceAttendanceRuleResult.Invalid("请输入 YYYY-MM-DD 格式的指定日期。")
            AttendanceEditScope.ONLY_THIS_OCCURRENCE -> RuleEditCommand.InstancesBySource(
                sourceUids = sourceUids,
                instanceKeysBySourceUid = selected.groupBy(SourceCalendarOccurrence::sourceUid)
                    .mapValues { (_, items) -> items.map(SourceCalendarOccurrence::instanceKey).toSet() },
                state = pendingAttendance,
            )
        }
        val existing = currentLedger.rules.associateBy(SyncRule::sourceUid)
        val ensuredRules = sourceUids.map { sourceUid ->
            existing[sourceUid] ?: SyncRule(sourceUid, hasConfirmedFixplatz = true, defaultState = AttendanceState.CHOOSE_TO_ATTEND)
        }
        return SourceAttendanceRuleResult.Applied(
            rules = currentLedger.copy(rules = RuleLedgerEditor.apply(currentLedger.rules.filterNot { it.sourceUid in sourceUids } + ensuredRules, command)),
            sourceUids = sourceUids,
            favoriteAction = pendingFavoriteAction,
        )
    }
}
