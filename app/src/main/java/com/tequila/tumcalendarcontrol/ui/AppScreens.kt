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
        AppScreen.SOURCE_PREVIEW -> SourcePreviewScreen(state)
        AppScreen.SOURCE_ATTENDANCE -> SourceAttendanceScreen(state)
        AppScreen.MANUAL_EVENTS -> ManualEventsScreen(state)
        AppScreen.MANAGED_SYNC_PREVIEW -> ManagedSyncPreviewScreen(state)
    }
}

@Composable
private fun OverviewScreen(state: CalendarControlState) {
    CourseList(
        courses = state.courses.filter { it.hasFixedPlus && it.sourceStatus in state.visibleSourceStatuses },
        heading = "已获 Fixplatz 的课程",
        onCourseClick = state::openCourse,
    )
}

@Composable
private fun DetailScreen(state: CalendarControlState) {
    val palette = LocalTumPalette.current
    val course = state.selectedCourse
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InformationCard(course.displayTitle, "${course.displaySchedule} · ${course.displayLocation} · ${course.sourceStatus.label}")
        InformationCard("本机字段覆盖", if (course.localOverride == CourseLocalOverride()) "尚未覆盖；当前显示来源字段" else "已覆盖；来源原始字段未被改写")
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InformationCard("编辑本机课程资料", "名称、地点留空会显示来源字段；时间用星期与小时／分钟填写，不需要输入冒号。")
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
        }, modifier = Modifier.fillMaxWidth()) { Text("仅保存本机课程资料") }
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
        item { InformationCard("规则与范围", "来源课程默认蓝色；绿／蓝／灰规则会在确认同步前先生成双目标差异预览") }
        item { SourceStatusSelector(state) }
        item { Button(onClick = state::openManualEvents, modifier = Modifier.fillMaxWidth()) { Text("管理手动校园事项") } }
        item { Button(onClick = state::openSyncPreview, modifier = Modifier.fillMaxWidth()) { Text("查看离线同步预览") } }
        item {
            Button(
                onClick = state::requestSourcePreview,
                enabled = !state.sourceReadInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.sourceReadInProgress) "正在读取私有 iCal…" else "读取并预览私有 iCal") }
        }
        item { InformationCard("只读边界", "只有点按上方按钮才会以 HTTPS 读取一次；不会显示订阅地址或 ICS 原文。读取后仍需先预览、再确认写入。") }
        item { SyncDestinationSelector(state) }
        item { InformationCard("写入边界", "仅管理本应用创建且带所有权标记的目标日历；不改动主日历、订阅或其他应用日程") }
        if (state.syncDestinations.googleCalendarEnabled) {
            item {
                Button(onClick = state::requestGoogleAuthorization, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.hasGoogleAuthorization) "Google 已获本次运行授权" else "连接 Google 日历")
                }
            }
        }
        item { Button(onClick = state::requestManagedSyncPreview, modifier = Modifier.fillMaxWidth()) { Text("生成受控同步预览") } }
        item { InformationCard("应用版本", "${BuildConfig.VERSION_NAME} · 构建号 ${BuildConfig.VERSION_CODE}") }
        item { ThemeModeSelector(state) }
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Button(onClick = state::requestSync, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Refresh, "刷新同步")
                Text("刷新同步预览", modifier = Modifier.padding(start = 8.dp))
            }
        }
        state.feedback?.let { feedback ->
            item { Text(feedback, color = palette.onSurfaceVariant) }
        }
    }
}

@Composable
private fun SourceStatusSelector(state: CalendarControlState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("来源状态筛选", style = MiuixTheme.textStyles.title3)
        Text("只控制合成示例课程的显示；不会影响私有 iCal 读取或任何日历。", color = LocalTumPalette.current.onSurfaceVariant)
        TumEventStatus.entries.forEach { status ->
            Switch(checked = status in state.visibleSourceStatuses, onCheckedChange = { state.toggleSourceStatusVisible(status) })
            Text(status.label, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** 仅使用固定合成差异；此页没有确认写入按钮，防止被误认为真实同步。 */
@Composable
private fun SyncPreviewScreen(state: CalendarControlState) {
    val preview = state.offlinePreview
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { InformationCard("离线合成预览", "不会读取来源、申请权限或写入本机／Google 日历") }
        item { InformationCard("变更汇总", "新增 ${preview.createCount} · 更新 ${preview.updateCount} · 移除 ${preview.removeCount} · 保持 ${preview.keepCount} · 跳过 ${preview.skipCount}") }
        items(preview.changes) { change -> InformationCard(change.type.label, "合成身份：${change.identity.sourceUid}／${change.identity.instanceKey}") }
        items(preview.skipped) { skipped -> InformationCard("跳过", "${skipped.reason}（${skipped.sourceUid ?: "无 UID"}）") }
    }
}

/** 来源目录只在当前运行内显示；规则身份使用 UID，标题与地点绝不持久化。 */
@Composable
private fun SourcePreviewScreen(state: CalendarControlState) {
    val preview = state.sourcePreview
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { InformationCard("私有 iCal 课程目录", "已完成一次只读读取。课程默认蓝色“选择出席”；本页只保存规则，不写入、授权或同步。") }
        if (preview == null) {
            item { InformationCard("没有可显示的预览", "请返回设置重新执行用户主动读取；不会自动重试。") }
        } else {
            item { InformationCard("来源汇总", "解析到 ${preview.sourceEventCount} 条来源记录；可管理实例 ${preview.manageableOccurrenceCount} 条，聚合为 ${preview.courses.size} 门课程。") }
            item {
                val first = preview.firstManageableStart?.toString() ?: "无"
                val last = preview.lastManageableEnd?.toString() ?: "无"
                InformationCard("读取时间范围", "$first 至 $last")
            }
            item { InformationCard("跳过汇总", if (preview.skippedByReason.isEmpty()) "无跳过记录" else preview.skippedByReason.entries.joinToString("；") { "${it.key.label} ${it.value} 条" }) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = state::selectAllSourceCourses, modifier = Modifier.weight(1f)) { Text("全选课程") }
                    Button(onClick = state::clearSourceCourseSelection, modifier = Modifier.weight(1f)) { Text("清除选择") }
                }
            }
            if (state.selectedSourceCourseUids.isNotEmpty()) {
                item {
                    Button(onClick = { state.openSourceAttendanceEditor() }, modifier = Modifier.fillMaxWidth()) {
                        Text("设置已选 ${state.selectedSourceCourseUids.size} 门课程的状态")
                    }
                }
            }
            item {
                Button(onClick = state::requestManagedSyncPreview, modifier = Modifier.fillMaxWidth()) {
                    Text("生成受控同步预览")
                }
            }
            items(preview.courses, key = { it.sourceUid }) { course ->
                SourceCourseRow(
                    course = course,
                    selected = course.sourceUid in state.selectedSourceCourseUids,
                    attendance = state.sourceAttendanceFor(course.sourceUid),
                    onToggle = { state.toggleSourceCourseSelection(course.sourceUid) },
                    onEdit = { state.openSourceAttendanceEditor(setOf(course.sourceUid)) },
                )
            }
        }
    }
}

/** 每张卡片只负责选择或打开该来源 UID 的规则编辑器，不把来源资料写入规则库。 */
@Composable
private fun SourceCourseRow(
    course: com.tequila.tumcalendarcontrol.sync.SourceCourse,
    selected: Boolean,
    attendance: com.tequila.tumcalendarcontrol.domain.AttendanceState,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        insideMargin = androidx.compose.foundation.layout.PaddingValues(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = onToggle)
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(course.title, style = MiuixTheme.textStyles.title3)
                    Text(
                        "${attendance.colorLabel}色 · ${attendance.label} · ${course.occurrenceCount} 次实例",
                        color = LocalTumPalette.current.onSurfaceVariant,
                    )
                }
            }
            if (course.location.isNotBlank()) Text("地点：${course.location}", color = LocalTumPalette.current.onSurfaceVariant)
            Text("首个实例：${course.firstStart}", color = LocalTumPalette.current.onSurfaceVariant)
            Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("单独设置状态") }
        }
    }
}

/** 编辑私有配置的草稿；已保存的订阅地址绝不作为初始值回显。 */
@Composable
private fun SyncConfigurationScreen(state: CalendarControlState) {
    val current = state.privateSyncConfiguration
    var replacementSubscriptionUrl by remember { mutableStateOf("") }
    var replacingSubscription by remember { mutableStateOf(!current.hasTumSubscription) }
    var editingGoogleAdvanced by remember { mutableStateOf(false) }
    var googleClientId by remember(current.googleOAuthClientId) { mutableStateOf(current.googleOAuthClientId) }
    var calendarName by remember(current.googleCalendarName) { mutableStateOf(current.googleCalendarName) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InformationCard(
            "私有订阅地址",
            "当前：${current.maskedTumSubscription()}。地址不会回显；只有主动替换才会写入新值。",
        )
        if (replacingSubscription) PrivateAddressInput(
            value = replacementSubscriptionUrl,
            onValueChange = { replacementSubscriptionUrl = it },
        ) else Button(onClick = { replacingSubscription = true }, modifier = Modifier.fillMaxWidth()) { Text("替换私有 iCal 地址") }
        InformationCard("Google 日历", "Google 授权不是账户或密码。正式同步时会由系统选择账户，并仅请求本应用创建日历的最小权限；令牌不会保存。")
        if (editingGoogleAdvanced) TextField(
            value = googleClientId,
            onValueChange = { googleClientId = it },
            modifier = Modifier.fillMaxWidth(),
            label = "Google OAuth 客户端标识（高级，可留空）",
        ) else Button(onClick = { editingGoogleAdvanced = true }, modifier = Modifier.fillMaxWidth()) { Text("配置 Google 高级选项") }
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
                        onCheckedChange = { requested ->
                            if (destination == SyncDestination.LOCAL_CALENDAR && requested) state.requestLocalCalendarOutput()
                            else state.setSyncDestinationEnabled(destination, requested)
                        },
                    )
                }
            }
        }
        InformationCard("权限与写入", "本机开关开启时会请求读取／写入日历权限；同意后仍须先预览，再点按确认执行")
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
