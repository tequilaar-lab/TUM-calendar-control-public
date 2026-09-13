/*
 * 职责：以独立于 Compose 生命周期的后台执行与超时竞争协调一次 iCal 只读请求。
 * 输入／输出：输入来源地址和读取函数；输出一次且仅一次带阶段/耗时的脱敏读取完成结果。
 * 外部副作用：运行调用方提供的只读读取函数；不记录 URL、ICS 正文或日历内容。
 * 不负责：保存私有配置、解析 iCal、改变 UI 状态、申请权限或写入任何目标日历。
 */
package com.tequila.tumcalendarcontrol.sync

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

enum class SourceReadCompletionStage { SUCCESS, FAILURE, EXCEPTION, TIMEOUT }

data class SourceReadCompletion(
    val result: IcalReadOnlyImportResult,
    val stage: SourceReadCompletionStage,
    val elapsedMillis: Long,
)

/**
 * 读取协调器确保回调只发生一次：超时优先时会取消工作线程，任何迟到结果都会被忽略。
 * 调用方须自行在主线程处理回调；本类不持有 Android Context，也不会输出敏感输入。
 */
class SourceReadCoordinator(
    private val reader: (String) -> IcalReadOnlyImportResult,
    private val timeoutMillis: Long,
    private val workers: ExecutorService = Executors.newCachedThreadPool(),
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
    private val nowNanos: () -> Long = System::nanoTime,
) : AutoCloseable {
    init {
        require(timeoutMillis > 0) { "timeoutMillis must be positive" }
    }

    fun start(url: String, onCompleted: (SourceReadCompletion) -> Unit) {
        val completed = AtomicBoolean(false)
        val startedAt = nowNanos()
        val worker = AtomicReference<java.util.concurrent.Future<*>?>()
        fun finish(result: IcalReadOnlyImportResult, stage: SourceReadCompletionStage): Boolean {
            if (completed.compareAndSet(false, true)) {
                val elapsed = ((nowNanos() - startedAt) / 1_000_000L).coerceAtLeast(0L)
                onCompleted(SourceReadCompletion(result, stage, elapsed))
                return true
            }
            return false
        }
        val timeout = scheduler.schedule(
            {
                // 先获得唯一终结权，避免被中断的工作线程抢先报告为“异常”。
                val timedOut = finish(
                    IcalReadOnlyImportResult.Failure("来源读取超过 ${timeoutMillis / 1000} 秒，已停止等待；请检查网络后再试"),
                    SourceReadCompletionStage.TIMEOUT,
                )
                if (timedOut) worker.get()?.cancel(true)
            },
            timeoutMillis,
            TimeUnit.MILLISECONDS,
        )
        worker.set(workers.submit {
            val result = runCatching { reader(url) }
            val stage = when (result.getOrNull()) {
                is IcalReadOnlyImportResult.Success -> SourceReadCompletionStage.SUCCESS
                is IcalReadOnlyImportResult.Failure -> SourceReadCompletionStage.FAILURE
                null -> SourceReadCompletionStage.EXCEPTION
            }
            val safeResult = result.getOrElse {
                IcalReadOnlyImportResult.Failure("来源读取发生未知错误；请稍后重试")
            }
            timeout.cancel(false)
            finish(safeResult, stage)
        })
    }

    override fun close() {
        workers.shutdownNow()
        scheduler.shutdownNow()
    }
}
