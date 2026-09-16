/*
 * 职责：渲染课程总览、详情、本机显示编辑、搜索、收藏及其共享列表项。
 * 输入／输出：输入 CalendarControlState 的课程投影与已有回调；输出可滚动 Compose-Miuix 课程页面。
 * 外部副作用：无；按钮只调用既有状态入口，不读取来源、不保存偏好、不写入日历。
 * 不负责：页面路由、出席规则编辑、设置／输出页面或 Android 外部协调。
 */
package com.tequila.tumcalendarcontrol.ui
import com.tequila.tumcalendarcontrol.domain.browseKey

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.domain.Course
import com.tequila.tumcalendarcontrol.domain.CourseLocalOverride
import com.tequila.tumcalendarcontrol.domain.CourseMeetingTime
import com.tequila.tumcalendarcontrol.domain.CourseWeekday
import com.tequila.tumcalendarcontrol.domain.displayLocation
import com.tequila.tumcalendarcontrol.domain.displaySchedule
import com.tequila.tumcalendarcontrol.domain.displayTitle
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun OverviewScreen(state: CalendarControlState) {
    CourseList(
        courses = state.displayedCourses,
        heading = if (state.sourceCatalogLoaded) "已读取的课程" else "课程总览",
        browserStateKey = "overview",
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
    val sourceStart = course.startsAt?.atZone(java.time.ZoneId.of("Europe/Berlin"))
    val sourceEnd = course.endsAt?.atZone(java.time.ZoneId.of("Europe/Berlin"))
    val seedWeekday = course.localOverride.meetingTime?.weekday ?: sourceStart?.let { CourseWeekday.entries[it.dayOfWeek.value - 1] } ?: CourseWeekday.MONDAY
    val seedStartHour = (course.localOverride.meetingTime?.startHour ?: sourceStart?.hour ?: 10).toString()
    val seedStartMinute = (course.localOverride.meetingTime?.startMinute ?: sourceStart?.minute ?: 0).toString()
    val seedEndHour = (course.localOverride.meetingTime?.endHour ?: sourceEnd?.hour ?: 12).toString()
    val seedEndMinute = (course.localOverride.meetingTime?.endMinute ?: sourceEnd?.minute ?: 0).toString()
    var weekday by remember(course) { mutableStateOf(seedWeekday) }
    var startHour by remember(course) { mutableStateOf(seedStartHour) }
    var startMinute by remember(course) { mutableStateOf(seedStartMinute) }
    var endHour by remember(course) { mutableStateOf(seedEndHour) }
    var endMinute by remember(course) { mutableStateOf(seedEndMinute) }
    var validation by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InformationCard("编辑这门课程", "修改适用于这门课的所有课次，下次确认写入时更新日历，不修改学校来源。名称和地点留空沿用来源；未改动时间则保留各课次原时间。修改星期会移动到同一周。")
        TextField(title, { title = it }, Modifier.fillMaxWidth(), label = "名称（当前来源：${course.title}）")
        TextField(location, { location = it }, Modifier.fillMaxWidth(), label = "地点（当前来源：${course.location}）")
        Text("上课日", style = MiuixTheme.textStyles.title3)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                val timeChanged = weekday != seedWeekday || startHour != seedStartHour || startMinute != seedStartMinute || endHour != seedEndHour || endMinute != seedEndMinute
                val time = if (!timeChanged) course.localOverride.meetingTime else runCatching {
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
        ) { Text("保存课程修改") }
    }
}

@Composable
internal fun SearchScreen(state: CalendarControlState) {
    AdaptiveCourseBrowser(
        courses = state.searchedCourses,
        heading = if (state.searchQuery.isBlank()) "可搜索的课程" else "搜索结果",
        browserStateKey = "search",
        onCourseClick = state::openCourse,
        leadingItems = {
            item {
            TextField(
                value = state.searchQuery,
                onValueChange = { state.searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = "搜索课程",
            )
            }
        },
    )
}

@Composable
internal fun FavoritesScreen(state: CalendarControlState) {
    AdaptiveCourseBrowser(
        courses = state.favoriteCourses,
        heading = "收藏列表",
        browserStateKey = "favorites",
        onCourseClick = state::openCourse,
        leadingItems = { item { InformationCard("已收藏课程", "收藏只影响显示，不改变出席状态") } },
    )
}

@Composable
private fun CourseList(courses: List<Course>, heading: String, browserStateKey: String, onCourseClick: (String) -> Unit) {
    AdaptiveCourseBrowser(courses, heading, browserStateKey, onCourseClick)
}

/** 展开屏以列表＋辅助面板利用额外宽度；窄屏保持单一可滚动课程列表。 */
@Composable
private fun AdaptiveCourseBrowser(
    courses: List<Course>,
    heading: String,
    browserStateKey: String,
    onCourseClick: (String) -> Unit,
    leadingItems: LazyListScope.() -> Unit = {},
) {
    var expandedGroupKeys by androidx.compose.runtime.saveable.rememberSaveable(browserStateKey) { mutableStateOf<Set<String>>(emptySet()) }
    var expandedTeachingTypeKeys by androidx.compose.runtime.saveable.rememberSaveable(browserStateKey) { mutableStateOf<Set<String>>(emptySet()) }
    val groups = remember(courses) { CourseDisplayGroupModel.groups(courses) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val listContent: @Composable (Modifier) -> Unit = { modifier ->
            LazyColumn(
                modifier = modifier.testTag("course-browser-list"),
                contentPadding = PaddingValues(bottom = FloatingNavigationContentClearance),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                leadingItems()
                courseListItems(
                    groups = groups,
                    heading = heading,
                    expandedGroupKeys = expandedGroupKeys,
                    expandedTeachingTypeKeys = expandedTeachingTypeKeys,
                    onCourseClick = onCourseClick,
                    onGroupExpandedChange = { key ->
                        expandedGroupKeys = expandedGroupKeys.toggleCourseDisplayGroup(key)
                    },
                    onTeachingTypeExpandedChange = { key ->
                        expandedTeachingTypeKeys = expandedTeachingTypeKeys.toggleCourseTeachingTypeGroup(key)
                    },
                )
            }
        }
        if (maxWidth < 720.dp) {
            listContent(Modifier.fillMaxSize())
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listContent(Modifier.weight(0.62f).fillMaxHeight())
                CourseBrowserSupportPane(
                    groups = groups,
                    modifier = Modifier.weight(0.38f).fillMaxHeight(),
                    expandedGroupKeys = expandedGroupKeys,
                    expandedTeachingTypeKeys = expandedTeachingTypeKeys,
                    onGroupExpandedChange = { key -> expandedGroupKeys = expandedGroupKeys.toggleCourseDisplayGroup(key) },
                    onTeachingTypeExpandedChange = { key -> expandedTeachingTypeKeys = expandedTeachingTypeKeys.toggleCourseTeachingTypeGroup(key) },
                )
            }
        }
    }
}

@Composable
private fun CourseBrowserSupportPane(
    groups: List<CourseDisplayGroup>,
    modifier: Modifier,
    expandedGroupKeys: Set<String>,
    expandedTeachingTypeKeys: Set<String>,
    onGroupExpandedChange: (String) -> Unit,
    onTeachingTypeExpandedChange: (String) -> Unit,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(bottom = FloatingNavigationContentClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InformationCard("课程目录", "左侧是完整列表；这里是同一份“课程名 → 类型 → 课次”索引，可独立滚动和展开。")
        groups.forEach { group ->
            CourseDirectoryGroup(
                group = group,
                expanded = group.key in expandedGroupKeys,
                expandedTeachingTypeKeys = expandedTeachingTypeKeys,
                onGroupExpandedChange = onGroupExpandedChange,
                onTeachingTypeExpandedChange = onTeachingTypeExpandedChange,
            )
        }
        InformationCard("安全说明", "课程名称和类型只用于浏览分组。写入、更新和清理始终使用来源 UID 与具体课次身份。")
    }
}

/** 让总览、搜索和收藏共用同一批列表项，避免在可滚动页面中嵌套 LazyColumn。 */
private fun LazyListScope.courseListItems(
    groups: List<CourseDisplayGroup>,
    heading: String,
    expandedGroupKeys: Set<String>,
    expandedTeachingTypeKeys: Set<String>,
    onCourseClick: (String) -> Unit,
    onGroupExpandedChange: (String) -> Unit,
    onTeachingTypeExpandedChange: (String) -> Unit,
) {
    item { Text(heading, style = MiuixTheme.textStyles.title3) }
    if (groups.isEmpty()) {
        item { InformationCard("暂无课程", "请先读取课程或检查筛选条件。") }
    } else {
        groups.forEach { group ->
            item(key = "course-group:${group.key}") {
                CourseGroupCard(
                    group = group,
                    expanded = group.key in expandedGroupKeys,
                    onClick = { onGroupExpandedChange(group.key) },
                )
            }
            item(key = "course-group-rows:${group.key}") {
                AnimatedVisibility(
                    visible = group.key in expandedGroupKeys,
                    enter = expandVertically(animationSpec = tween(260)) + fadeIn(animationSpec = tween(180)),
                    exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(140)),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        group.teachingTypes.forEach { typeGroup ->
                            CourseTeachingTypeCard(
                                typeGroup = typeGroup,
                                expanded = typeGroup.key in expandedTeachingTypeKeys,
                                onClick = { onTeachingTypeExpandedChange(typeGroup.key) },
                            )
                            AnimatedVisibility(
                                visible = typeGroup.key in expandedTeachingTypeKeys,
                                enter = expandVertically(animationSpec = tween(230)) + fadeIn(animationSpec = tween(160)),
                                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(130)),
                            ) {
                                Column(
                                    modifier = Modifier.padding(start = 18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    typeGroup.courses.forEach { course ->
                                        CourseRow(course, onClick = { onCourseClick(course.browseKey) }, condensed = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 同名同课程基础名先折叠为浏览卡片；展开只改变当前页面状态，不会合并课程身份。 */
@Composable
private fun CourseGroupCard(group: CourseDisplayGroup, expanded: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    // 左箭头顺时针旋转会朝上；逆时针 90° 才是使用者预期的向下展开指示。
    val arrowRotation by animateFloatAsState(if (expanded) -90f else 0f, label = "课程组箭头旋转")
    Card(
        modifier = Modifier.fillMaxWidth().clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        ),
        insideMargin = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(group.displayTitle, style = MiuixTheme.textStyles.body1)
                Text(
                    if (expanded) "${group.teachingTypeCount} 种教学类型、${group.courses.size} 个课次已展开；点按可收起。" else "${group.teachingTypeCount} 种教学类型、${group.courses.size} 个课次；点按查看。",
                    color = LocalTumPalette.current.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                if (expanded) "收起课程" else "展开课程",
                modifier = Modifier.rotate(arrowRotation),
            )
        }
    }
}

/** 第二层仅显示可识别的教学类型；其展开才显露可独立操作的具体课次。 */
@Composable
private fun CourseTeachingTypeCard(typeGroup: CourseTeachingTypeGroup, expanded: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val arrowRotation by animateFloatAsState(if (expanded) -90f else 0f, label = "教学类型箭头旋转")
    Card(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp).clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        ),
        insideMargin = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("└", color = LocalTumPalette.current.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
            Column(Modifier.weight(1f)) {
                Text(typeGroup.label, style = MiuixTheme.textStyles.body2)
                Text("${typeGroup.courses.size} 个课次", color = LocalTumPalette.current.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                if (expanded) "收起${typeGroup.label}" else "展开${typeGroup.label}",
                modifier = Modifier.rotate(arrowRotation),
            )
        }
    }
}

internal fun directoryArrowRotation(expanded: Boolean): Float = if (expanded) 90f else 0f

/** 宽屏右列用紧凑树形索引复用左列的展开状态，不复制或改变任一课程数据。 */
@Composable
private fun CourseDirectoryGroup(
    group: CourseDisplayGroup,
    expanded: Boolean,
    expandedTeachingTypeKeys: Set<String>,
    onGroupExpandedChange: (String) -> Unit,
    onTeachingTypeExpandedChange: (String) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val arrowRotation by animateFloatAsState(directoryArrowRotation(expanded), label = "宽屏课程目录箭头")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(interactionSource, indication = null) { onGroupExpandedChange(group.key) }.padding(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, if (expanded) "收起课程目录" else "展开课程目录", modifier = Modifier.rotate(arrowRotation))
            Text(group.displayTitle, modifier = Modifier.padding(start = 8.dp).weight(1f), style = MiuixTheme.textStyles.body2)
            Text("${group.courses.size}", color = LocalTumPalette.current.onSurfaceVariant)
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(modifier = Modifier.padding(start = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                group.teachingTypes.forEach { typeGroup ->
                    val typeExpanded = typeGroup.key in expandedTeachingTypeKeys
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(interactionSource, indication = null) { onTeachingTypeExpandedChange(typeGroup.key) }.padding(6.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        val typeRotation by animateFloatAsState(directoryArrowRotation(typeExpanded), label = "目录类型箭头")
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, if (typeExpanded) "收起教学类型" else "展开教学类型", modifier = Modifier.rotate(typeRotation))
                        Text(typeGroup.label, modifier = Modifier.padding(start = 8.dp).weight(1f))
                        Text("${typeGroup.courses.size}", color = LocalTumPalette.current.onSurfaceVariant)
                    }
                    AnimatedVisibility(visible = typeExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(modifier = Modifier.padding(start = 22.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            typeGroup.courses.forEach { course ->
                                Text("• ${course.displaySchedule}", color = LocalTumPalette.current.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
