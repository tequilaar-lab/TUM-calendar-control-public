/*
 * 职责：提供读取后课程的分组列表、按日期浏览、搜索、选择和固定下一步操作。
 * 输入／输出：输入只读运行内来源和选择状态；输出设置出席收藏或写入日历意图。
 * 外部副作用：无；不读取 iCal、不持久化来源正文、不执行日历写入。
 * 不负责：按标题合并同步身份、修改解析结果或替代写入确认。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.tequila.tumcalendarcontrol.sync.SourceCourse
import com.tequila.tumcalendarcontrol.sync.SourceOutputDateWindow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val CourseDisplayTime = DateTimeFormatter.ofPattern("M月d日 HH:mm")
private val CourseBrowserZone = ZoneId.of("Europe/Berlin")
private val SourceSelectionBarShape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)

private enum class SourceBrowserMode(val label: String) { GROUPS("分组列表"), DATES("按日期查看") }

@Composable
fun SourceCourseSelectorScreen(state: CalendarControlState) {
    val outputWindow = state.privateSyncConfiguration.sourceOutputWindow ?: SourceOutputDateWindow()
    val groups = SourceCourseBrowserModel.groups(
        courses = state.sourceCourses,
        occurrences = state.currentSourceOccurrences,
        selectedSourceUids = state.selectedSourceCourseUids,
        query = state.sourceBrowserQuery,
        filter = state.sourceBrowserFilter,
        outputWindow = outputWindow,
        zoneId = CourseBrowserZone,
    )
    var mode by remember { mutableStateOf(SourceBrowserMode.GROUPS) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InformationCard(
            "选择要管理的课程",
            "已读取 ${state.sourceCourses.size} 门课程。按课程名称或上课日期找到你想管理的课。",
        )
        TextField(
            value = state.sourceBrowserQuery,
            onValueChange = state::updateSourceBrowserQuery,
            modifier = Modifier.fillMaxWidth(),
            label = "搜索课程或地点",
        )
        SourceFilterRow(state)
        SourceModeRow(mode = mode, onChange = { mode = it })
        when (mode) {
            SourceBrowserMode.GROUPS -> SourceGroupList(state, groups, Modifier.weight(1f))
            SourceBrowserMode.DATES -> SourceCourseDateBrowser(state, outputWindow, Modifier.weight(1f))
        }
        SourceSelectionBottomBar(
            selectedCount = state.selectedSourceCourseUids.size,
            modifier = Modifier.fillMaxWidth(),
            onClear = state::clearSourceCourseSelection,
            onAttendance = state::openSourceAttendanceEditor,
            onWrite = state::prepareCalendarWrite,
        )
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
private fun SourceGroupList(state: CalendarControlState, groups: List<SourceCourseDisplayGroup>, modifier: Modifier) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        if (groups.isEmpty()) item { InformationCard("没有匹配课程", "尝试清除搜索或切换到“全部”。") }
        items(groups, key = { it.displayTitle + it.earliestStart }) { group -> SourceGroupRow(state, group) }
    }
}

@Composable
private fun SourceGroupRow(state: CalendarControlState, group: SourceCourseDisplayGroup) {
    var expanded by remember(group.displayTitle, group.earliestStart) { mutableStateOf(group.selectedCount > 0) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(group.displayTitle, style = MiuixTheme.textStyles.title3)
                    Text(
                        "${group.sourceUidCount} 个课程项 · ${group.totalOccurrenceCount} 次课程 · 已选 ${group.selectedCount}",
                        color = LocalTumPalette.current.onSurfaceVariant,
                    )
                    Text(
                        "范围内有日程：${group.inOutputRangeCount} 个来源项",
                        color = LocalTumPalette.current.onSurfaceVariant,
                    )
                }
                Text(if (expanded) "收起" else "展开")
            }
            if (expanded) group.courses.forEach { course ->
                SourceCourseChoiceRow(
                    course = course,
                    selected = course.sourceUid in state.selectedSourceCourseUids,
                    onToggle = { state.toggleSourceCourseSelection(course.sourceUid) },
                )
            }
        }
    }
}

@Composable
internal fun SourceCourseChoiceRow(course: SourceCourse, selected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onToggle)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(course.title, style = MiuixTheme.textStyles.body1)
            Text(
                "共 ${course.occurrenceCount} 次课程 · 首次上课：${course.firstStart.toCourseText()}",
                color = LocalTumPalette.current.onSurfaceVariant,
            )
            if (course.location.isNotBlank()) Text("地点：${course.location}", color = LocalTumPalette.current.onSurfaceVariant)
        }
    }
}

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
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .shadow(elevation = 16.dp, shape = SourceSelectionBarShape, clip = false)
            .clip(SourceSelectionBarShape)
            .background(palette.surfaceHighest)
            .border(width = 1.dp, color = palette.outline.copy(alpha = 0.52f), shape = SourceSelectionBarShape),
        insideMargin = PaddingValues(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("已选择 $selectedCount 门课程", style = MiuixTheme.textStyles.title3)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onClear, enabled = selectedCount > 0, modifier = Modifier.weight(1f)) { Text("清除") }
                Button(onClick = onAttendance, enabled = selectedCount > 0, modifier = Modifier.weight(1f)) { Text("设置出席与收藏") }
            }
            Button(onClick = onWrite, enabled = selectedCount > 0, modifier = Modifier.fillMaxWidth()) { Text("写入日历") }
            Text("未设置时会按蓝色“选择出席”处理；写入前仍会请你确认影响数量。", color = palette.onSurfaceVariant)
        }
    }
}

private fun Instant.toCourseText(): String = atZone(CourseBrowserZone).format(CourseDisplayTime)
