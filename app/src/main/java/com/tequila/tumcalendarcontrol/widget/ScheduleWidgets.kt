/*
 * Aufgabe: Die Startbildschirm-Widgets (klein „Wohin jetzt“, mittel „Heute“, groß „Woche“, „Countdown“, Prüf-Widget,
 *   „Stundenplan“) bereitstellen und aktualisieren: Daten laden, je Instanz nach Größe zeichnen, nächste Aktualisierung grob vormerken.
 * Ein-/Ausgabe: Systemereignisse (Aktualisierung, Größenänderung, Zeit/Zeitzone/Sprache) und App-Aufrufe -> RemoteViews.
 * Nebenwirkungen: Liest nur private Zuordnungen, eigene Termine, Widget-Einstellungen und Kurznamen; setzt einen nicht
 *   weckenden, ungenauen Alarm.
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
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
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

    /** Entfernte Widgets: ihre eigenen Einstellungen löschen. */
    override fun onDeleted(context: Context, ids: IntArray) {
        val preferences = WidgetPreferences(context)
        ids.forEach(preferences::remove)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED, ScheduleWidgets.ACTION_REFRESH -> ScheduleWidgets.refreshAll(context)
            CheckWidgetRenderer.ACTION_CHECK_NOW -> CheckWidgetRenderer.startCheck(context)
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
/** „Auf Änderungen prüfen“ (1×1 bis 2×2); ersetzt die frühere Schnelleinstellung. */
class ScheduleWidgetCheck : ScheduleWidgetProvider()
/** Stundenplan: Kalenderwoche als Raster nach Uhrzeit (4×3 und größer). */
class ScheduleWidgetGrid : ScheduleWidgetProvider()

object ScheduleWidgets {
    internal const val ACTION_REFRESH = "com.tequila.tumcalendarcontrol.widget.REFRESH"
    private val providers = listOf(
        ScheduleWidgetSmall::class.java to WidgetSize.SMALL,
        ScheduleWidgetMedium::class.java to WidgetSize.MEDIUM,
        ScheduleWidgetLarge::class.java to WidgetSize.LARGE,
        ScheduleWidgetCountdown::class.java to WidgetSize.COUNTDOWN,
        ScheduleWidgetCheck::class.java to WidgetSize.CHECK,
        ScheduleWidgetGrid::class.java to WidgetSize.GRID,
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
        val preferences = WidgetPreferences(app)
        val settings = preferences.load()
        val shortNames = CourseShortNames(app).load()
        placed.forEach { (size, ids) ->
            ids.forEach { id ->
                // Jedes platzierte Widget mit eigenen Einstellungen (neue: Standard, klein/Countdown quadratisch).
                val own = preferences.load(id, size)
                fun frame(width: Int, height: Int) = WidgetFrame(events, events.isNotEmpty(), own, now, zone, width, height, manual, shortNames)
                manager.updateAppWidget(id, viewsFor(app, size, manager.getAppWidgetOptions(id), ::frame))
            }
        }
        val courses = ScheduleWidgetModel.nextRefresh(ScheduleWidgetModel.visible(events, settings), now, zone)
        scheduleNext(app, listOfNotNull(courses, countdownRefresh(manual, now)).min())
    }

    /**
     * Ab Android 12 je gemeldeter Größe eine eigene Darstellung; der Startbildschirm wählt passend zur tatsächlichen
     * Größe (keine eigene Schätzung, die je nach Auslöser anders ausfallen kann). Davor: Hochformat-Abmessungen.
     */
    internal fun viewsFor(context: Context, size: WidgetSize, options: Bundle, frame: (Int, Int) -> WidgetFrame): RemoteViews {
        val sizes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty().distinct().take(MAX_SIZES)
        } else emptyList()
        if (sizes.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return RemoteViews(sizes.associateWith { ScheduleWidgetRenderer.render(context, size, frame(it.width.toInt(), it.height.toInt())) })
        }
        val (width, height) = portraitSize(options, size)
        return ScheduleWidgetRenderer.render(context, size, frame(width, height))
    }

    /** Platziertes Widget mit Art und (Hochformat-)Größe in dp für Einstellungsliste und Vorschau. */
    data class PlacedWidget(val id: Int, val size: WidgetSize, val widthDp: Int, val heightDp: Int)

    /** Alle platzierten Widgets dieser App, nach Art und ID geordnet. */
    fun placed(context: Context): List<PlacedWidget> {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app) ?: return emptyList()
        return providers.flatMap { (type, size) ->
            manager.getAppWidgetIds(ComponentName(app, type)).sorted().map { id ->
                val (width, height) = portraitSize(manager.getAppWidgetOptions(id), size)
                PlacedWidget(id, size, width, height)
            }
        }
    }

    /** Hochformatgröße: ab Android 12 die erste gemeldete Größe, sonst minWidth × maxHeight, sonst Standard. */
    internal fun portraitSize(options: Bundle, size: WidgetSize): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)?.firstOrNull()?.let { return it.width.toInt() to it.height.toInt() }
        }
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: defaultWidth(size)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: defaultHeight(size)
        return width to height
    }

    /** RemoteViews erlaubt höchstens 16 Größen in einer Zuordnung. */
    private const val MAX_SIZES = 16

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

    internal fun defaultWidth(size: WidgetSize): Int = when (size) { WidgetSize.SMALL, WidgetSize.COUNTDOWN -> 150; WidgetSize.CHECK -> 80; else -> 320 }
    internal fun defaultHeight(size: WidgetSize): Int = when (size) { WidgetSize.SMALL, WidgetSize.COUNTDOWN -> 150; WidgetSize.CHECK -> 100; WidgetSize.MEDIUM -> 170; WidgetSize.LARGE -> 340; WidgetSize.GRID -> 250 }

    /** Ob der Startbildschirm das direkte Hinzufügen anbietet (Systemdialog, Nutzer bestätigt). */
    fun canRequestPin(context: Context): Boolean = AppWidgetManager.getInstance(context)?.isRequestPinAppWidgetSupported == true

    /** Fragt den Startbildschirm, ein Widget der Größe hinzuzufügen; der Nutzer bestätigt im Systemdialog. */
    fun requestPin(context: Context, size: WidgetSize): Boolean {
        val type = providers.first { it.second == size }.first
        return AppWidgetManager.getInstance(context)?.requestPinAppWidget(ComponentName(context, type), null, null) == true
    }
}
