/*
 * 职责：把用户明确开启的后台只读检查映射为唯一的 WorkManager 周期任务。
 * 输入／输出：输入检查设置；输出排程或取消操作。
 * 外部副作用：写入 WorkManager 队列；不会立即读取 iCal、通知或写入日历。
 * 不负责：检查内容、账户授权、日历同步或通知权限请求。
 */
package com.tequila.tumcalendarcontrol.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class PeriodicSourceCheckScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun apply(settings: SourceCheckSettings) {
        if (!settings.enabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequest.Builder(
            PeriodicSourceCheckWorker::class.java,
            settings.cadence.intervalHours,
            TimeUnit.HOURS,
        ).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel() = workManager.cancelUniqueWork(UNIQUE_WORK_NAME)

    companion object { const val UNIQUE_WORK_NAME = "tum_private_source_read_only_check" }
}
