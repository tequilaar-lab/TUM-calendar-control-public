/*
 * 职责：渲染课程总览、详情、本机显示编辑、搜索、收藏及其共享列表项。
 * 输入／输出：输入 CalendarControlState 的课程投影与已有回调；输出可滚动 Compose-Miuix 课程页面。
 * 外部副作用：无；按钮只调用既有状态入口，不读取来源、不保存偏好、不写入日历。
 * 不负责：页面路由、出席规则编辑、设置／输出页面或 Android 外部协调。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.domain.Course
import com.tequila.tumcalendarcontrol.domain.CourseLocalOverride
import com.tequila.tumcalendarcontrol.domain.CourseMeetingTime
import com.tequila.tumcalendarcontrol.domain.CourseWeekday
import com.tequila.tumcalendarcontrol.domain.displayLocation
import com.tequila.tumcalendarcontrol.domain.displaySchedule
import com.tequila.tumcalendarcontrol.domain.displayTitle
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun OverviewScreen(state: CalendarControlState) {
    CourseList(
        courses = state.displayedCourses,
        heading = if (state.sourceCatalogLoaded) "已读取的课程" else "课程总览",
        onCourseClick = state::openCourse,
    )
}

@Composable
internal fun DetailScreen(state: CalendarControlState) {
    val palette = LocalTumPalette.current
    val course = state.selectedCourse
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InformationCard(course.displayTitle, "${course.displaySchedule} · ${course.displayLocation} · ${course.sourceStatus.label}")
        InformationCard("本机显示修改", if (course.localOverride == CourseLocalOverride()) "还没有修改；正在显示课程原来的信息" else "已修改本机显示；课程原来的信息没有改变")
        Button(onClick = state::openCourseEdit, modifier = Modifier.fillMaxWidth()) { Text("编辑名称、时间与地点") }
        InformationCard(
            "当前状态：${course.attendance.label}",
            "${course.attendance.colorLabel}色 · ${course.attendance.description}",
        )
        InformationCard("修改出席状态", "从本次或指定日期起生效；历史日程不改")
        Button(onClick = state::openAttendance, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Add, "修改出席状态")
            Text("修改出席状态", modifier = Modifier.padding(start = 8.dp))
        }
        Button(
            onClick = { state.toggleFavorite(course.id) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(if (course.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "切换收藏")
            Text(if (course.isFavorite) "取消收藏" else "收藏课程", modifier = Modifier.padding(start = 8.dp))
        }
        state.feedback?.let { Text(it, color = palette.onSurfaceVariant) }
    }
}

/** 只编辑本机覆盖值；留空即恢复显示来源字段，不会回写 TUM。 */
@Composable
internal fun CourseEditScreen(state: CalendarControlState) {
    val course = state.selectedCourse
    var title by remember(course.localOverride) { mutableStateOf(course.localOverride.title.orEmpty()) }
    var location by remember(course.localOverride) { mutableStateOf(course.localOverride.location.orEmpty()) }
    var weekday by remember(course.localOverride) { mutableStateOf(course.localOverride.meetingTime?.weekday ?: CourseWeekday.MONDAY) }
    var startHour by remember(course.localOverride) { mutableStateOf(course.localOverride.meetingTime?.startHour?.toString() ?: "10") }
    var startMinute by remember(course.localOverride) { mutableStateOf(course.localOverride.meetingTime?.startMinute?.toString() ?: "0") }
    var endHour by remember(course.localOverride) { mutableStateOf(course.localOverride.meetingTime?.endHour?.toString() ?: "12") }
    var endMinute by remember(course.localOverride) { mutableStateOf(course.localOverride.meetingTime?.endMinute?.toString() ?: "0") }
    var validation by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InformationCard("编辑课程显示", "这里只修改本机显示。名称和地点留空会继续使用课程原来的信息；时间只需填星期、小时和分钟。")
        TextField(title, { title = it }, Modifier.fillMaxWidth(), label = "名称（当前来源：${course.title}）")
        TextField(location, { location = it }, Modifier.fillMaxWidth(), label = "地点（当前来源：${course.location}）")
        Text("上课日", style = MiuixTheme.textStyles.title3)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CourseWeekday.entries.forEach { day ->
                Text(
                    day.label,
                    modifier = Modifier.clickable { weekday = day }.padding(6.dp),
                    color = if (weekday == day) LocalTumPalette.current.primary else LocalTumPalette.current.onSurfaceVariant,
                )
            }
        }
        Text("时间", style = MiuixTheme.textStyles.title3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(startHour, { startHour = it }, Modifier.weight(1f), label = "开始时")
            TextField(startMinute, { startMinute = it }, Modifier.weight(1f), label = "开始分")
            TextField(endHour, { endHour = it }, Modifier.weight(1f), label = "结束时")
            TextField(endMinute, { endMinute = it }, Modifier.weight(1f), label = "结束分")
        }
        validation?.let { Text(it, color = LocalTumPalette.current.onSurfaceVariant) }
        Button(
            onClick = {
                val time = runCatching {
                    CourseMeetingTime(weekday, startHour.toInt(), startMinute.toInt(), endHour.toInt(), endMinute.toInt())
                }.getOrElse {
                    validation = "请填写合法时间：小时 0–23、分钟 0–59，结束须晚于开始。"
                    return@Button
                }
                state.updateSelectedCourseOverride(
                    CourseLocalOverride(title.ifBlank { null }, location = location.ifBlank { null }, meetingTime = time),
                )
                state.handleBack()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存课程显示") }
    }
}

@Composable
internal fun SearchScreen(state: CalendarControlState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextField(
                value = state.searchQuery,
                onValueChange = { state.searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = "搜索课程",
            )
        }
        courseListItems(
            courses = state.searchedCourses,
            heading = if (state.searchQuery.isBlank()) "可搜索的课程" else "搜索结果",
            onCourseClick = state::openCourse,
        )
    }
}

@Composable
internal fun FavoritesScreen(state: CalendarControlState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { InformationCard("已收藏课程", "收藏只影响显示，不改变出席状态") }
        courseListItems(state.favoriteCourses, "收藏列表", state::openCourse)
    }
}

@Composable
private fun CourseList(courses: List<Course>, heading: String, onCourseClick: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        courseListItems(courses, heading, onCourseClick)
    }
}

/** 让总览、搜索和收藏共用同一批列表项，避免在可滚动页面中嵌套 LazyColumn。 */
private fun LazyListScope.courseListItems(courses: List<Course>, heading: String, onCourseClick: (String) -> Unit) {
    item { Text(heading, style = MiuixTheme.textStyles.title3) }
    if (courses.isEmpty()) {
        item { InformationCard("暂无课程", "请先读取课程或检查筛选条件。") }
    } else {
        items(courses, key = { it.id }) { course ->
            CourseRow(course) { onCourseClick(course.id) }
        }
    }
}
