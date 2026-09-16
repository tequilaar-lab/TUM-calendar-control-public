/*
 * 职责：把课程组织为“课程名 → 教学类型 → 独立课次”的纯视觉目录。
 * 输入／输出：输入独立 Course 列表；输出保留每条 Course 身份的三级浏览分组。
 * 外部副作用：无；不保存分组、不修改课程、规则、收藏或日历。
 * 不负责：以标题或教学类型作为同步身份、合并 UID／实例、筛选来源或处理用户点击。
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.domain.Course
import com.tequila.tumcalendarcontrol.domain.displayTitle
import java.util.Locale

/** 顶层只按去除教学类型后的显示课程名归类；成员身份仍由 Course.id 保留。 */
internal data class CourseDisplayGroup(
    val key: String,
    val displayTitle: String,
    val teachingTypes: List<CourseTeachingTypeGroup>,
) {
    val courses: List<Course> get() = teachingTypes.flatMap(CourseTeachingTypeGroup::courses)
    val teachingTypeCount: Int get() = teachingTypes.size
}

/** 类型行是目录节点，不是同步类型或日历颜色；未知后缀只安全归入“其他课次”。 */
internal data class CourseTeachingTypeGroup(
    val key: String,
    val label: String,
    val courses: List<Course>,
)

internal object CourseDisplayGroupModel {
    /** 空白与大小写差异不会拆散同一视觉课程名；排序仅改善浏览，不参与任何业务规则。 */
    fun groups(courses: List<Course>): List<CourseDisplayGroup> = courses
        .map { course -> course to parseTitle(course.displayTitle) }
        .groupBy { (_, parsed) -> normalizedKey(parsed.courseName) }
        .map { (key, members) ->
            val title = members.first().second.courseName.ifBlank { "未命名课程" }
            val types = members
                .groupBy { (_, parsed) -> parsed.type }
                .map { (type, typeMembers) ->
                    CourseTeachingTypeGroup(
                        key = "$key:${normalizedKey(type.key)}",
                        label = type.label,
                        courses = typeMembers.map { it.first }.sortedWith(compareBy<Course> { it.startsAt }.thenBy { it.id }),
                    )
                }
                .sortedWith(compareBy<CourseTeachingTypeGroup> { it.label == TitleRules.OTHER_TYPE.label }.thenBy(CourseTeachingTypeGroup::label))
            CourseDisplayGroup(key = key, displayTitle = title, teachingTypes = types)
        }
        .sortedWith(compareBy<CourseDisplayGroup> { it.displayTitle.lowercase(Locale.ROOT) }.thenBy(CourseDisplayGroup::key))

    /** 给更新说明复用与目录相同的“课程名忽略教学类型”归类规则；不用于同步身份。 */
    fun titleGroupingKey(title: String): String = normalizedKey(parseTitle(title).courseName)

    /** 只识别标题末尾的标准 TUM 教学类型，避免把普通文字或地点错误当成类型。 */
    private fun parseTitle(rawTitle: String): ParsedCourseTitle {
        val title = rawTitle.trim().replace(Regex("\\s+"), " ").ifBlank { "未命名课程" }
        val bracket = TitleRules.BRACKET_SUFFIX.find(title)
        if (bracket != null) {
            val type = recognizedType(bracket.groupValues[1])
            if (type != null) return ParsedCourseTitle(title.removeRange(bracket.range).cleanName(), type)
        }
        val suffix = TitleRules.TYPE_SUFFIX.find(title)
        if (suffix != null) {
            val type = TeachingType(suffix.groupValues[1].uppercase(Locale.ROOT))
            return ParsedCourseTitle(title.removeRange(suffix.range).cleanName(), type)
        }
        val grouped = TitleRules.TYPE_BEFORE_GROUP.find(title)
        if (grouped != null) {
            return ParsedCourseTitle(title.substring(0, grouped.range.first).cleanName(), TeachingType(grouped.groupValues[1].uppercase(Locale.ROOT)))
        }
        return ParsedCourseTitle(title, TitleRules.OTHER_TYPE)
    }

    private fun recognizedType(bracketContent: String): TeachingType? = TitleRules.TYPE_TOKEN.find(bracketContent)
        ?.groupValues?.get(1)?.uppercase(Locale.ROOT)?.let(::TeachingType)

    private fun String.cleanName(): String = trim().trimEnd(',', ';', ':', '/', '-', '–', '—').trim().ifBlank { "未命名课程" }
    private fun normalizedKey(title: String): String = title.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT).ifBlank { "(untitled)" }

    private data class ParsedCourseTitle(val courseName: String, val type: TeachingType)
    private data class TeachingType(val label: String) { val key: String get() = label.lowercase(Locale.ROOT) }

    private object TitleRules {
        val OTHER_TYPE = TeachingType("其他课次")
        private const val TYPE_NAMES = "VO|UE|PR|SE|TU|EX|KO|VL|ÜB|VÜ"
        val TYPE_TOKEN = Regex("(?i)(?<![A-ZÄÖÜ])($TYPE_NAMES)(?![A-ZÄÖÜ])")
        val BRACKET_SUFFIX = Regex("\\s*[\\(\\[]([^\\)\\]]+)[\\)\\]]\\s*$")
        val TYPE_SUFFIX = Regex("(?i)(?:\\s|[,;:/\\-–—])+($TYPE_NAMES)\\s*$")
        val TYPE_BEFORE_GROUP = Regex("(?i)\\s+($TYPE_NAMES)\\s*,\\s*(?:Standardgruppe|Gruppe\\b.*|Group\\b.*)$")
    }
}

/** 顶层和类型层都仅保存视觉组键；切换不会改变其它组或课程成员。 */
internal fun Set<String>.toggleCourseDisplayGroup(key: String): Set<String> =
    if (key in this) this - key else this + key

internal fun Set<String>.toggleCourseTeachingTypeGroup(key: String): Set<String> =
    if (key in this) this - key else this + key
