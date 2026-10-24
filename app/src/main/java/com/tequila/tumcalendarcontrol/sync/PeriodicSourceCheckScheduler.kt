/*
 * Aufgabe: Ausdrücklich aktivierte Quellenprüfung als eindeutigen periodischen WorkManager-Auftrag planen.
 * Ein-/Ausgabe: Expliziter Prüfschalter und Häufigkeit -> eindeutiger periodischer WorkManager-Auftrag.
 * Nebenwirkungen: Nur ausdrücklich aktivierten WorkManager-Auftrag einplanen/entfernen.
 * Nicht zuständig: Inhaltsprüfung, Kontoautorisierung, Synchronisierung oder Anfrage der Benachrichtigungsberechtigung.
 */
package com.tequila.tumcalendarcontrol.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
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

    /** Einmalige lesende Prüfung sofort (Schnelleinstellung); der Worker prüft selbst, ob die Prüfung aktiviert ist. */
    fun runOnceNow() {
        val request = OneTimeWorkRequest.Builder(PeriodicSourceCheckWorker::class.java)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        workManager.enqueueUniqueWork(ONCE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "tum_private_source_read_only_check"
        const val ONCE_WORK_NAME = "tum_private_source_read_only_check_once"
    }
}
