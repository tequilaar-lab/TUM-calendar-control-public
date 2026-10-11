/*
 * Aufgabe: Einmalige lesende iCal-Anfrage mit unabhängigem Hintergrundauftrag und Timeout koordinieren.
 * Ein-/Ausgabe: Einmaliger Abrufauftrag -> genau ein Ergebnis oder Timeout.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Private Konfigurationsspeicherung, iCal-Parsing, UI-Zustandsänderungen, Berechtigungen oder Zielkalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.i18n.message

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

/** Koordinator ruft genau einmal zurück. Timeout stoppt Arbeitsfaden und verwirft verspätete Ergebnisse. Aufrufer übernimmt Hauptthread; kein Android Context oder sensitive Ausgabe. */
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
                // Zuerst exklusiven Abschluss beanspruchen, damit unterbrochener Arbeitsfaden nicht vorzeitig einen Fehler meldet.
                val timedOut = finish(
                    IcalReadOnlyImportResult.Failure(message(128, (timeoutMillis / 1000))),
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
                IcalReadOnlyImportResult.Failure(message(129))
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
