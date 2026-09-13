/*
 * 职责：为后台只读检查生成不保留可见来源正文的稳定变化摘要。
 * 输入／输出：输入来源实例的输出相关字段；输出 SHA-256 十六进制摘要。
 * 外部副作用：无。
 * 不负责：保存摘要、显示事件、读取 iCal 或判断出席状态。
 */
package com.tequila.tumcalendarcontrol.sync

import java.security.MessageDigest

object SourceCheckFingerprint {
    /** 字段仅在本次内存计算中参与；最终摘要会再由 Keystore 加密保存，绝不保存明文事件。 */
    fun from(occurrences: List<SourceCalendarOccurrence>): String {
        val canonical = occurrences.sortedWith(compareBy(SourceCalendarOccurrence::sourceUid, SourceCalendarOccurrence::instanceKey)).joinToString("\n") {
            listOf(it.sourceUid, it.instanceKey, it.title, it.location, it.startsAt, it.endsAt, it.isCancelled)
                .joinToString("|") { field -> "${field.toString().length}:${field}" }
        }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
