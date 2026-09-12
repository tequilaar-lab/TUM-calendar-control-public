/*
 * 职责：在应用私有 SharedPreferences 中读写非敏感的昵称和头像颜色。
 * 输入／输出：输入 UserProfile；输出恢复的 UserProfile，缺失时返回中性默认资料。
 * 外部副作用：只写入本应用沙盒；不发送网络请求或访问日历、账户、相册。
 * 不负责：加密凭据、照片文件、跨设备同步或任何真实身份校验。
 */
package com.tequila.tumcalendarcontrol.ui

import android.content.Context

class ProfilePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): UserProfile {
        val savedName = preferences.getString(KEY_NAME, null)?.trim().orEmpty()
        val savedTone = preferences.getString(KEY_TONE, null)
        return UserProfile(
            displayName = savedName.ifBlank { UserProfile().displayName },
            avatarTone = AvatarTone.entries.firstOrNull { it.name == savedTone } ?: AvatarTone.TUM_BLUE,
        )
    }

    fun save(profile: UserProfile) {
        preferences.edit()
            .putString(KEY_NAME, profile.displayName.trim().ifBlank { UserProfile().displayName })
            .putString(KEY_TONE, profile.avatarTone.name)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "local_profile"
        const val KEY_NAME = "display_name"
        const val KEY_TONE = "avatar_tone"
    }
}
