/*
 * 职责：在用户开启的周期任务中只读检查私有 iCal 是否可能变化，并记录脱敏状态。
 * 输入／输出：读取已加密的本机来源配置；输出 WorkManager 成功／重试结果和通用通知。
 * 外部副作用：一次 HTTPS 只读读取、应用私有状态保存、可选系统通知；绝不写任何日历。
 * 不负责：持久化来源正文、更新课程目录、申请 OAuth／日历权限、执行同步或撤销。
 */
package com.tequila.tumcalendarcontrol.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Instant

class PeriodicSourceCheckWorker(appContext: Context, parameters: WorkerParameters) : Worker(appContext, parameters) {
    override fun doWork(): Result {
        val configuration = PrivateSyncConfigurationStore(applicationContext).load()
        val store = SourceCheckSettingsStore(applicationContext)
        if (!store.loadSettings().enabled) return Result.success()
        if (!configuration.hasTumSubscription) {
            store.recordFailure(Instant.now(), SourceCheckResult.NEEDS_CONFIGURATION)
            return Result.success()
        }
        return when (val result = ReadOnlyIcalImportService().preview(configuration.tumSubscriptionUrl)) {
            is IcalReadOnlyImportResult.Success -> {
                val status = store.recordSuccess(SourceCheckFingerprint.from(result.preview.occurrences), Instant.now())
                if (status.result == SourceCheckResult.CHANGED) notifyPossibleChange()
                Result.success()
            }
            is IcalReadOnlyImportResult.Failure -> {
                store.recordFailure(Instant.now(), SourceCheckResult.FAILED)
                Result.retry()
            }
        }
    }

    private fun notifyPossibleChange() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "TUM 来源检查", NotificationManager.IMPORTANCE_DEFAULT))
        val notification = android.app.Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("TUM 日历控制")
            .setContentText("检测到来源可能有更新；打开应用查看并手动预览。")
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "tum_source_check"
        const val NOTIFICATION_ID = 4102
    }
}
