/*
 * 职责：保存后台来源检查的非敏感开关、频率与加密后的结果摘要。
 * 输入／输出：输入用户选择或工作结果；输出恢复后的设置和状态。
 * 外部副作用：仅写入应用私有 SharedPreferences 与 Android Keystore；不读取网络或日历。
 * 不负责：调度 WorkManager、解析 iCal、显示通知或保存私有 URL。
 */
package com.tequila.tumcalendarcontrol.sync

import android.content.Context
import java.time.Instant

enum class SourceCheckCadence(val label: String, val intervalHours: Long) {
    DAILY("每天检查一次", 24),
    WEEKLY("每周检查一次", 24 * 7L),
}

data class SourceCheckSettings(
    val enabled: Boolean = false,
    val cadence: SourceCheckCadence = SourceCheckCadence.DAILY,
)

enum class SourceCheckResult { NEVER, INITIAL, UNCHANGED, CHANGED, FAILED, NEEDS_CONFIGURATION }

data class SourceCheckStatus(
    val checkedAt: Instant? = null,
    val result: SourceCheckResult = SourceCheckResult.NEVER,
)

/** 检查指纹及时间都以应用 Keystore 加密，避免摘要成为可读的来源旁路。 */
class SourceCheckSettingsStore(context: Context, private val cipher: KeystoreTextCipher = KeystoreTextCipher()) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun loadSettings(): SourceCheckSettings = SourceCheckSettings(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        cadence = preferences.getString(KEY_CADENCE, null)?.let { runCatching { SourceCheckCadence.valueOf(it) }.getOrNull() }
            ?: SourceCheckCadence.DAILY,
    )

    fun saveSettings(settings: SourceCheckSettings) {
        preferences.edit().putBoolean(KEY_ENABLED, settings.enabled).putString(KEY_CADENCE, settings.cadence.name).apply()
    }

    fun loadStatus(): SourceCheckStatus = runCatching {
        SourceCheckStatus(
            checkedAt = preferences.getString(KEY_CHECKED_AT, null)?.let(cipher::decrypt)?.let(Instant::parse),
            result = preferences.getString(KEY_RESULT, null)?.let(SourceCheckResult::valueOf) ?: SourceCheckResult.NEVER,
        )
    }.getOrElse { SourceCheckStatus() }

    fun previousFingerprint(): String? = preferences.getString(KEY_FINGERPRINT, null)?.let { runCatching { cipher.decrypt(it) }.getOrNull() }

    fun recordSuccess(fingerprint: String, checkedAt: Instant): SourceCheckStatus {
        val previous = previousFingerprint()
        val result = when {
            previous == null -> SourceCheckResult.INITIAL
            previous == fingerprint -> SourceCheckResult.UNCHANGED
            else -> SourceCheckResult.CHANGED
        }
        saveProtectedStatus(fingerprint, checkedAt, result)
        return SourceCheckStatus(checkedAt, result)
    }

    fun recordFailure(checkedAt: Instant, result: SourceCheckResult): SourceCheckStatus {
        require(result == SourceCheckResult.FAILED || result == SourceCheckResult.NEEDS_CONFIGURATION)
        preferences.edit()
            .putString(KEY_CHECKED_AT, cipher.encrypt(checkedAt.toString()))
            .putString(KEY_RESULT, result.name)
            .apply()
        return SourceCheckStatus(checkedAt, result)
    }

    private fun saveProtectedStatus(fingerprint: String, checkedAt: Instant, result: SourceCheckResult) {
        preferences.edit()
            .putString(KEY_FINGERPRINT, cipher.encrypt(fingerprint))
            .putString(KEY_CHECKED_AT, cipher.encrypt(checkedAt.toString()))
            .putString(KEY_RESULT, result.name)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "source_check_settings"
        const val KEY_ENABLED = "enabled"
        const val KEY_CADENCE = "cadence"
        const val KEY_FINGERPRINT = "fingerprint"
        const val KEY_CHECKED_AT = "checked_at"
        const val KEY_RESULT = "result"
    }
}
