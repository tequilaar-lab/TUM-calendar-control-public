/*
 * 职责：保存课程子类型的显示别名，并提供独立改名弹窗。
 * 输入／输出：视觉分组键和名称；输出共享显示别名。
 * 外部副作用：偏好类仅写应用私有存储，界面通过回调更新状态。
 * 不负责：修改源标题、课程成员、出席状态或日历。
 */
package com.tequila.tumcalendarcontrol.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalConfiguration
import top.yukonga.miuix.kmp.basic.*

internal class CourseTypeNamesPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("course_type_names", Context.MODE_PRIVATE)
    fun load(): Map<String, String> = preferences.all.mapNotNull { (key, value) ->
        (value as? String)?.takeIf(::validCourseTypeName)?.let { key to it }
    }.toMap()
    fun save(names: Map<String, String>) {
        val editor = preferences.edit().clear()
        names.filterValues(::validCourseTypeName).forEach { (key, value) -> editor.putString(key, value.trim()) }
        editor.apply()
    }
}

internal fun validCourseTypeName(value: String): Boolean = value.trim().isNotEmpty() && value.trim().length <= 30 && value.none { it.isISOControl() }
internal data class CourseTypeNames(val names: Map<String, String> = emptyMap(), val change: (String, String?) -> Unit = { _, _ -> })
internal val LocalCourseTypeNames = staticCompositionLocalOf { CourseTypeNames() }

@Composable
internal fun CourseTypeRenameButton(type: CourseTeachingTypeGroup) {
    val names = LocalCourseTypeNames.current
    var editing by remember { mutableStateOf(false) }
    var value by remember(type.label) { mutableStateOf(type.label) }
    val configuration = LocalConfiguration.current
    Button(onClick = { value = type.label; editing = true }) { Text("改名") }
    if (editing) Dialog(onDismissRequest = { editing = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.width((configuration.screenWidthDp - 48).coerceIn(200, 480).dp).height((configuration.screenHeightDp * 0.72f).coerceAtMost(480f).dp), insideMargin = PaddingValues(16.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("编辑子类型名称")
                Text("仅更改这门课的类型显示；首页、搜索和收藏一起更新，不改变日历内容。")
                TextField(value, { value = it }, Modifier.fillMaxWidth(), label = "子类型名称（最多 30 字）")
                Button(onClick = { names.change(type.key, value.trim()); editing = false }, enabled = validCourseTypeName(value), modifier = Modifier.fillMaxWidth()) { Text("保存名称") }
                Button(onClick = { names.change(type.key, null); editing = false }, modifier = Modifier.fillMaxWidth()) { Text("恢复自动名称") }
                Button(onClick = { editing = false }, modifier = Modifier.fillMaxWidth()) { Text("取消") }
            }
        }
    }
}
