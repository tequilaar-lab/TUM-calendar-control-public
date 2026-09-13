/*
 * 职责：表达用户未来亲自提供的私有来源与 Google 连接配置，并执行无网络格式校验。
 * 输入／输出：输入订阅地址、OAuth 客户端标识和目标日历名称；输出校验结果及脱敏摘要。
 * 外部副作用：无。
 * 不负责：保存秘密、发起 OAuth、联网读取订阅或创建任何日历。
 */
package com.tequila.tumcalendarcontrol.sync

import java.net.URI

data class PrivateSyncConfiguration(
    val tumSubscriptionUrl: String = "",
    val googleOAuthClientId: String = "",
    val googleCalendarName: String = DEFAULT_GOOGLE_CALENDAR_NAME,
    /** 仅保存本应用创建的目标标识；不会保存账户名、令牌或日历正文。 */
    val localCalendarId: String = "",
    val googleCalendarId: String = "",
) {
    val hasTumSubscription: Boolean get() = tumSubscriptionUrl.isNotBlank()
    val hasGoogleClient: Boolean get() = googleOAuthClientId.isNotBlank()

    /** 配置页不展示地址的任何组成部分，避免主机名也成为可观察的私有信息。 */
    fun maskedTumSubscription(): String = if (hasTumSubscription) "•••••••• · 已加密保存在本机" else "尚未配置"

    fun validate(): List<ConfigurationProblem> = buildList {
        if (hasTumSubscription) {
            val uri = runCatching { URI(tumSubscriptionUrl.trim()) }.getOrNull()
            if (uri?.scheme != "https" || uri.host.isNullOrBlank()) {
                add(ConfigurationProblem.INVALID_TUM_SUBSCRIPTION)
            }
        }
        if (hasGoogleClient && !googleOAuthClientId.trim().endsWith(".apps.googleusercontent.com")) {
            add(ConfigurationProblem.INVALID_GOOGLE_CLIENT_ID)
        }
        if (googleCalendarName.trim().isBlank()) add(ConfigurationProblem.BLANK_GOOGLE_CALENDAR_NAME)
    }

    companion object {
        const val DEFAULT_GOOGLE_CALENDAR_NAME = "TUM 日历控制"
    }
}

enum class ConfigurationProblem(val message: String) {
    INVALID_TUM_SUBSCRIPTION("订阅地址必须是带主机名的 HTTPS 地址"),
    INVALID_GOOGLE_CLIENT_ID("Google OAuth 客户端标识应以 .apps.googleusercontent.com 结尾"),
    BLANK_GOOGLE_CALENDAR_NAME("Google 专用目标日历名称不能为空"),
}
