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
    fun load(): RuleLedger = RuleLedgerCodec.decode(preferences.getString(KEY_DATA, null))
    fun save(ledger: RuleLedger) { preferences.edit().putString(KEY_DATA, RuleLedgerCodec.encode(ledger)).apply() }
    /** 后台逐项检查点同步落盘：应用被终止时不丢失此前已经完成的外部写入身份。 */
    fun checkpointMappings(mappings: List<StoredEventMapping>) {
        check(preferences.edit().putString(KEY_DATA, RuleLedgerCodec.encode(load().copy(mappings = mappings))).commit()) { "应用写入记录未能保存" }
    }
    private companion object { const val FILE_NAME = "rule_ledger"; const val KEY_DATA = "serialized_ledger" }
}
