/*
 * 职责：实现课程总览、详情、搜索、收藏、更多操作和设置与同步的内容页面。
 * 输入／输出：输入 CalendarControlState；输出相应的 Compose-Miuix 页面并委托用户操作。
 * 外部副作用：无；同步按钮仅显示本地反馈，绝不访问真实账户或日历。
 * 不负责：出席状态编辑表单（由 AttendanceScreen 单独负责）或数据持久化。
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.BuildConfig
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Refresh
import com.tequila.tumcalendarcontrol.domain.Course
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppScreenContent(state: CalendarControlState, screen: AppScreen) {
    when (screen) {
        AppScreen.OVERVIEW -> OverviewScreen(state)
        AppScreen.DETAIL -> DetailScreen(state)
        AppScreen.ATTENDANCE -> AttendanceScreen(state)
        AppScreen.SEARCH -> SearchScreen(state)
        AppScreen.FAVORITES -> FavoritesScreen(state)
        AppScreen.MORE -> MoreScreen(state)
        AppScreen.SETTINGS -> SettingsScreen(state)
    }
}

@Composable
private fun OverviewScreen(state: CalendarControlState) {
    CourseList(
        courses = state.courses.filter { it.hasFixedPlus },
        heading = "已获 Fixplatz 的课程",
        onCourseClick = state::openCourse,
    )
}

@Composable
private fun DetailScreen(state: CalendarControlState) {
    val palette = LocalTumPalette.current
    val course = state.selectedCourse
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InformationCard("${course.title}（合成示例）", "${course.schedule} · ${course.location}")
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

@Composable
private fun SearchScreen(state: CalendarControlState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextField(
            value = state.searchQuery,
            onValueChange = { state.searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            label = "搜索已获 Fixplatz 的课程",
        )
        CourseList(
            courses = state.searchedCourses,
            heading = if (state.searchQuery.isBlank()) "可搜索的课程" else "搜索结果",
            onCourseClick = state::openCourse,
        )
    }
}

@Composable
private fun FavoritesScreen(state: CalendarControlState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InformationCard("已收藏课程", "收藏只影响显示，不改变出席状态")
        CourseList(state.favoriteCourses, "收藏列表", state::openCourse)
    }
}

/** 由完整覆盖层承载的更多操作内容；不直接创建覆盖层或处理返回动效。 */
@Composable
fun MoreScreen(state: CalendarControlState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InformationCard("筛选显示", "仅显示已获 Fixplatz 的课程")
        InformationCard("同步状态", "尚未连接任何真实日历；没有待写入的更改")
        InformationCard("应用设置", "日历来源、颜色与显示偏好")
        Button(onClick = { state.open(AppScreen.SETTINGS) }, modifier = Modifier.fillMaxWidth()) {
            Text("打开设置与同步")
        }
    }
}

@Composable
private fun SettingsScreen(state: CalendarControlState) {
    val palette = LocalTumPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InformationCard("同步账户", "Google 日历 · 未连接")
        InformationCard("日历来源", "TUM 私有 iCal 订阅地址 · 尚未配置")
        InformationCard("同步方向", "读取 TUM → 写入 Google 日历受控日程")
        InformationCard("写入范围", "仅管理本应用创建的日程；不改动其他日历")
        InformationCard("应用版本", "${BuildConfig.VERSION_NAME} · 构建号 ${BuildConfig.VERSION_CODE}")
        ThemeModeSelector(state)
        Spacer(Modifier.height(4.dp))
        Button(onClick = state::requestSync, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Refresh, "刷新同步")
            Text("刷新同步", modifier = Modifier.padding(start = 8.dp))
        }
        state.feedback?.let { Text(it, color = palette.onSurfaceVariant) }
    }
}

@Composable
private fun CourseList(courses: List<Course>, heading: String, onCourseClick: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text(heading, style = MiuixTheme.textStyles.title3) }
        if (courses.isEmpty()) {
            item { InformationCard("暂无课程", "当前筛选条件下没有可显示的已获 Fixplatz 课程") }
        } else {
            items(courses, key = { it.id }) { course ->
                CourseRow(course) { onCourseClick(course.id) }
            }
        }
    }
}

@Composable
private fun ThemeModeSelector(state: CalendarControlState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("外观", style = MiuixTheme.textStyles.title3)
        ThemeMode.entries.forEach { mode ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { state.themeMode = mode },
                insideMargin = androidx.compose.foundation.layout.PaddingValues(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.themeMode == mode,
                        onClick = { state.themeMode = mode },
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(mode.label, style = MiuixTheme.textStyles.body1)
                        Text(
                            if (mode == ThemeMode.SYSTEM) "仅跟随系统明暗，不使用壁纸取色" else "本次运行内立即生效",
                            color = LocalTumPalette.current.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
