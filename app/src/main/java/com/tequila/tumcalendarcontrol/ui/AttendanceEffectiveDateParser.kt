/*
 * 职责：将出席规则表单中的日期文本规范化并严格解析为 LocalDate。
 * 输入／输出：输入用户键入的日期文本；输出有效 LocalDate 或 null。
 * 外部副作用：无。
 * 不负责：推断日期顺序、选择日历实例、保存规则或进行任何同步。
 */
package com.tequila.tumcalendarcontrol.ui

import java.time.LocalDate

/** 只兼容常见输入法的数字与横线差异；格式和实际日期仍由 LocalDate 严格验证。 */
object AttendanceEffectiveDateParser {
    fun parseOrNull(value: String): LocalDate? = runCatching {
        LocalDate.parse(value.trim().normalizeCommonDateCharacters())
    }.getOrNull()

    private fun String.normalizeCommonDateCharacters(): String = buildString(length) {
        this@normalizeCommonDateCharacters.forEach { character ->
            append(
                when (character) {
                    '－', '–', '—', '−' -> '-'
                    in '０'..'９' -> ('0'.code + character.code - '０'.code).toChar()
                    else -> character
                },
            )
        }
    }
}
