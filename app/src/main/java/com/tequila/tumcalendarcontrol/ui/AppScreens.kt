/*
 * 职责：实现课程总览、详情、搜索、收藏、更多操作和设置与同步的内容页面。
 * 输入／输出：输入 CalendarControlState；输出相应的 Compose-Miuix 页面并委托用户操作。
 * 外部副作用：无；同步按钮仅显示本地反馈，绝不访问真实账户或日历。
 * 不负责：出席状态编辑表单（由 AttendanceScreen 单独负责）或本机资料存储实现。
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Refresh
import com.tequila.tumcalendarcontrol.domain.Course
import com.tequila.tumcalendarcontrol.sync.PrivateSyncConfiguration
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Switch
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
        AppScreen.PROFILE -> ProfileScreen(state)
        AppScreen.SYNC_CONFIGURATION -> SyncConfigurationScreen(state)
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
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            InformationCard(
                "日历来源",
                "TUM 私有 iCal · ${state.privateSyncConfiguration.maskedTumSubscription()}",
            )
        }
        item {
            Button(onClick = state::openSyncConfiguration, modifier = Modifier.fillMaxWidth()) {
                Text("配置来源与 Google 连接")
            }
        }
        item { InformationCard("规则与范围", "只处理已获 Fixplatz 的日程；绿／蓝／灰规则由本应用统一决定") }
        item { SyncDestinationSelector(state) }
        item { InformationCard("写入边界", "仅管理本应用创建的目标日历；不改动用户、订阅或其他应用的日程") }
        item { InformationCard("应用版本", "${BuildConfig.VERSION_NAME} · 构建号 ${BuildConfig.VERSION_CODE}") }
        item { ThemeModeSelector(state) }
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Button(onClick = state::requestSync, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Refresh, "刷新同步")
                Text("刷新同步", modifier = Modifier.padding(start = 8.dp))
            }
        }
        state.feedback?.let { feedback ->
            item { Text(feedback, color = palette.onSurfaceVariant) }
        }
    }
}

/** 编辑私有配置的草稿；已保存的订阅地址绝不作为初始值回显。 */
@Composable
private fun SyncConfigurationScreen(state: CalendarControlState) {
    val current = state.privateSyncConfiguration
    var replacementSubscriptionUrl by remember { mutableStateOf("") }
    var googleClientId by remember(current.googleOAuthClientId) { mutableStateOf(current.googleOAuthClientId) }
    var calendarName by remember(current.googleCalendarName) { mutableStateOf(current.googleCalendarName) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InformationCard(
            "私有订阅地址",
            "当前：${current.maskedTumSubscription()}。旧地址不会回显；只有输入新地址才会替换。",
        )
        TextField(
            value = replacementSubscriptionUrl,
            onValueChange = { replacementSubscriptionUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = "替换 TUM 私有 iCal 地址（可留空）",
        )
        InformationCard("Google 连接", "客户端标识只用于未来由你确认的 OAuth 授权；保存配置不会联网。")
        TextField(
            value = googleClientId,
            onValueChange = { googleClientId = it },
            modifier = Modifier.fillMaxWidth(),
            label = "Google OAuth 客户端标识（可留空）",
        )
        TextField(
            value = calendarName,
            onValueChange = { calendarName = it },
            modifier = Modifier.fillMaxWidth(),
            label = "应用专用 Google 次级日历名称",
        )
        validationMessage?.let { Text(it, color = LocalTumPalette.current.onSurfaceVariant) }
        Button(
            onClick = {
                val updated = PrivateSyncConfiguration(
                    tumSubscriptionUrl = replacementSubscriptionUrl.trim().ifBlank { current.tumSubscriptionUrl },
                    googleOAuthClientId = googleClientId,
                    googleCalendarName = calendarName,
                )
                val problems = updated.validate()
                if (problems.isEmpty()) {
                    state.updatePrivateSyncConfiguration(updated)
                    state.handleBack()
                } else {
                    validationMessage = problems.joinToString("；") { it.message }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("仅保存本机配置") }
    }
}

/** 两个输出目标可独立启用；开关仅保存未来许可，不能在未配置时触发同步。 */
@Composable
private fun SyncDestinationSelector(state: CalendarControlState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("同步目标", style = MiuixTheme.textStyles.title3)
        Text("两个目标共用同一份 TUM 输入和出席规则，可分别开关。", color = LocalTumPalette.current.onSurfaceVariant)
        SyncDestination.entries.forEach { destination ->
            val enabled = state.syncDestinations.isEnabled(destination)
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = androidx.compose.foundation.layout.PaddingValues(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(destination.title, style = MiuixTheme.textStyles.body1)
                        Text(
                            if (enabled) destination.enabledSummary else destination.disabledSummary,
                            color = LocalTumPalette.current.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { state.setSyncDestinationEnabled(destination, it) },
                    )
                }
            }
        }
        InformationCard("当前阶段", "开关只保存在本机；尚未连接账户、申请权限或发送任何日程数据")
    }
}

/** 资料编辑只准备草稿；点击保存后才更新状态，再由界面根部写入应用私有偏好。 */
@Composable
private fun ProfileScreen(state: CalendarControlState) {
    var draftName by remember(state.profile) { mutableStateOf(state.profile.displayName) }
    var draftTone by remember(state.profile) { mutableStateOf(state.profile.avatarTone) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InformationCard("本机个人资料", "昵称和头像颜色只保存在这台设备的应用内，不是 Google 或 TUM 账户")
        TextField(
            value = draftName,
            onValueChange = { draftName = it },
            modifier = Modifier.fillMaxWidth(),
            label = "显示昵称",
        )
        Text("头像颜色", style = MiuixTheme.textStyles.title3)
        AvatarTone.entries.forEach { tone ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { draftTone = tone },
                insideMargin = androidx.compose.foundation.layout.PaddingValues(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(UserProfile(draftName, tone), modifier = Modifier.size(40.dp))
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(tone.label, style = MiuixTheme.textStyles.body1)
                        Text(
                            if (tone == draftTone) "当前选择" else "点按选择",
                            color = LocalTumPalette.current.onSurfaceVariant,
                        )
                    }
                    RadioButton(
                        selected = tone == draftTone,
                        onClick = { draftTone = tone },
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
        Button(
            onClick = {
                state.updateProfile(UserProfile(draftName, draftTone))
                state.handleBack()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存个人资料") }
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
