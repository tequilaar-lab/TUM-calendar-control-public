/*
 * Aufgabe: Die Live-Kursbenachrichtigung bauen, zeigen und entfernen: bevorstehend mit Countdown bis Beginn, laufend
 *   mit Restzeit und Fortschritt; ab Android 16 QPR2 (API 36.1) als „Live-Update“ angefragt (Statusleisten-Chip),
 *   sonst normal dauerhaft.
 * Ein-/Ausgabe: LiveState, Kurzname, Zeit -> Notification im Kanal „tum_class_live“; Antippen -> Kursdetails, „Karte“ -> nav.tum.de.
 * Nebenwirkungen: Nur diese eine Benachrichtigung und ihr Kanal; kein Netzwerk, keine Kalender- oder Regeländerung.
 * Nicht zuständig: Planung (ClassLivePlanner), Wecker (ClassLiveScheduler) oder Berechtigungsanfrage (Oberfläche).
 */
package com.tequila.tumcalendarcontrol.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.domain.RoomLink
import com.tequila.tumcalendarcontrol.i18n.AppLanguagePreferences
import com.tequila.tumcalendarcontrol.i18n.localizedAppContext
import com.tequila.tumcalendarcontrol.i18n.localizedMessage
import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.ui.AppEntry
import com.tequila.tumcalendarcontrol.ui.AppEntryIntents
import com.tequila.tumcalendarcontrol.widget.WidgetEvent
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ClassLiveNotifier {
    private const val CHANNEL_ID = "tum_class_live"
    private const val NOTIFICATION_ID = 4103
    /** Eigene ID der Testbenachrichtigung: Neuplanung beim Verlassen der App entfernt sie nicht (Gerätefund). */
    private const val TEST_NOTIFICATION_ID = 4104
    private val Time = DateTimeFormatter.ofPattern("HH:mm")

    /** Sichtbare Texte (testbar ohne Benachrichtigungsdienst): Titel, Text, kurzer Chiptext. */
    data class Content(val title: String, val text: String, val chip: String)

    fun content(state: LiveState, name: String, zone: ZoneId, translate: (String) -> String = { it }): Content {
        val event = state.event
        val start = Time.format(event.startsAt.atZone(zone))
        val end = Time.format(event.endsAt.atZone(zone))
        val room = event.location.trim()
        return when (state.phase) {
            LivePhase.UPCOMING -> Content(translate(message(931, start, name)), room, start)
            LivePhase.IN_PROGRESS -> Content(translate(message(932, name)), translate(if (room.isEmpty()) message(934, end) else message(933, room, end)), end)
        }
    }

    /** Fortschritt eines laufenden Kurses in Prozent (0–100). */
    fun progressPercent(state: LiveState, now: Instant): Int {
        val total = Duration.between(state.event.startsAt, state.event.endsAt).toMillis().coerceAtLeast(1)
        return (Duration.between(state.event.startsAt, now).toMillis() * 100 / total).toInt().coerceIn(0, 100)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * Live-Updates anfragbar: setRequestPromotedOngoing und die Berechtigung gibt es erst ab Android 16 QPR2 (API 36.1);
     * ProgressStyle und der Chiptext schon ab 36.0.
     */
    internal fun promotionAvailable(sdkInt: Int = Build.VERSION.SDK_INT, sdkIntFull: () -> Int = { Build.VERSION.SDK_INT_FULL }): Boolean =
        sdkInt >= Build.VERSION_CODES.BAKLAVA && sdkIntFull() >= Build.VERSION_CODES_FULL.BAKLAVA_1

    /** Ob das System Live-Updates dieser App zulässt (ab API 36.1 und vom Nutzer nicht abgeschaltet); sonst false. */
    fun canPromote(context: Context): Boolean =
        promotionAvailable() && context.getSystemService(NotificationManager::class.java)?.canPostPromotedNotifications() == true

    /** entry = Ziel beim Antippen (Standard: Kursdetails; Testbenachrichtigung: Einstellungsseite). */
    fun show(
        context: Context, state: LiveState, name: String, now: Instant, zone: ZoneId,
        entry: AppEntry = AppEntry.Course(state.event.courseId), id: Int = NOTIFICATION_ID,
    ) {
        if (!canPost(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val localized = localizedAppContext(context, AppLanguagePreferences(context).load())
        val translate = { value: String -> localizedMessage(localized, value) }
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, translate(message(936)), NotificationManager.IMPORTANCE_DEFAULT))
        val content = content(state, name, zone, translate)
        val event = state.event
        val target = if (state.phase == LivePhase.UPCOMING) event.startsAt else event.endsAt
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_EVENT)
            // Systemuhr zählt bis Beginn bzw. Ende herunter; nach Kursende verschwindet die Meldung von selbst.
            .setWhen(target.toEpochMilli())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter(Duration.between(now, event.endsAt).toMillis().coerceAtLeast(1))
            .setContentIntent(open(context, AppEntryIntents.intent(context, entry), 0))
        RoomLink.url(event.location)?.let { url ->
            builder.addAction(Notification.Action.Builder(null, translate(message(935)), open(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)), 1)).build())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) promote(builder, state, content, now)
        manager.notify(id, builder.build())
    }

    /** Android 16: kurzer Chiptext (Uhrzeit), laufend mit Fortschrittsbalken; ab 36.1 zusätzlich Live-Update anfragen. */
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun promote(builder: Notification.Builder, state: LiveState, content: Content, now: Instant) {
        builder.setShortCriticalText(content.chip)
        if (promotionAvailable()) builder.setRequestPromotedOngoing(true)
        if (state.phase == LivePhase.IN_PROGRESS) builder.setStyle(
            Notification.ProgressStyle().setProgressSegments(listOf(Notification.ProgressStyle.Segment(100))).setProgress(progressPercent(state, now)),
        )
    }

    /**
     * Testbenachrichtigung mit synthetischem laufendem Kurs (Beginn vor 1 Minute, Ende in 2 Minuten); zeigt auf dem Gerät
     * sofort, ob das System Live-Updates hochstuft (HONOR: Kapsel). Antippen öffnet die Einstellungsseite.
     */
    fun showTest(context: Context, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) {
        val sample = WidgetEvent("sample", "sample", message(943), now.minus(Duration.ofMinutes(1)), now.plus(Duration.ofMinutes(2)), AttendanceState.MUST_ATTEND, "Hörsaal (0000.00.000)")
        val localized = localizedAppContext(context, AppLanguagePreferences(context).load())
        show(context, LiveState(sample, LivePhase.IN_PROGRESS), localizedMessage(localized, message(943)), now, zone, AppEntry.ClassLive, TEST_NOTIFICATION_ID)
    }

    /** Ob die Testbenachrichtigung vom System als Live-Update hochgestuft wurde (ab API 36); null = keine aktiv. */
    fun isPromoted(context: Context): Boolean? {
        val active = context.getSystemService(NotificationManager::class.java)?.activeNotifications?.firstOrNull { it.id == TEST_NOTIFICATION_ID } ?: return null
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && active.notification.flags and Notification.FLAG_PROMOTED_ONGOING != 0
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    private fun open(context: Context, intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(context, 40 + requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}
