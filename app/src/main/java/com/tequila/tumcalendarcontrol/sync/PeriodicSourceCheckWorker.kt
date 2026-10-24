/*
 * Aufgabe: Aktivierte periodische iCal-Quelle lesend auf Änderungen prüfen und bereinigten Status speichern.
 * Ein-/Ausgabe: Private Quelle und Prüfeinstellungen -> verschlüsselter Änderungsfingerabdruck und allgemeiner Status.
 * Nebenwirkungen: Nur aktivierte lesende Quellenprüfung, verschlüsselte Statusablage und berechtigter Hinweis mit
 *   Änderungszahlen gegenüber der gespeicherten Lesung; aufgeklappt und nur entsperrt zusätzlich je Kurs die Arten der
 *   Änderung (Sperrbildschirm: öffentliche Fassung nur mit Zahlen); Antippen öffnet nur die Seite „Kurse lesen“.
 * Nicht zuständig: Speicherung des Quelleninhalts, Kurskatalogaktualisierung, OAuth-/Kalenderberechtigungen, Synchronisierung oder Rücknahme.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.i18n.AppLanguagePreferences
import com.tequila.tumcalendarcontrol.i18n.localizedAppContext
import com.tequila.tumcalendarcontrol.i18n.localizedMessage

import android.Manifest
import android.app.NotificationChannel
import android.app.PendingIntent
import com.tequila.tumcalendarcontrol.ui.AppEntry
import com.tequila.tumcalendarcontrol.ui.AppEntryIntents
import com.tequila.tumcalendarcontrol.ui.SourceSnapshotPreferences
import com.tequila.tumcalendarcontrol.ui.countsText
import com.tequila.tumcalendarcontrol.ui.courseDigest
import com.tequila.tumcalendarcontrol.widget.ScheduleWidgets
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
                if (status.result == SourceCheckResult.CHANGED) notifyPossibleChange(changeSummary(result.preview.occurrences))
                // Prüf-Widget zeigt den neuen Stand.
                ScheduleWidgets.refreshAll(applicationContext)
                Result.success()
            }
            is IcalReadOnlyImportResult.Failure -> {
                store.recordFailure(Instant.now(), SourceCheckResult.FAILED)
                ScheduleWidgets.refreshAll(applicationContext)
                Result.retry()
            }
        }
    }

    /**
     * Nur Zähltext gegen die zuletzt gespeicherte Lesung (nichts wird gespeichert). Die Prüfung liest einen Standardzeitraum,
     * die gespeicherte Lesung ggf. einen anderen: neue/entfallene Termine daher nur im gemeinsamen Zeitraum zählen.
     */
    private fun changeSummary(read: List<SourceCalendarOccurrence>): SourceChangeSummary? {
        val saved = SourceSnapshotPreferences(applicationContext).peek().latest ?: return null
        return SourceChangeSummary.between(saved.occurrences, read, restrictToCommonSpan = true).takeUnless { it.isEmpty }
    }

    /** Zähler als Kurztext; aufgeklappt zusätzlich je Kurs die Arten der Änderung (höchstens drei Kurse, Rest gezählt). */
    private fun notifyPossibleChange(summary: SourceChangeSummary?) {
        val counts = summary?.countsText()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val localized = localizedAppContext(applicationContext, AppLanguagePreferences(applicationContext).load())
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, localizedMessage(localized, message(106)), NotificationManager.IMPORTANCE_DEFAULT))
        val title = localizedMessage(localized, message(33))
        val short = localizedMessage(localized, counts?.let { message(774, it) } ?: message(107))
        val open = PendingIntent.getActivity(applicationContext, 0, AppEntryIntents.intent(applicationContext, AppEntry.SourceRange), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun base() = android.app.Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync).setContentTitle(title).setContentText(short).setContentIntent(open).setAutoCancel(true)
        // Kursnamen nur entsperrt: Sperrbildschirm zeigt die öffentliche Fassung mit reinen Zahlen.
        val digest = summary?.courseDigest().orEmpty().map { localizedMessage(localized, it) }
        val notification = base()
            .setVisibility(android.app.Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(base().build())
            .apply { if (digest.isNotEmpty()) setStyle(android.app.Notification.BigTextStyle().bigText((listOf(short) + digest).joinToString("\n"))) }
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "tum_source_check"
        const val NOTIFICATION_ID = 4102
    }
}
