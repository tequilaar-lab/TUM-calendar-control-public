/*
 * 职责：以 Google Play services Authorization API 请求应用创建日历的短生命周期访问令牌。
 * 输入／输出：输入用户明确点按与授权结果 Intent；输出内存令牌或脱敏失败摘要。
 * 外部副作用：仅在调用 authorize 时显示 Google 系统授权界面；不保存令牌、不创建日历、不发送日程。
 * 不负责：Google REST 调用、账户资料显示、离线刷新令牌或权限申请之外的同步。
 */
package com.tequila.tumcalendarcontrol.ui

import android.app.PendingIntent
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
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
            .addOnSuccessListener {
                val outcome = it.toOutcome()
                Log.i(GoogleAuthorizationLogTag, "stage=authorization_result outcome=${outcome.logLabel}")
                callback(outcome)
            }
            .addOnFailureListener { error ->
                val summary = failureSummary("请求失败", error)
                Log.w(GoogleAuthorizationLogTag, "stage=authorize_failed diagnostic=${failureDiagnostic(error)}")
                callback(GoogleAuthorizationOutcome.Failure(summary))
            }
    }

    fun resolveResult(resultCode: Int, data: Intent?, callback: (GoogleAuthorizationOutcome) -> Unit) {
        if (resultCode != Activity.RESULT_OK) {
            val outcome = GoogleAuthorizationOutcome.Failure("Google 授权页未完成（系统结果码：$resultCode）。")
            Log.w(GoogleAuthorizationLogTag, "stage=consent_return_not_ok resultCode=$resultCode")
            callback(outcome)
            return
        }
        if (data == null) {
            val outcome = GoogleAuthorizationOutcome.Failure("Google 授权返回为空（系统结果码：$resultCode）。")
            Log.w(GoogleAuthorizationLogTag, "stage=consent_return_empty_data resultCode=$resultCode")
            callback(outcome)
            return
        }
        val outcome = runCatching { client.getAuthorizationResultFromIntent(data).toOutcome() }
            .getOrElse { error ->
                Log.w(GoogleAuthorizationLogTag, "stage=consent_return_failed diagnostic=${failureDiagnostic(error)}")
                GoogleAuthorizationOutcome.Failure(failureSummary("返回失败", error))
            }
        Log.i(GoogleAuthorizationLogTag, "stage=consent_return outcome=${outcome.logLabel}")
        callback(outcome)
    }

    /** 仅在 Calendar API 已明确返回 401 后清除 Play services 的本地令牌缓存；不会撤销授权。 */
    fun clearRejectedAccessToken(accessToken: String, onComplete: (Boolean) -> Unit) {
        val request = ClearTokenRequest.builder().setToken(accessToken).build()
        client.clearToken(request).addOnCompleteListener { result ->
            Log.i(GoogleAuthorizationLogTag, "stage=token_cache_cleared success=${result.isSuccessful}")
            onComplete(result.isSuccessful)
        }
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

    /** 仅把可公开的系统状态码带到界面；不保留或显示异常正文。 */
    private fun failureSummary(stage: String, error: Throwable): String = when (error) {
        is ApiException -> "Google 授权$stage（服务状态码：${error.statusCode}）。"
        else -> "Google 授权$stage（异常类型：${error.javaClass.simpleName}）。"
    }

    private fun failureDiagnostic(error: Throwable): String = when (error) {
        is ApiException -> "apiStatus=${error.statusCode}"
        else -> "exceptionType=${error.javaClass.simpleName}"
    }

    private companion object {
        const val GoogleAuthorizationLogTag = "TumGoogleAuth"
        const val GOOGLE_CALENDAR_APP_CREATED_SCOPE = "https://www.googleapis.com/auth/calendar.app.created"
    }
}

/** 仅输出脱敏阶段标签；令牌、账户、日历和异常正文均不得进入日志。 */
private val GoogleAuthorizationOutcome.logLabel: String
    get() = when (this) {
        is GoogleAuthorizationOutcome.AccessToken -> "access_token_received"
        is GoogleAuthorizationOutcome.LaunchConsent -> "consent_required"
        is GoogleAuthorizationOutcome.Failure -> "failure"
    }
