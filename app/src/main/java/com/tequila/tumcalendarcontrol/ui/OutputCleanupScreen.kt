/*
 * 职责：让用户在清除应用已写入日程前选择全部、课程或日期范围，并进行二次确认。
 * 输入／输出：输入运行内来源目录、应用映射和用户选择；输出精确的清理范围意图。
 * 外部副作用：无；确认后的日历访问由根协调层处理。
 * 不负责：读取 iCal、保存来源正文、按标题识别日程、申请权限或直接删除日历事件。
 */
package com.tequila.tumcalendarcontrol.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.R
import com.tequila.tumcalendarcontrol.sync.ManagedEventCleanupScope
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import java.time.LocalDate
import java.time.ZoneId
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.window.Dialog

private val CleanupBerlinZone = ZoneId.of("Europe/Berlin")

private enum class OutputCleanupMode(val label: String, val description: String) {
    ALL("全部应用日程", "清除应用专用日历中仍带有本应用标记的所有日程。"),
    COURSES("按课程", "只清除选中课程的应用日程。"),
    DATES("按日期范围", "只清除所选日期范围内的应用日程。"),
}

private data class CleanupCourseGroup(
    val label: String,
    val sourceUids: Set<String>,
    val occurrenceCount: Int,
)

@Composable
internal fun OutputCleanupScreen(state: CalendarControlState) {
    var mode by remember { mutableStateOf(OutputCleanupMode.ALL) }
    var selectedSourceUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var pendingScope by remember { mutableStateOf<ManagedEventCleanupScope?>(null) }
    var localMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val darkTheme = state.themeMode.usesDarkTheme(isSystemInDarkTheme())
    val targets = state.managedOutputTargetsForCleanup
    var selectedTargets by remember(targets) { mutableStateOf(targets) }
    val catalogue = state.cleanupOccurrencesForTargets(selectedTargets)
    val sourceLoaded = catalogue.isNotEmpty()
    val courseGroups = cleanupCourseGroups(catalogue)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InformationCard(
            "清理已写入日程",
            "这是独立的删除操作。普通“写入日历”只新增或更新本次选择，不会清除以前写入的课程。",
        )
        InformationCard("选择清理位置", "仅清理勾选的日历；另一端的日程不会改变。")
        targets.forEach { target ->
            Button(onClick = { selectedTargets = if (target in selectedTargets) selectedTargets - target else selectedTargets + target }, modifier = Modifier.fillMaxWidth()) {
                Text("${if (target in selectedTargets) "✓ " else ""}${setOf(target).cleanupTargetLabel()}")
            }
        }
        Button(onClick = { state.requestCleanupCatalogRead(selectedTargets) }, enabled = !state.managedSyncInProgress && state.cleanupCatalogReadTargets.isEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(if (state.cleanupCatalogReadTargets.isEmpty()) "核对已写入日程（不删除）" else "正在核对日程…")
        }
        InformationCard("找不到以前的课程？", "点击上方核对，可从所选日历补齐旧版写入记录的课程和日期。只核对应用自己的记录，不读取其他日历。")
        OutputCleanupMode.entries.forEach { choice ->
            CleanupModeRow(mode = choice, selected = mode == choice) {
                mode = choice
                localMessage = null
            }
        }
        when (mode) {
            OutputCleanupMode.ALL -> InformationCard(
                "全部清理的保护",
                "只会检查本机／Google 的应用专用日历中带不可见应用标记的日程；不会触碰主日历、订阅日历或其他应用日程。",
            )
            OutputCleanupMode.COURSES -> {
                if (!sourceLoaded) {
                    InformationCard("还没有可识别的课程", "先点击“核对已写入日程”。若旧版连写入记录也已丢失，可单独选择“全部应用日程”并确认清理。")
                } else {
                    InformationCard("选择课程", "只清理选中的课程，不影响同时段的其他课程。")
                    courseGroups.forEach { group ->
                        val selected = group.sourceUids.all { it in selectedSourceUids }
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedSourceUids = if (selected) selectedSourceUids - group.sourceUids else selectedSourceUids + group.sourceUids
                            },
                            insideMargin = PaddingValues(14.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(group.label, style = MiuixTheme.textStyles.body1)
                                    Text("可识别 ${group.occurrenceCount} 个课次", color = LocalTumPalette.current.onSurfaceVariant)
                                }
                                Text(
                                    if (selected) "已选择" else "选择",
                                    color = if (selected) {
                                        LocalTumPalette.current.primary
                                    } else {
                                        LocalTumPalette.current.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }
            OutputCleanupMode.DATES -> {
                if (!sourceLoaded) {
                    InformationCard("还没有可识别的日期", "先点击“核对已写入日程”，再按日期选择。日历不会因核对而改变。")
                } else {
                    InformationCard("选择日期范围", "开始或结束可单独设置，留空的一端不限制。按已写入的时间选择，也包括已从课程列表移除的记录。")
                    CleanupDateField("开始日期", startDate) {
                        showCleanupDatePicker(context, startDate, darkTheme) { startDate = it }
                    }
                    CleanupDateField("结束日期", endDate) {
                        showCleanupDatePicker(context, endDate, darkTheme) { endDate = it }
                    }
                    Button(onClick = { startDate = null; endDate = null }, modifier = Modifier.fillMaxWidth()) { Text("清除日期范围") }
                }
            }
        }
        val selectedCount = when (mode) {
            OutputCleanupMode.ALL -> null
            OutputCleanupMode.COURSES -> state.cleanupIdentities(selectedSourceUids, null, null, selectedTargets).size
            OutputCleanupMode.DATES -> state.cleanupIdentities(null, startDate, endDate, selectedTargets).size
        }
        if (selectedCount != null) InformationCard("准备清理", "当前匹配 $selectedCount 个具体课次；此处尚未修改任何日历。")
        localMessage?.let { Text(it, color = LocalTumPalette.current.onSurfaceVariant) }
        Button(
            onClick = {
                val scope = when (mode) {
                    OutputCleanupMode.ALL -> ManagedEventCleanupScope.AllManagedEvents
                    OutputCleanupMode.COURSES -> state.selectedCleanupScope(selectedSourceUids, null, null, selectedTargets)
                    OutputCleanupMode.DATES -> state.selectedCleanupScope(null, startDate, endDate, selectedTargets)
                }
                if (scope is ManagedEventCleanupScope.SelectedIdentities && scope.identities.isEmpty()) {
                    localMessage = "请先选择至少一门课程，或设置能匹配课次的日期范围。"
                } else {
                    pendingScope = scope
                }
            },
            enabled = !state.managedSyncInProgress && state.cleanupCatalogReadTargets.isEmpty() && selectedTargets.isNotEmpty() && (selectedCount == null || selectedCount > 0) && (startDate == null || endDate == null || startDate!! <= endDate!!),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("下一步：确认清理") }
        state.feedback?.let { Text(it, color = LocalTumPalette.current.onSurfaceVariant) }
    }
    pendingScope?.let { scope ->
        CleanupConfirmationDialog(
            summary = when (scope) {
                ManagedEventCleanupScope.AllManagedEvents -> "将清理全部应用日程。"
                is ManagedEventCleanupScope.SelectedIdentities -> "将清理所选范围内的 ${scope.identities.size} 个具体课次。"
            },
            targets = selectedTargets.cleanupTargetLabel(),
            onDismiss = { pendingScope = null },
            onConfirm = {
                pendingScope = null
                state.requestManagedRollback(scope, selectedTargets)
            },
        )
    }
}

@Composable
private fun CleanupModeRow(mode: OutputCleanupMode, selected: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), insideMargin = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(mode.label, style = MiuixTheme.textStyles.body1)
                Text(mode.description, color = LocalTumPalette.current.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun CleanupDateField(label: String, date: LocalDate?, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), insideMargin = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MiuixTheme.textStyles.body1)
                Text(date?.toString() ?: "不限", color = LocalTumPalette.current.onSurfaceVariant)
            }
            Text("选择日期")
        }
    }
}

@Composable
private fun CleanupConfirmationDialog(summary: String, targets: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("确认清理日程", style = MiuixTheme.textStyles.title2)
                Text(summary)
                Text("将影响：$targets", color = LocalTumPalette.current.onSurfaceVariant)
                Text("此操作只删除本应用标记的日程，不能撤回。", color = LocalTumPalette.current.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("确认清理") }
                }
            }
        }
    }
}

private fun cleanupCourseGroups(occurrences: List<SourceCalendarOccurrence>): List<CleanupCourseGroup> = occurrences
    .asSequence()
    .groupBy { it.title.trim().replace(Regex("\\s+"), " ").ifBlank { "未命名课程" } }
    .map { (label, members) -> CleanupCourseGroup(label, members.map(SourceCalendarOccurrence::sourceUid).toSet(), members.size) }
    .sortedBy(CleanupCourseGroup::label)
    .toList()

private fun Set<com.tequila.tumcalendarcontrol.sync.OutputTarget>.cleanupTargetLabel(): String = buildList {
    if (com.tequila.tumcalendarcontrol.sync.OutputTarget.LOCAL_CALENDAR in this@cleanupTargetLabel) add("本机应用专用日历")
    if (com.tequila.tumcalendarcontrol.sync.OutputTarget.GOOGLE_CALENDAR in this@cleanupTargetLabel) add("Google 应用专用日历")
}.joinToString("、").ifBlank { "没有可用目标" }

private fun showCleanupDatePicker(context: android.content.Context, current: LocalDate?, darkTheme: Boolean, onPicked: (LocalDate) -> Unit) {
    val seed = current ?: LocalDate.now(CleanupBerlinZone)
    DatePickerDialog(
        context,
        if (darkTheme) R.style.Theme_TumCalendarControl_DatePicker_Dark else R.style.Theme_TumCalendarControl_DatePicker_Light,
        { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
        seed.year,
        seed.monthValue - 1,
        seed.dayOfMonth,
    ).show()
}
