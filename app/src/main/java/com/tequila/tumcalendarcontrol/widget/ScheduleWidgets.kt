/*
 * Aufgabe: Die Startbildschirm-Widgets (klein „Wohin jetzt“, mittel „Heute“, groß „Woche“, „Countdown“) bereitstellen und
 *   aktualisieren: Daten laden, je Instanz nach Größe zeichnen, nächste Aktualisierung grob vormerken.
 * Ein-/Ausgabe: Systemereignisse (Aktualisierung, Größenänderung, Zeit/Zeitzone/Sprache) und App-Aufrufe -> RemoteViews.
 * Nebenwirkungen: Liest nur private Zuordnungen, eigene Termine und Widget-Einstellungen; setzt einen nicht weckenden, ungenauen Alarm.
 *   Kein Netzwerk, kein Kalenderzugriff, keine Schreibvorgänge an Regeln oder Kalendern.
 * Nicht zuständig: Inhalt/Layout (Model, Renderer) oder Einstellungsoberfläche.
 */
package com.tequila.tumcalendarcontrol.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.tequila.tumcalendarcontrol.sync.RuleLedgerPreferences
import com.tequila.tumcalendarcontrol.sync.DeadlineCountdown
import com.tequila.tumcalendarcontrol.sync.ManualEventKind
import com.tequila.tumcalendarcontrol.sync.ManualScheduleItem
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Gemeinsame Basis: zeichnet alle Instanzen der jeweiligen Größe; Systemzeit-/Sprachwechsel lösen Neuzeichnen aus. */
abstract class ScheduleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = ScheduleWidgets.refreshAll(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        ScheduleWidgets.refreshAll(context)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED, ScheduleWidgets.ACTION_REFRESH -> ScheduleWidgets.refreshAll(context)
            else -> super.onReceive(context, intent)
        }
    }
}

/** Drei Einträge in der Widget-Liste des Startbildschirms; Größe ergibt sich aus der Klasse (siehe providers). */
class ScheduleWidgetSmall : ScheduleWidgetProvider()
class ScheduleWidgetMedium : ScheduleWidgetProvider()
class ScheduleWidgetLarge : ScheduleWidgetProvider()
/** Countdown bis zu Prüfungen und Fristen (eigene Termine, nicht nur geschriebene). */
class ScheduleWidgetCountdown : ScheduleWidgetProvider()

object ScheduleWidgets {
    internal const val ACTION_REFRESH = "com.tequila.tumcalendarcontrol.widget.REFRESH"
    private val providers = listOf(
        ScheduleWidgetSmall::class.java to WidgetSize.SMALL,
        ScheduleWidgetMedium::class.java to WidgetSize.MEDIUM,
        ScheduleWidgetLarge::class.java to WidgetSize.LARGE,
        ScheduleWidgetCountdown::class.java to WidgetSize.COUNTDOWN,
    )

    /**
     * Alle platzierten Widgets neu zeichnen (nach Ausgabe, Einstellungsänderung, App im Hintergrund, Systemereignis).
     * Ohne platzierte Widgets passiert nichts außer dem Laden der Daten; Fehler beim Laden ergeben „noch nichts geschrieben“.
     */
    fun refreshAll(context: Context, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app) ?: return
        val placed = providers.map { (type, size) -> size to manager.getAppWidgetIds(ComponentName(app, type)) }.filter { it.second.isNotEmpty() }
        if (placed.isEmpty()) return
        val ledger = runCatching { RuleLedgerPreferences(app).load() }.getOrNull()
        val events = ledger?.let { ScheduleWidgetModel.eventsFrom(it.mappings) }.orEmpty()
        val manual = ledger?.manualEvents.orEmpty()
        val settings = WidgetPreferences(app).load()
        placed.forEach { (size, ids) ->
            ids.forEach { id ->
                val options = manager.getAppWidgetOptions(id)
                // Hochformat: Breite = kleinste, Höhe = größte gemeldete Abmessung.
                val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: defaultWidth(size)
                val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: defaultHeight(size)
                manager.updateAppWidget(id, ScheduleWidgetRenderer.render(app, size, WidgetFrame(events, events.isNotEmpty(), settings, now, zone, width, height, manual)))
            }
        }
        val courses = ScheduleWidgetModel.nextRefresh(ScheduleWidgetModel.visible(events, settings), now, zone)
        scheduleNext(app, listOfNotNull(courses, countdownRefresh(manual, now)).min())
    }

    /** Countdown: Zielzeiten (und Prüfungsende) als Ränder; in der letzten Stunde alle 5 Minuten. */
    internal fun countdownRefresh(items: List<ManualScheduleItem>, now: Instant): Instant? {
        val boundaries = items.flatMap { listOfNotNull(DeadlineCountdown.target(it), if (it.kind == ManualEventKind.EXAM) it.endsAt else null) }.filter { it > now }
        val next = boundaries.minOrNull() ?: return null
        return if (Duration.between(now, next) <= Duration.ofHours(1)) minOf(next, now.plus(Duration.ofMinutes(5))) else next
    }

    /** Ungenauer, nicht weckender Alarm (keine Sonderberechtigung, keine Pünktlichkeitszusage); ersetzt den vorherigen. */
    private fun scheduleNext(context: Context, at: Instant) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, ScheduleWidgetSmall::class.java).setAction(ACTION_REFRESH)
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        alarms.set(AlarmManager.RTC, at.toEpochMilli(), pending)
    }

    internal fun defaultWidth(size: WidgetSize): Int = if (size == WidgetSize.SMALL || size == WidgetSize.COUNTDOWN) 150 else 320
    internal fun defaultHeight(size: WidgetSize): Int = when (size) { WidgetSize.SMALL, WidgetSize.COUNTDOWN -> 150; WidgetSize.MEDIUM -> 170; WidgetSize.LARGE -> 340 }

    /** Ob der Startbildschirm das direkte Hinzufügen anbietet (Systemdialog, Nutzer bestätigt). */
    fun canRequestPin(context: Context): Boolean = AppWidgetManager.getInstance(context)?.isRequestPinAppWidgetSupported == true

    /** Fragt den Startbildschirm, ein Widget der Größe hinzuzufügen; der Nutzer bestätigt im Systemdialog. */
    fun requestPin(context: Context, size: WidgetSize): Boolean {
        val type = providers.first { it.second == size }.first
        return AppWidgetManager.getInstance(context)?.requestPinAppWidget(ComponentName(context, type), null, null) == true
    }
}
