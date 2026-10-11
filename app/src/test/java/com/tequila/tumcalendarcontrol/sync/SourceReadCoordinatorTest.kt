/*
 * Aufgabe: synthetische Regressionstests für SourceReadCoordinator.
 * Ein-/Ausgabe: künstliche Zustände und Fixtures -> JUnit-Prüfergebnisse.
 * Nebenwirkungen: nur lokaler Testprozess und gegebenenfalls Robolectric-Präferenzen.
 * Nicht zuständig: echte Konten, private Quellen, Geräte oder reale Kalenderausgaben.
 */
package com.tequila.tumcalendarcontrol.sync

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceReadCoordinatorTest {
    @Test
    fun `成功读取只回调一次`() {
        val coordinator = SourceReadCoordinator(reader = { success() }, timeoutMillis = 200)
        val completion = await(coordinator)

        assertEquals(SourceReadCompletionStage.SUCCESS, completion.stage)
        coordinator.close()
    }

    @Test
    fun `读取失败保留失败阶段`() {
        val coordinator = SourceReadCoordinator(reader = { IcalReadOnlyImportResult.Failure("合成失败") }, timeoutMillis = 200)
        val completion = await(coordinator)

        assertEquals(SourceReadCompletionStage.FAILURE, completion.stage)
        coordinator.close()
    }

    @Test
    fun `读取异常被转换为可重试失败`() {
        val coordinator = SourceReadCoordinator(reader = { error("synthetic") }, timeoutMillis = 200)
        val completion = await(coordinator)

        assertEquals(SourceReadCompletionStage.EXCEPTION, completion.stage)
        assertTrue(completion.result is IcalReadOnlyImportResult.Failure)
        coordinator.close()
    }

    @Test
    fun `永不返回的读取在截止时间终结`() {
        val release = CountDownLatch(1)
        val coordinator = SourceReadCoordinator(reader = { release.await(); success() }, timeoutMillis = 30)
        val completion = await(coordinator)

        assertEquals(SourceReadCompletionStage.TIMEOUT, completion.stage)
        release.countDown()
        coordinator.close()
    }

    @Test
    fun `超时后的迟到成功不会覆盖超时结果`() {
        val release = CountDownLatch(1)
        val callbacks = mutableListOf<SourceReadCompletion>()
        val done = CountDownLatch(1)
        val coordinator = SourceReadCoordinator(reader = { release.await(); success() }, timeoutMillis = 30)

        coordinator.start("https://synthetic.example.test", { completion -> synchronized(callbacks) { callbacks += completion }; done.countDown() })
        assertTrue(done.await(1, TimeUnit.SECONDS))
        release.countDown()
        Thread.sleep(60)

        assertEquals(listOf(SourceReadCompletionStage.TIMEOUT), synchronized(callbacks) { callbacks.map(SourceReadCompletion::stage) })
        coordinator.close()
    }

    private fun await(coordinator: SourceReadCoordinator): SourceReadCompletion {
        val done = CountDownLatch(1)
        var result: SourceReadCompletion? = null
        coordinator.start("https://synthetic.example.test") { completion -> result = completion; done.countDown() }
        assertTrue(done.await(1, TimeUnit.SECONDS))
        return requireNotNull(result)
    }

    private fun success() = IcalReadOnlyImportResult.Success(
        IcalReadOnlyPreview(0, 0, emptyMap(), null, null, emptyList()),
    )
}
