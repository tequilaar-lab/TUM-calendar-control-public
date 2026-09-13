/*
 * 职责：严格解析可选的 iCal 来源输出日期窗口，并判断来源实例是否与窗口相交。
 * 输入／输出：输入日期文本或来源实例；输出日期窗口、解析结果或是否保留。
 * 外部副作用：无。
 * 不负责：网络读取、保存配置、选择课程、规则三态或写入日历。
 */
package com.tequila.tumcalendarcontrol.sync

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object SourceDateTextParser {
    fun parseOrNull(value: String): LocalDate? = runCatching {
        LocalDate.parse(value.trim().normalizeCommonDateCharacters())
    }.getOrNull()

    private fun String.normalizeCommonDateCharacters(): String = buildString(length) {
        this@normalizeCommonDateCharacters.forEach { character ->
            append(when (character) {
                '－', '–', '—', '−' -> '-'
                in '０'..'９' -> ('0'.code + character.code - '０'.code).toChar()
                else -> character
            })
        }
    }
}

/** 开始/结束均为可选且按当地自然日包含；实例只要与窗口相交即可保留。 */
data class SourceOutputDateWindow(val startsOn: LocalDate? = null, val endsOn: LocalDate? = null) {
    init { require(startsOn == null || endsOn == null || startsOn <= endsOn) { "来源输出范围的开始日期不能晚于结束日期。" } }

    fun includes(startsAt: Instant, endsAt: Instant, zoneId: ZoneId): Boolean {
        val lowerBound = startsOn?.atStartOfDay(zoneId)?.toInstant()
        val upperExclusive = endsOn?.plusDays(1)?.atStartOfDay(zoneId)?.toInstant()
        return (lowerBound == null || endsAt > lowerBound) && (upperExclusive == null || startsAt < upperExclusive)
    }

    companion object {
        fun parseOrNull(startText: String, endText: String): SourceOutputDateWindow? {
            val start = startText.takeIf(String::isNotBlank)?.let(SourceDateTextParser::parseOrNull) ?: if (startText.isBlank()) null else return null
            val end = endText.takeIf(String::isNotBlank)?.let(SourceDateTextParser::parseOrNull) ?: if (endText.isBlank()) null else return null
            return runCatching { SourceOutputDateWindow(start, end) }.getOrNull()
        }
    }
}
