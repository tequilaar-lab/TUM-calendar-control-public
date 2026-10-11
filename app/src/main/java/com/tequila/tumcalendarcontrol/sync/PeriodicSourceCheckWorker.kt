/*
 * Aufgabe: Aktivierte periodische iCal-Quelle lesend auf Änderungen prüfen und bereinigten Status speichern.
 * Ein-/Ausgabe: Private Quelle und Prüfeinstellungen -> verschlüsselter Änderungsfingerabdruck und allgemeiner Status.
 * Nebenwirkungen: Nur aktivierte lesende Quellenprüfung, verschlüsselte Statusablage und berechtigter allgemeiner Hinweis.
 * Nicht zuständig: Speicherung des Quelleninhalts, Kurskatalogaktualisierung, OAuth-/Kalenderberechtigungen, Synchronisierung oder Rücknahme.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.i18n.AppLanguagePreferences
import com.tequila.tumcalendarcontrol.i18n.localizedAppContext
import com.tequila.tumcalendarcontrol.i18n.localizedMessage

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
        val localized = localizedAppContext(applicationContext, AppLanguagePreferences(applicationContext).load())
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, localizedMessage(localized, message(106)), NotificationManager.IMPORTANCE_DEFAULT))
        val notification = android.app.Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(localizedMessage(localized, message(33)))
            .setContentText(localizedMessage(localized, message(107)))
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "tum_source_check"
        const val NOTIFICATION_ID = 4102
    }
}
