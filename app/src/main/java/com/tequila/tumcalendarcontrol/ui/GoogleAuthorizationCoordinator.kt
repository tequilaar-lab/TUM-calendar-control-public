/*
 * 职责：以 Google Play services Authorization API 请求应用创建日历的短生命周期访问令牌。
 * 输入／输出：输入用户明确点按与授权结果 Intent；输出内存令牌或脱敏失败摘要。
 * 外部副作用：仅在调用 authorize 时显示 Google 系统授权界面；不保存令牌、不创建日历、不发送日程。
 * 不负责：Google REST 调用、账户资料显示、离线刷新令牌或权限申请之外的同步。
 */
package com.tequila.tumcalendarcontrol.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

sealed interface GoogleAuthorizationOutcome {
    data class AccessToken(val value: String) : GoogleAuthorizationOutcome
    data class LaunchConsent(val pendingIntent: PendingIntent) : GoogleAuthorizationOutcome
    data class Failure(val summary: String) : GoogleAuthorizationOutcome
}

class GoogleAuthorizationCoordinator(context: Context) {
    private val client = Identity.getAuthorizationClient(context.applicationContext)

    fun authorize(callback: (GoogleAuthorizationOutcome) -> Unit) {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GOOGLE_CALENDAR_APP_CREATED_SCOPE)))
            .build()
        client.authorize(request)
            .addOnSuccessListener { callback(it.toOutcome()) }
            .addOnFailureListener { callback(GoogleAuthorizationOutcome.Failure("Google 授权未完成；请检查设备上的 Google 服务后重试")) }
    }

    fun resolveResult(data: Intent?, callback: (GoogleAuthorizationOutcome) -> Unit) {
        val outcome = runCatching { client.getAuthorizationResultFromIntent(requireNotNull(data)).toOutcome() }
            .getOrElse { GoogleAuthorizationOutcome.Failure("Google 授权未完成或已取消；未获得令牌") }
        callback(outcome)
    }

    private fun AuthorizationResult.toOutcome(): GoogleAuthorizationOutcome {
        val token = accessToken
        val consent = pendingIntent
        return when {
            !token.isNullOrBlank() -> GoogleAuthorizationOutcome.AccessToken(token)
            consent != null -> GoogleAuthorizationOutcome.LaunchConsent(consent)
            else -> GoogleAuthorizationOutcome.Failure("Google 未返回日历访问令牌")
        }
    }

    private companion object {
        const val GOOGLE_CALENDAR_APP_CREATED_SCOPE = "https://www.googleapis.com/auth/calendar.app.created"
    }
}
