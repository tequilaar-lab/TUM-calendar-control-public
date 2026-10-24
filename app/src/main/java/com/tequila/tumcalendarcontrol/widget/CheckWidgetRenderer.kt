/*
 * Aufgabe: Widget „Auf Änderungen prüfen“ (ersetzt die Schnelleinstellung): letzten Stand zeigen und beim Antippen
 *   sofort eine lesende Prüfung einreihen – oder, wenn nicht eingerichtet, die Seite der Hintergrundprüfung öffnen.
 * Ein-/Ausgabe: Prüfeinstellungen/-stand, Größe in dp -> RemoteViews; Antippen -> einmalige Worker-Anfrage bzw. App-Einstieg.
 * Nebenwirkungen: Nur die bestehende lesende Prüfung und ein privater Zeitstempel „Prüfung gestartet“; nie Kalenderausgabe.
 * Nicht zuständig: Prüflogik (PeriodicSourceCheckWorker), Einstellungen der Prüfung oder Kurs-Widgets.
 */
package com.tequila.tumcalendarcontrol.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.tequila.tumcalendarcontrol.R
import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.sync.PeriodicSourceCheckScheduler
import com.tequila.tumcalendarcontrol.sync.PrivateSyncConfigurationStore
import com.tequila.tumcalendarcontrol.sync.SourceCheckResult
import com.tequila.tumcalendarcontrol.sync.SourceCheckSettingsStore
import com.tequila.tumcalendarcontrol.sync.SourceCheckStatus
import com.tequila.tumcalendarcontrol.ui.AppEntry
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CheckWidgetRenderer {
    internal const val ACTION_CHECK_NOW = "com.tequila.tumcalendarcontrol.widget.CHECK_NOW"
    private const val PREFS = "widget_check"
    private const val KEY_STARTED = "started_at"
    /** „Prüft…“ höchstens so lange anzeigen (danach gilt der gespeicherte Stand, auch wenn der Worker wartet). */
    private val CheckingWindow = Duration.ofMinutes(10)
    private val Stamp = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    /** Was ein Antippen bewirkt: direkt prüfen nur bei aktivierter Prüfung und eingerichteter Quelle. */
    enum class Action { CHECK_NOW, OPEN_SETTINGS }

    fun action(checkEnabled: Boolean, sourceConfigured: Boolean): Action =
        if (checkEnabled && sourceConfigured) Action.CHECK_NOW else Action.OPEN_SETTINGS

    /** Eingaben eines Zeichendurchgangs (aus den Speichern gelesen oder im Test vorgegeben). */
    data class State(val enabled: Boolean, val configured: Boolean, val status: SourceCheckStatus, val startedAt: Instant?, val now: Instant, val zone: ZoneId)

    fun load(context: Context, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): State {
        val store = SourceCheckSettingsStore(context)
        return State(
            enabled = store.loadSettings().enabled,
            configured = runCatching { PrivateSyncConfigurationStore(context).load().hasTumSubscription }.getOrDefault(false),
            status = store.loadStatus(),
            startedAt = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_STARTED, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
            now = now, zone = zone,
        )
    }

    /** Statuszeile: „Prüft…“ (kurz nach dem Antippen, solange kein neuerer Stand vorliegt), sonst letzter Stand. */
    fun statusText(state: State): String {
        if (action(state.enabled, state.configured) == Action.OPEN_SETTINGS) return message(897)
        val started = state.startedAt
        val checkedAt = state.status.checkedAt
        if (started != null && Duration.between(started, state.now) < CheckingWindow && (checkedAt == null || checkedAt < started)) return message(896)
        val result = when (state.status.result) {
            SourceCheckResult.NEVER -> return message(895)
            SourceCheckResult.UNCHANGED -> message(890)
            SourceCheckResult.CHANGED -> message(891)
            SourceCheckResult.FAILED -> message(892)
            SourceCheckResult.INITIAL -> message(893)
            SourceCheckResult.NEEDS_CONFIGURATION -> message(894)
        }
        return checkedAt?.let { message(889, Stamp.format(it.atZone(state.zone)), result) } ?: result
    }

    /**
     * Kompakte Statuszeile für 1×1 (Gerätefund: „Zuletzt …“ wurde abgeschnitten): zweizeilig Uhrzeit (heute) bzw.
     * Datum und Ergebnis; „Prüft…“, „nie geprüft“ und „aus“ unverändert kurz.
     */
    fun compactStatusText(state: State): String {
        if (action(state.enabled, state.configured) == Action.OPEN_SETTINGS) return message(876)
        val full = statusText(state)
        val checkedAt = state.status.checkedAt ?: return full
        if (state.status.result == SourceCheckResult.NEVER || full == message(896)) return full
        val local = checkedAt.atZone(state.zone)
        val stamp = if (local.toLocalDate() == state.now.atZone(state.zone).toLocalDate()) DateTimeFormatter.ofPattern("HH:mm").format(local) else DateTimeFormatter.ofPattern("MM-dd").format(local)
        val result = when (state.status.result) {
            SourceCheckResult.UNCHANGED -> message(890)
            SourceCheckResult.CHANGED -> message(891)
            SourceCheckResult.FAILED -> message(892)
            SourceCheckResult.INITIAL -> message(893)
            else -> message(894)
        }
        return com.tequila.tumcalendarcontrol.i18n.joinMessages(listOf(stamp, result), "\n")
    }

    /** Klein (unter 110 dp Breite oder 90 dp Höhe): nur Symbol und kompakter Status; sonst mit Titel. */
    fun render(context: Context, frame: WidgetFrame, state: State = load(context, frame.now, frame.zone)): RemoteViews {
        val text = ScheduleWidgetRenderer.WidgetTexts(context)
        val views = RemoteViews(context.packageName, R.layout.widget_check)
        ScheduleWidgetRenderer.resetForReapply(views, emptyList(), emptyList())
        views.setInt(R.id.widget_root, "setBackgroundResource", ScheduleWidgetRenderer.background(frame.settings))
        val compact = frame.widthDp < 110 || frame.heightDp < 90
        views.setViewVisibility(R.id.check_title, if (compact) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.check_title, text(message(873)))
        views.setTextViewText(R.id.check_status, text(if (compact) compactStatusText(state) else statusText(state)))
        val click = when (action(state.enabled, state.configured)) {
            Action.CHECK_NOW -> PendingIntent.getBroadcast(
                context, 3, Intent(context, ScheduleWidgetCheck::class.java).setAction(ACTION_CHECK_NOW),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            Action.OPEN_SETTINGS -> ScheduleWidgetRenderer.openApp(context, AppEntry.BackgroundCheck)
        }
        views.setOnClickPendingIntent(R.id.widget_root, click)
        return views
    }

    /** Antippen bei eingerichteter Prüfung: einmalige lesende Prüfung einreihen, „Prüft…“ anzeigen. */
    fun startCheck(context: Context) {
        val state = load(context)
        if (action(state.enabled, state.configured) != Action.CHECK_NOW) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_STARTED, Instant.now().toEpochMilli()).apply()
        PeriodicSourceCheckScheduler(context).runOnceNow()
        ScheduleWidgets.refreshAll(context)
    }
}
