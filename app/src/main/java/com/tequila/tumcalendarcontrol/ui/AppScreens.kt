/*
 * 职责：实现课程总览、详情、搜索、收藏、更多操作和设置与同步的内容页面。
 * 输入／输出：输入 CalendarControlState；输出相应的 Compose-Miuix 页面并委托用户操作。
 * 外部副作用：无；同步按钮仅显示本地反馈，绝不访问真实账户或日历。
 * 不负责：出席状态编辑表单（由 AttendanceScreen 单独负责）或本机资料存储实现。
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.BuildConfig
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import com.tequila.tumcalendarcontrol.domain.Course
import com.tequila.tumcalendarcontrol.domain.CourseLocalOverride
import com.tequila.tumcalendarcontrol.domain.CourseMeetingTime
import com.tequila.tumcalendarcontrol.domain.CourseWeekday
import com.tequila.tumcalendarcontrol.domain.TumEventStatus
import com.tequila.tumcalendarcontrol.domain.displayLocation
import com.tequila.tumcalendarcontrol.domain.displaySchedule
import com.tequila.tumcalendarcontrol.domain.displayTitle
import com.tequila.tumcalendarcontrol.sync.PrivateSyncConfiguration
import com.tequila.tumcalendarcontrol.sync.SyncChangeType
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
        AppScreen.COURSE_EDIT -> CourseEditScreen(state)
        AppScreen.SEARCH -> SearchScreen(state)
        AppScreen.FAVORITES -> FavoritesScreen(state)
        AppScreen.MORE -> MoreScreen(state)
        AppScreen.SETTINGS -> SettingsScreen(state)
        AppScreen.PROFILE -> ProfileScreen(state)
        AppScreen.SYNC_CONFIGURATION -> SyncConfigurationScreen(state)
        AppScreen.SYNC_PREVIEW -> SyncPreviewScreen(state)
        AppScreen.SOURCE_RANGE -> SourceRangeScreen(state)
        AppScreen.SOURCE_PREVIEW -> SourceCourseSelectorScreen(state)
        AppScreen.SOURCE_ATTENDANCE -> SourceAttendanceScreen(state)
        AppScreen.MANUAL_EVENTS -> ManualEventsScreen(state)
        AppScreen.MANAGED_SYNC_PREVIEW -> ManagedSyncPreviewScreen(state)
        AppScreen.BACKGROUND_CHECK -> BackgroundCheckScreen(state)
        AppScreen.RULE_BACKUP -> RuleBackupScreen(state)
    }
}

@Composable
private fun OverviewScreen(state: CalendarControlState) {
    CourseList(
        courses = state.displayedCourses,
        heading = if (state.sourceCatalogLoaded) "已读取的课程" else "课程总览",
        onCourseClick = state::openCourse,
    )
}

@Composable
private fun DetailScreen(state: CalendarControlState) {
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
private fun CourseEditScreen(state: CalendarControlState) {
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
            CourseWeekday.entries.forEach { day -> Text(day.label, modifier = Modifier.clickable { weekday = day }.padding(6.dp), color = if (weekday == day) LocalTumPalette.current.primary else LocalTumPalette.current.onSurfaceVariant) }
        }
        Text("时间", style = MiuixTheme.textStyles.title3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(startHour, { startHour = it }, Modifier.weight(1f), label = "开始时")
            TextField(startMinute, { startMinute = it }, Modifier.weight(1f), label = "开始分")
            TextField(endHour, { endHour = it }, Modifier.weight(1f), label = "结束时")
            TextField(endMinute, { endMinute = it }, Modifier.weight(1f), label = "结束分")
        }
        validation?.let { Text(it, color = LocalTumPalette.current.onSurfaceVariant) }
        Button(onClick = {
            val time = runCatching { CourseMeetingTime(weekday, startHour.toInt(), startMinute.toInt(), endHour.toInt(), endMinute.toInt()) }.getOrElse { validation = "请填写合法时间：小时 0–23、分钟 0–59，结束须晚于开始。"; return@Button }
            state.updateSelectedCourseOverride(CourseLocalOverride(title.ifBlank { null }, location = location.ifBlank { null }, meetingTime = time)); state.handleBack()
        }, modifier = Modifier.fillMaxWidth()) { Text("保存课程显示") }
    }
}

@Composable
private fun SearchScreen(state: CalendarControlState) {
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
private fun FavoritesScreen(state: CalendarControlState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { InformationCard("已收藏课程", "收藏只影响显示，不改变出席状态") }
        courseListItems(state.favoriteCourses, "收藏列表", state::openCourse)
    }
}

/** 由完整覆盖层承载的更多操作内容；不直接创建覆盖层或处理返回动效。 */
@Composable
fun MoreScreen(state: CalendarControlState) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InformationCard("当前课程", if (state.sourceCatalogLoaded) "正在显示本次读取的课程" else "当前显示示例课程")
        InformationCard("日历状态", "还没有连接日历，也没有等待写入的日程。")
        InformationCard("应用设置", "课程、日历和外观设置")
        Button(onClick = { state.open(AppScreen.SETTINGS) }, modifier = Modifier.fillMaxWidth()) {
            Text("打开设置")
        }
    }
}

@Composable
private fun SettingsScreen(state: CalendarControlState) {
    val palette = LocalTumPalette.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { InformationCard("使用步骤", "1 选择时间 · 2 读取并选择课程 · 3 设置出席和收藏 · 4 确认写入。返回不会清空已选课程。") }
        item { Button(onClick = state::openSourceRange, modifier = Modifier.fillMaxWidth()) { Text("读取课程") } }
        item {
            val summary = state.privateSyncConfiguration.sourceOutputWindow?.let {
                if (it.startsOn == null && it.endsOn == null) "当前不限时间；点按可使用日历选择范围。" else "当前已设置范围；点按可查看、修改或清除。"
            } ?: "当前范围格式无效；请点按修正。"
            InformationCard("读取时间", summary)
        }
        item {
            val courseSummary = when {
                state.sourcePreview == null -> "还没有读取课程。先选择时间并读取课程。"
                else -> "本次已读取 ${state.sourceCourses.size} 门课程；已选择 ${state.selectedSourceCourseUids.size} 门。"
            }
            InformationCard("已读取课程", courseSummary)
        }
        item { Button(onClick = state::openSourceCourseSelector, modifier = Modifier.fillMaxWidth()) { Text("选择课程") } }
        item { Button(onClick = state::openSyncConfiguration, modifier = Modifier.fillMaxWidth()) { Text("日历连接") } }
        item { InformationCard("出席设置", "新课程默认蓝色“选择出席”；绿色为一定出席，灰色从指定日期起不去。") }
        item { Button(onClick = state::openManualEvents, modifier = Modifier.fillMaxWidth()) { Text("添加校园事项") } }
        item { Button(onClick = state::openBackgroundCheck, modifier = Modifier.fillMaxWidth()) { Text("检查课程更新") } }
        item { Button(onClick = state::openRuleBackup, modifier = Modifier.fillMaxWidth()) { Text("备份课程设置") } }
        item { SyncDestinationSelector(state) }
        item { InformationCard("写入到哪里", "应用只会写入自己创建的日程。你的主日历、订阅日历和其他应用日程不会改变。") }
        if (state.syncDestinations.googleCalendarEnabled) {
            item { GoogleAuthorizationPanel(state) }
        }
        item { Button(onClick = state::prepareCalendarWrite, modifier = Modifier.fillMaxWidth()) { Text("写入日历") } }
        if (state.ruleLedger.mappings.isNotEmpty()) {
            item { Button(onClick = state::openMappedOutputRollback, modifier = Modifier.fillMaxWidth()) { Text("清除或查看已写入日程") } }
        }
        item { InformationCard("应用版本", "${BuildConfig.VERSION_NAME} · 构建号 ${BuildConfig.VERSION_CODE}") }
        item { ThemeModeSelector(state) }
        item { Spacer(Modifier.height(4.dp)) }
        state.feedback?.let { feedback ->
            item { Text(feedback, color = palette.onSurfaceVariant) }
        }
    }
}

@Composable
private fun SourceStatusSelector(state: CalendarControlState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("课程显示", style = MiuixTheme.textStyles.title3)
        if (state.sourceCatalogLoaded) {
            Text("本次读取的课程会全部显示，不会按学校状态隐藏。", color = LocalTumPalette.current.onSurfaceVariant)
        } else {
            Text("这里只控制示例课程显示，不会改变课程地址或任何日历。", color = LocalTumPalette.current.onSurfaceVariant)
            TumEventStatus.entries.forEach { status ->
                Switch(checked = status in state.visibleSourceStatuses, onCheckedChange = { state.toggleSourceStatusVisible(status) })
                Text(status.label, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** 仅使用固定合成差异；此页没有确认写入按钮，防止被误认为真实同步。 */
@Composable
private fun SyncPreviewScreen(state: CalendarControlState) {
    val preview = state.offlinePreview
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { InformationCard("示例日程结果", "这是示例数据，不会读取课程、申请权限或写入日历。") }
        item { InformationCard("变更汇总", "新增 ${preview.createCount} · 更新 ${preview.updateCount} · 移除 ${preview.removeCount} · 保持 ${preview.keepCount} · 跳过 ${preview.skipCount}") }
        items(preview.changes) { change -> InformationCard(change.type.label, "这条是示例数据，不会写入日历。") }
        items(preview.skipped) { skipped -> InformationCard("不写入", "这条示例不会写入日历。") }
    }
}

/** 编辑私有配置的草稿；已保存的订阅地址绝不作为初始值回显。 */
@Composable
private fun SyncConfigurationScreen(state: CalendarControlState) {
    val current = state.privateSyncConfiguration
    var replacementSubscriptionUrl by remember { mutableStateOf("") }
    var replacingSubscription by remember { mutableStateOf(!current.hasTumSubscription) }
    var calendarName by remember(current.googleCalendarName) { mutableStateOf(current.googleCalendarName) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InformationCard(
            "课程订阅地址",
            "已保存：${current.maskedTumSubscription()}。为保护隐私，地址不会显示；只有重新填写后才会替换。",
        )
        if (replacingSubscription) PrivateAddressInput(
            value = replacementSubscriptionUrl,
            onValueChange = { replacementSubscriptionUrl = it },
        ) else Button(onClick = { replacingSubscription = true }, modifier = Modifier.fillMaxWidth()) { Text("更换课程订阅地址") }
        InformationCard("读取时间", "在“选择读取时间”页面设置；使用日历选择，无需手动输入日期。")
        InformationCard("连接 Google 日历", "系统会让你选择 Google 账户。应用只请求创建和管理自己日历所需的权限；不会保存登录信息。")
        InformationCard("首次连接说明", "首次使用前，需要在 Google Cloud 为此应用完成一次配置。应用不会显示或保存客户端编号。")
        TextField(
            value = calendarName,
            onValueChange = { calendarName = it },
            modifier = Modifier.fillMaxWidth(),
            label = "Google 日历名称",
        )
        validationMessage?.let { Text(it, color = LocalTumPalette.current.onSurfaceVariant) }
        Button(
            onClick = {
                val updated = current.copy(
                    tumSubscriptionUrl = replacementSubscriptionUrl.trim().ifBlank { current.tumSubscriptionUrl },
                    googleOAuthClientId = "",
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
        ) { Text("保存到这台设备") }
    }
}

/** Google 状态卡始终紧邻连接操作，避免长设置页将成功或失败反馈藏到屏幕外。 */
@Composable
private fun GoogleAuthorizationPanel(state: CalendarControlState) {
    InformationCard(state.googleAuthorizationStatus.title, state.googleAuthorizationDetail)
    Button(onClick = state::requestGoogleAuthorization, modifier = Modifier.fillMaxWidth()) {
        val label = when (state.googleAuthorizationStatus) {
            GoogleAuthorizationStatus.NOT_CONNECTED -> "连接 Google 日历"
            GoogleAuthorizationStatus.AUTHORIZING -> "正在等待 Google 授权"
            GoogleAuthorizationStatus.AUTHORIZED -> "重新授权 Google 日历"
            GoogleAuthorizationStatus.FAILED -> "重试 Google 授权"
        }
        Text(label)
    }
}

/** 两个输出目标可独立启用；开关仅保存未来许可，不能在未配置时触发同步。 */
@Composable
private fun SyncDestinationSelector(state: CalendarControlState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("写入位置", style = MiuixTheme.textStyles.title3)
        Text("课程和出席设置可分别写入本机日历和 Google 日历。", color = LocalTumPalette.current.onSurfaceVariant)
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
                        onCheckedChange = { requested ->
                            if (destination == SyncDestination.LOCAL_CALENDAR && requested) state.requestLocalCalendarOutput()
                            else state.setSyncDestinationEnabled(destination, requested)
                        },
                    )
                }
            }
        }
        InformationCard("日历权限", "打开本机日历后，系统会请求日历权限。允许后仍要查看数量并确认写入。")
    }
}

/** 资料编辑只准备草稿；点击保存后才更新状态，再由界面根部写入应用私有偏好。 */
@Composable
private fun ProfileScreen(state: CalendarControlState) {
    var draftName by remember(state.profile) { mutableStateOf(state.profile.displayName) }
    var draftTone by remember(state.profile) { mutableStateOf(state.profile.avatarTone) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
