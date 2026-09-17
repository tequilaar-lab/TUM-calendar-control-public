/*
 * 职责：提供来源课次的分组／按日期浏览、精确选择和折叠屏响应式布局。
 * 输入／输出：输入用户已选择的只读来源快照、课次选择状态与浏览条件；输出设置状态收藏或生成写入预览的意图。
 * 外部副作用：无；不读取 iCal、不持久化来源正文、不执行日历写入。
 * 不负责：按标题生成同步身份、修改解析结果或替代最后的写入确认。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.tequila.tumcalendarcontrol.sync.SourceOutputDateWindow
import java.time.ZoneId
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val SourceSelectionBarShape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
private val ExpandedSelectorBreakpoint = 720.dp
private val CourseBrowserZone = ZoneId.of("Europe/Berlin")

private enum class SourceBrowserMode(val label: String) { GROUPS("按课程分组"), DATES("按日期找课次") }

@Composable
fun SourceCourseSelectorScreen(state: CalendarControlState) {
    // 活动快照已在真正读取时按范围生成；旧快照不能再被“下次读取”草稿二次裁剪。
    val outputWindow = SourceOutputDateWindow()
    val groups = SourceCourseBrowserModel.groups(
        occurrences = state.currentSourceOccurrences,
        selectedOccurrences = state.selectedSourceOccurrences,
        query = state.sourceBrowserQuery,
        filter = state.sourceBrowserFilter,
        outputWindow = outputWindow,
        zoneId = CourseBrowserZone,
    )
    var mode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(SourceBrowserMode.GROUPS) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= ExpandedSelectorBreakpoint) {
            ExpandedSourceCourseSelector(state, outputWindow, groups, mode, onModeChange = { mode = it })
        } else {
            CompactSourceCourseSelector(state, outputWindow, groups, mode, onModeChange = { mode = it })
        }
    }
}

/** 展开态左栏只承载控制和悬浮操作；右栏始终完整显示且独立滚动课程／课次。 */
@Composable
private fun ExpandedSourceCourseSelector(
    state: CalendarControlState,
    outputWindow: SourceOutputDateWindow,
    groups: List<SourceCourseDisplayGroup>,
    mode: SourceBrowserMode,
    onModeChange: (SourceBrowserMode) -> Unit,
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(0.39f).fillMaxHeight()) {
            var floatingHeight by remember { mutableStateOf(184.dp) }
            val density = LocalDensity.current
            SourceSelectorControls(
                state = state,
                mode = mode,
                onModeChange = onModeChange,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp, 12.dp, 12.dp, floatingHeight + 12.dp),
            )
            SourceSelectionBottomBar(
                selectedCount = state.selectedSourceOccurrences.size,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { floatingHeight = with(density) { it.height.toDp() } }.padding(12.dp),
                onClear = state::clearSourceCourseSelection,
                onAttendance = state::openSourceAttendanceEditor,
                onWrite = state::prepareCalendarWrite,
            )
        }
        Box(Modifier.weight(0.61f).fillMaxHeight().padding(end = 12.dp).testTag("source-list-viewport")) {
            SourceCourseSelectionList(state, outputWindow, groups, mode, Modifier.fillMaxSize())
        }
    }
}

/** 折叠态保持单栏；列表独立滚动，操作卡片仅悬浮在底部而不铺满页面。 */
@Composable
private fun CompactSourceCourseSelector(
    state: CalendarControlState,
    outputWindow: SourceOutputDateWindow,
    groups: List<SourceCourseDisplayGroup>,
    mode: SourceBrowserMode,
    onModeChange: (SourceBrowserMode) -> Unit,
) {
    var floatingHeight by remember { mutableStateOf(184.dp) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceSelectorControls(state, mode, onModeChange, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))
            SourceCourseSelectionList(state, outputWindow, groups, mode, Modifier.weight(1f).padding(horizontal = 12.dp), floatingHeight + 12.dp)
        }
        SourceSelectionBottomBar(
            selectedCount = state.selectedSourceOccurrences.size,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().widthIn(max = 560.dp).onSizeChanged { floatingHeight = with(density) { it.height.toDp() } }.padding(12.dp),
            onClear = state::clearSourceCourseSelection,
            onAttendance = state::openSourceAttendanceEditor,
            onWrite = state::prepareCalendarWrite,
        )
    }
}

@Composable
private fun SourceSelectorControls(
    state: CalendarControlState,
    mode: SourceBrowserMode,
    onModeChange: (SourceBrowserMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InformationCard("选择具体课次", "勾选一个日期与时间，就只会处理这一个课次；不会自动选中同一门课的其他时间。")
        TextField(
            value = state.sourceBrowserQuery,
            onValueChange = state::updateSourceBrowserQuery,
            modifier = Modifier.fillMaxWidth(),
            label = "搜索课程或地点",
        )
        SourceFilterRow(state)
        SourceModeRow(mode = mode, onChange = onModeChange)
    }
}

@Composable
private fun SourceCourseSelectionList(
    state: CalendarControlState,
    outputWindow: SourceOutputDateWindow,
    groups: List<SourceCourseDisplayGroup>,
    mode: SourceBrowserMode,
    modifier: Modifier,
    bottomClearance: Dp = 16.dp,
) {
    when (mode) {
        SourceBrowserMode.GROUPS -> SourceGroupList(state, groups, modifier, bottomClearance)
        SourceBrowserMode.DATES -> SourceCourseDateBrowser(state, outputWindow, modifier, bottomClearance)
    }
}

@Composable
private fun SourceFilterRow(state: CalendarControlState) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        SourceCourseFilter.entries.forEach { filter ->
            val selected = state.sourceBrowserFilter == filter
            Button(onClick = { state.updateSourceBrowserFilter(filter) }, modifier = Modifier.weight(1f)) {
                Text(if (selected) "✓ ${filter.label}" else filter.label)
            }
        }
    }
}

@Composable
private fun SourceModeRow(mode: SourceBrowserMode, onChange: (SourceBrowserMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        SourceBrowserMode.entries.forEach { item ->
            Button(onClick = { onChange(item) }, modifier = Modifier.weight(1f)) {
                Text(if (item == mode) "✓ ${item.label}" else item.label)
            }
        }
    }
}

@Composable
private fun SourceGroupList(state: CalendarControlState, groups: List<SourceCourseDisplayGroup>, modifier: Modifier, bottomClearance: Dp) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = bottomClearance),
    ) {
        if (groups.isEmpty()) item { InformationCard("没有匹配课次", "尝试清除搜索或切换到“全部”。") }
        items(groups, key = { it.displayTitle + it.earliestStart }) { group -> SourceGroupRow(state, group) }
    }
}

@Composable
private fun SourceGroupRow(state: CalendarControlState, group: SourceCourseDisplayGroup) {
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(group.displayTitle) { mutableStateOf(group.selectedOccurrenceCount > 0) }
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(group.displayTitle, style = MiuixTheme.textStyles.title3)
                    Text(
                        "${group.sourceUidCount} 个来源条目 · 共 ${group.totalOccurrenceCount} 个课次 · 已选 ${group.selectedOccurrenceCount} 个课次",
                        color = LocalTumPalette.current.onSurfaceVariant,
                    )
                    Text(
                        "所选时间范围内有 ${group.inOutputRangeOccurrenceCount} 个课次",
                        color = LocalTumPalette.current.onSurfaceVariant,
                    )
                }
                Text(if (expanded) "收起" else "展开")
            }
            if (expanded) group.occurrences.forEach { occurrence ->
                SourceOccurrenceChoiceRow(
                    occurrence = occurrence,
                    selected = occurrence.identity in state.selectedSourceOccurrences,
                    onToggle = { state.toggleSourceOccurrenceSelection(occurrence.identity) },
                )
            }
        }
    }
}

/** 仅包住操作本身的悬浮卡；页面背景和右栏列表均不由它着色。 */
@Composable
private fun SourceSelectionBottomBar(
    selectedCount: Int,
    modifier: Modifier = Modifier,
    onClear: () -> Unit,
    onAttendance: () -> Unit,
    onWrite: () -> Unit,
) {
    val palette = LocalTumPalette.current
    Card(
        modifier = modifier
            .shadow(elevation = 16.dp, shape = SourceSelectionBarShape, clip = false)
            .clip(SourceSelectionBarShape)
            .background(palette.surfaceHighest)
            .border(width = 1.dp, color = palette.outline.copy(alpha = 0.52f), shape = SourceSelectionBarShape),
        insideMargin = PaddingValues(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("已选择 $selectedCount 个课次", style = MiuixTheme.textStyles.title3)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onClear, enabled = selectedCount > 0, modifier = Modifier.weight(1f)) { Text("取消选择") }
                Button(onClick = onAttendance, enabled = selectedCount > 0, modifier = Modifier.weight(1f)) { Text("状态和收藏") }
            }
            Button(onClick = onWrite, enabled = selectedCount > 0, modifier = Modifier.fillMaxWidth()) { Text("查看写入内容") }
            Text("这里只查看将写入的课次；最后确认前不会修改任何日历。", color = palette.onSurfaceVariant)
        }
    }
}
