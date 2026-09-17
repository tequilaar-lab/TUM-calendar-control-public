/*
 * 职责：在应用沙盒 SharedPreferences 中保存非敏感规则资料库文本。
 * 输入／输出：输入 RuleLedger；输出已恢复资料库，缺失或损坏时安全为空资料库。
 * 外部副作用：仅写入本应用私有偏好设置。
 * 不负责：凭据加密、日历权限、跨设备同步或访问任何账户。
 */
package com.tequila.tumcalendarcontrol.sync

import android.content.Context

class RuleLedgerPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    fun load(): RuleLedger = synchronized(LOCK) { loadUnlocked() }

    /** 完整结果用于外部同步完成回写；同步 commit 失败必须由调用者报告。 */
    fun save(ledger: RuleLedger): Boolean = synchronized(LOCK) { saveUnlocked(ledger) }

    /**
     * 业务编辑不能用旧内存映射覆盖后台逐项检查点。课程展示资料与规则作为一次用户提交：
     * 第二份偏好写入失败时恢复原规则资料，避免界面声称只保存了一半。
     */
    fun saveBusinessState(ledger: RuleLedger, savePresentations: () -> Boolean): RuleLedger? = synchronized(LOCK) {
        val before = loadUnlocked()
        val merged = ledger.copy(mappings = before.mappings, lastSummary = before.lastSummary)
        if (!saveUnlocked(merged)) return@synchronized null
        if (savePresentations()) return@synchronized merged
        saveUnlocked(before)
        null
    }
    /** 后台逐项检查点同步落盘：应用被终止时不丢失此前已经完成的外部写入身份。 */
    fun checkpointMappings(mappings: List<StoredEventMapping>) {
        synchronized(LOCK) {
            check(saveUnlocked(loadUnlocked().copy(mappings = mappings))) { "应用写入记录未能保存" }
        }
    }
    private fun loadUnlocked(): RuleLedger = RuleLedgerCodec.decode(preferences.getString(KEY_DATA, null))
    private fun saveUnlocked(ledger: RuleLedger): Boolean = preferences.edit().putString(KEY_DATA, RuleLedgerCodec.encode(ledger)).commit()

    private companion object {
        const val FILE_NAME = "rule_ledger"
        const val KEY_DATA = "serialized_ledger"
        val LOCK = Any()
    }
}
