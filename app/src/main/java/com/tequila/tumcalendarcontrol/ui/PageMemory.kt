/*
 * 职责：保存非敏感页面偏好及稳定选择身份；与正式出席规则分离。
 * 输入／输出：页面记忆与 JSON；输出恢复值，损坏字段安全回退。
 * 外部副作用：仅应用私有偏好；不包含令牌、来源地址或执行确认。
 * 不负责：日历访问、规则存储或自动执行。
 */
package com.tequila.tumcalendarcontrol.ui

import android.content.Context
import com.tequila.tumcalendarcontrol.sync.SourceOccurrenceIdentity
import org.json.JSONArray
import org.json.JSONObject

data class PageMemory(
    val individual: Boolean = false,
    val scope: AttendanceEditScope = AttendanceEditScope.ALL_OCCURRENCES,
    val date: String = "",
    val selection: Set<SourceOccurrenceIdentity> = emptySet(),
    val cleanupMode: String = "COURSES",
    val cleanupCourses: Set<String> = emptySet(),
    val cleanupTargets: Set<String> = emptySet(),
    val cleanupStart: String = "",
    val cleanupEnd: String = "",
    val search: String = "",
    val sourceSearch: String = "",
    val sourceFilter: String = "ALL",
    val visibleSourceStatuses: Set<String> = emptySet(),
)

class PageMemoryPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("page_memory", Context.MODE_PRIVATE)
    fun load(): PageMemory = runCatching {
        val json = JSONObject(preferences.getString("data", "{}")!!)
        fun strings(key: String): Set<String> = json.optJSONArray(key)?.let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }.orEmpty()
        val selection = json.optJSONArray("selection")?.let { array -> (0 until array.length()).map {
            val item = array.getJSONArray(it)
            SourceOccurrenceIdentity(item.getString(0), item.getString(1))
        }.toSet() }.orEmpty()
        PageMemory(json.optBoolean("individual"), runCatching { AttendanceEditScope.valueOf(json.optString("scope")) }.getOrDefault(AttendanceEditScope.ALL_OCCURRENCES), json.optString("date"), selection,
            json.optString("cleanupMode", "COURSES"), strings("cleanupCourses"), strings("cleanupTargets"), json.optString("cleanupStart"), json.optString("cleanupEnd"),
            json.optString("search"), json.optString("sourceSearch"), json.optString("sourceFilter", "ALL"), strings("visibleSourceStatuses"))
    }.getOrDefault(PageMemory())

    fun save(memory: PageMemory) {
        val json = JSONObject().put("individual", memory.individual).put("scope", memory.scope.name).put("date", memory.date)
            .put("selection", JSONArray(memory.selection.map { JSONArray(listOf(it.sourceUid, it.instanceKey)) }))
            .put("cleanupMode", memory.cleanupMode).put("cleanupCourses", JSONArray(memory.cleanupCourses.toList()))
            .put("cleanupTargets", JSONArray(memory.cleanupTargets.toList())).put("cleanupStart", memory.cleanupStart).put("cleanupEnd", memory.cleanupEnd)
            .put("search", memory.search).put("sourceSearch", memory.sourceSearch).put("sourceFilter", memory.sourceFilter)
            .put("visibleSourceStatuses", JSONArray(memory.visibleSourceStatuses.toList()))
        check(preferences.edit().putString("data", json.toString()).commit()) { "页面选择未能保存" }
    }
}
