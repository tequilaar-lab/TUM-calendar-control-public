/*
 * Aufgabe: Live-Kursbenachrichtigung auf den aktuellen Stand bringen: geschriebene Termine und Einstellungen laden,
 *   planen, Benachrichtigung zeigen oder entfernen und den nächsten ungenauen Wecker stellen (ersetzt den vorherigen).
 * Ein-/Ausgabe: Context und Zeit -> Benachrichtigung, AlarmManager-Fenster auf ClassLiveReceiver.
 * Nebenwirkungen: Liest private Zuordnungen, Kurznamen und Einstellungen; setzt bzw. löscht einen weckenden, ungenauen
 *   Wecker ohne Sonderberechtigung (keine Pünktlichkeitszusage). Kein Netzwerk, keine Kalender- oder Regeländerung.
 * Nicht zuständig: Inhalt der Benachrichtigung (ClassLiveNotifier) oder Planungsregeln (ClassLivePlanner).
 */
package com.tequila.tumcalendarcontrol.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.tequila.tumcalendarcontrol.sync.RuleLedgerPreferences
import com.tequila.tumcalendarcontrol.widget.CourseShortNames
import com.tequila.tumcalendarcontrol.widget.ScheduleWidgetModel
import java.time.Instant
import java.time.ZoneId

object ClassLiveScheduler {
    internal const val ACTION_REFRESH = "com.tequila.tumcalendarcontrol.notify.CLASS_LIVE_REFRESH"

    /** Aufruf nach Einstellungsänderung, beim Verlassen der App, durch den Wecker und bei Zeit-/Zeitzonenwechsel. */
    fun refresh(context: Context, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) {
        val app = context.applicationContext
        val settings = ClassLiveSettingsStore(app).load()
        val events = if (settings.enabled) runCatching { ScheduleWidgetModel.eventsFrom(RuleLedgerPreferences(app).load().mappings) }.getOrDefault(emptyList()) else emptyList()
        val plan = ClassLivePlanner.plan(events, settings, now)
        val current = plan.current
        if (current == null) ClassLiveNotifier.cancel(app)
        else ClassLiveNotifier.show(app, current, CourseShortNames.resolve(CourseShortNames(app).load(), events)[current.event.courseId] ?: current.event.title, now, zone)
        schedule(app, plan)
    }

    /**
     * Ungenaues Fenster (Android dehnt kürzere Fenster ohnehin auf etwa 10 Minuten): Hinweisbeginn im Fenster davor, damit
     * er nie zu spät kommt; Beginn/Ende im Fenster danach. Ohne nächsten Zeitpunkt wird der Wecker gelöscht.
     */
    private fun schedule(context: Context, plan: LivePlan) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context, 0, Intent(context, ClassLiveReceiver::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val at = plan.nextCheck ?: run { alarms.cancel(pending); return }
        val window = ClassLivePlanner.EARLY_TOLERANCE
        val start = if (plan.early) at.minus(window) else at
        alarms.setWindow(AlarmManager.RTC_WAKEUP, start.toEpochMilli(), window.toMillis(), pending)
    }
}
