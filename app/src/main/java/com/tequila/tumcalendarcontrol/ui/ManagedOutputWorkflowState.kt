/*
 * 职责：保存受控输出预览、Google 授权和执行／回滚请求的运行时状态。
 * 输入／输出：接收用户确认和适配层回调；输出令牌、请求标志、授权结果及待执行候选。
 * 外部副作用：无；不保存令牌、不调用 OAuth、不创建／更新／删除任何日历事件。
 * 不负责：构建同步候选、读写 RuleLedger、解析 iCal、系统权限请求或网关实现。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tequila.tumcalendarcontrol.sync.DesiredCalendarEvent
import com.tequila.tumcalendarcontrol.sync.ManagedSyncPreview

/** 输出动作由调用者先完成前置检查，状态机只负责一次运行中的请求与令牌。 */
internal class ManagedOutputWorkflowState {
    var preview by mutableStateOf<ManagedSyncPreview?>(null)
    var desiredEvents by mutableStateOf<List<DesiredCalendarEvent>>(emptyList())
    var syncRequested by mutableStateOf(false)
        private set
    var rollbackRequested by mutableStateOf(false)
        private set
    var inProgress by mutableStateOf(false)
        private set
    var googleAuthorizationRequested by mutableStateOf(false)
        private set
    private var googleAccessToken by mutableStateOf<String?>(null)
    var googleAuthorizationStatus by mutableStateOf(GoogleAuthorizationStatus.NOT_CONNECTED)
        private set
    var googleAuthorizationDetail by mutableStateOf(GoogleAuthorizationStatus.NOT_CONNECTED.defaultDetail)
        private set
    var localCalendarPermissionGranted by mutableStateOf(false)
    var operationToken by mutableStateOf(0L)
        private set

    val hasGoogleAuthorization: Boolean get() = googleAccessToken != null
    fun currentGoogleAccessToken(): String? = googleAccessToken

    /** 仅表达 OAuth 页面启动意图，令牌只在适配层回调后写入当前进程。 */
    fun requestGoogleAuthorization(enabled: Boolean): String? {
        if (!enabled) {
            googleAuthorizationStatus = GoogleAuthorizationStatus.FAILED
            googleAuthorizationDetail = "请先开启 Google 日历写入，再连接 Google 日历。"
            return googleAuthorizationDetail
        }
        googleAccessToken = null
        googleAuthorizationRequested = true
        googleAuthorizationStatus = GoogleAuthorizationStatus.AUTHORIZING
        googleAuthorizationDetail = GoogleAuthorizationStatus.AUTHORIZING.defaultDetail
        return null
    }

    fun resolveGoogleAuthorization(outcome: GoogleAuthorizationOutcome): String {
        googleAuthorizationRequested = false
        when (outcome) {
            is GoogleAuthorizationOutcome.AccessToken -> {
                googleAccessToken = outcome.value
                googleAuthorizationStatus = GoogleAuthorizationStatus.AUTHORIZED
                googleAuthorizationDetail = GoogleAuthorizationStatus.AUTHORIZED.defaultDetail
            }
            is GoogleAuthorizationOutcome.Failure -> {
                googleAccessToken = null
                googleAuthorizationStatus = GoogleAuthorizationStatus.FAILED
                googleAuthorizationDetail = outcome.summary
            }
            is GoogleAuthorizationOutcome.LaunchConsent -> {
                googleAuthorizationStatus = GoogleAuthorizationStatus.AUTHORIZING
                googleAuthorizationDetail = GoogleAuthorizationStatus.AUTHORIZING.defaultDetail
            }
        }
        return googleAuthorizationDetail
    }

    fun disconnectGoogle() {
        googleAuthorizationRequested = false
        googleAccessToken = null
        googleAuthorizationStatus = GoogleAuthorizationStatus.NOT_CONNECTED
        googleAuthorizationDetail = GoogleAuthorizationStatus.NOT_CONNECTED.defaultDetail
    }

    fun consumeGoogleAuthorizationRequest() { googleAuthorizationRequested = false }

    fun beginSync(): Long? {
        if (inProgress) return null
        operationToken += 1
        inProgress = true
        syncRequested = true
        rollbackRequested = false
        return operationToken
    }

    fun beginRollback(): Long? {
        if (inProgress) return null
        operationToken += 1
        inProgress = true
        syncRequested = false
        rollbackRequested = true
        return operationToken
    }

    /** 旧后台任务只有令牌一致时才可以结束当前状态，避免覆盖新操作。 */
    fun finishOperation(token: Long): Boolean {
        if (token != operationToken || !inProgress) return false
        syncRequested = false
        rollbackRequested = false
        inProgress = false
        return true
    }
}
