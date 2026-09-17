/*
 * 职责：导出和导入不含连接信息、目标映射或同步摘要的规则备份。
 * 输入／输出：输入 RuleLedger 或备份文本；输出可预览的规则／手动事项资料。
 * 外部副作用：无。
 * 不负责：文件 I/O、替换当前资料库、保存订阅地址、OAuth 或修改日历。
 */
package com.tequila.tumcalendarcontrol.sync

data class RuleLedgerBackup(
    val rules: List<SyncRule>,
    val manualEvents: List<ManualScheduleItem>,
    val groupRules: List<GroupAttendanceRule> = emptyList(),
) {
    val summary: RuleLedgerBackupSummary get() = RuleLedgerBackupSummary(rules.size, groupRules.size, manualEvents.size)
}

data class RuleLedgerBackupSummary(val ruleCount: Int, val groupRuleCount: Int, val manualEventCount: Int)

object RuleLedgerBackupCodec {
    private const val HEADER = "tum-calendar-rule-backup-v2"
    private val SUPPORTED_HEADERS = setOf("tum-calendar-rule-backup-v1", HEADER)

    fun encode(ledger: RuleLedger): String = "$HEADER\n" + RuleLedgerCodec.encode(
        RuleLedger(rules = ledger.rules, manualEvents = ledger.manualEvents, groupRules = ledger.groupRules),
    )

    fun decode(text: String): RuleLedgerBackup? {
        val lines = text.lineSequence().toList()
        if (lines.firstOrNull() !in SUPPORTED_HEADERS) return null
        val decoded = RuleLedgerCodec.decodeOrNull(lines.drop(1).joinToString("\n")) ?: return null
        if (decoded.mappings.isNotEmpty() || decoded.lastSummary != null) return null
        return RuleLedgerBackup(decoded.rules, decoded.manualEvents, decoded.groupRules)
    }
}
