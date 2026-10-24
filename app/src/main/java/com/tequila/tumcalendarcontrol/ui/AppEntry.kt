/*
 * Aufgabe: Einstiegsziele von außen (Hinweis der Quellenprüfung, Startbildschirm-Widgets) als Intent kodieren und lesen.
 * Ein-/Ausgabe: Ziel -> expliziter Intent auf MainActivity; Intent (und gesicherter Zustand) -> Ziel oder null.
 * Nebenwirkungen: Keine; nur Intent-Aufbau und -Auswertung. Ziele navigieren ausschließlich, sie lösen keine Aktion aus.
 * Nicht zuständig: Navigation selbst (CalendarControlState), PendingIntent-Flags der Aufrufer oder Widget-Darstellung.
 */
package com.tequila.tumcalendarcontrol.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.tequila.tumcalendarcontrol.MainActivity
import com.tequila.tumcalendarcontrol.sync.ManualEventKind

/** Wohin die App beim Öffnen springt; reine Navigation, auch bei Aufruf durch fremde Apps unbedenklich. */
sealed interface AppEntry {
    /** Startseite (Übersicht) – Ziel der Kurs-Widgets außerhalb einzelner Termine. */
    data object Overview : AppEntry
    /** Seite „Kurse lesen“ – erneutes Lesen bleibt eine ausdrückliche, ggf. bestätigte Aktion. */
    data object SourceRange : AppEntry
    /** Kursdetails anhand der Quell-UID; unbekannte Kurse lassen die aktuelle Seite unverändert. */
    data class Course(val courseId: String) : AppEntry
    /** Einstellungsseite der Widgets; widgetId = gleich dieses platzierte Widget bearbeiten (aus der Einrichtung), null = Liste. */
    data class WidgetSettings(val widgetId: Int? = null) : AppEntry
    /** Campustermine; kind gibt die vorgewählte Art vor (z. B. Prüfung), null = unverändert. */
    data class ManualEvents(val kind: ManualEventKind? = null) : AppEntry
    /** Schreibvorschau berechnen und zeigen; geschrieben wird erst nach „Bestätigen und schreiben“. */
    data object WritePreview : AppEntry
    /** Seite der Hintergrundprüfung (aus der Schnelleinstellung, wenn die Prüfung noch nicht eingerichtet ist). */
    data object BackgroundCheck : AppEntry
}

object AppEntryIntents {
    private const val EXTRA_TARGET = "com.tequila.tumcalendarcontrol.entry"
    private const val EXTRA_COURSE = "com.tequila.tumcalendarcontrol.entry.course"
    private const val TARGET_OVERVIEW = "overview"
    private const val TARGET_SOURCE_RANGE = "source-range"
    private const val TARGET_COURSE = "course"
    private const val TARGET_WIDGETS = "widgets"
    private const val TARGET_EVENTS = "events"
    private const val TARGET_WRITE = "write-preview"
    private const val TARGET_CHECK = "background-check"
    private const val EXTRA_KIND = "com.tequila.tumcalendarcontrol.entry.kind"
    private const val EXTRA_WIDGET_ID = "com.tequila.tumcalendarcontrol.entry.widget"

    fun intent(context: Context, entry: AppEntry?): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            when (entry) {
                AppEntry.Overview -> putExtra(EXTRA_TARGET, TARGET_OVERVIEW)
                AppEntry.SourceRange -> putExtra(EXTRA_TARGET, TARGET_SOURCE_RANGE)
                is AppEntry.Course -> { putExtra(EXTRA_TARGET, TARGET_COURSE); putExtra(EXTRA_COURSE, entry.courseId) }
                is AppEntry.WidgetSettings -> { putExtra(EXTRA_TARGET, TARGET_WIDGETS); entry.widgetId?.let { putExtra(EXTRA_WIDGET_ID, it) } }
                is AppEntry.ManualEvents -> { putExtra(EXTRA_TARGET, TARGET_EVENTS); entry.kind?.let { putExtra(EXTRA_KIND, it.name) } }
                AppEntry.WritePreview -> putExtra(EXTRA_TARGET, TARGET_WRITE)
                AppEntry.BackgroundCheck -> putExtra(EXTRA_TARGET, TARGET_CHECK)
                null -> Unit
            }
        }

    /**
     * Ziel beim Erzeugen der Activity: neu gestartet -> aus dem Intent; nach Neuerstellung nur, wenn das Ziel vorher noch
     * nicht angewandt war (Gerätefund: nach einem Update wurde die Activity vor der ersten Darstellung neu erstellt und
     * das Ziel ging verloren – die App blieb auf der Startseite).
     */
    fun initial(intent: Intent?, savedState: Bundle?): AppEntry? =
        if (savedState == null || savedState.getBoolean(STATE_PENDING)) parse(intent) else null

    /** Merkt für eine Neuerstellung, ob noch ein Ziel aussteht. */
    fun save(outState: Bundle, pending: AppEntry?) = outState.putBoolean(STATE_PENDING, pending != null)

    private const val STATE_PENDING = "com.tequila.tumcalendarcontrol.entry.pending"

    fun parse(intent: Intent?): AppEntry? = when (intent?.getStringExtra(EXTRA_TARGET)) {
        TARGET_OVERVIEW -> AppEntry.Overview
        TARGET_SOURCE_RANGE -> AppEntry.SourceRange
        TARGET_COURSE -> intent.getStringExtra(EXTRA_COURSE)?.takeIf(String::isNotBlank)?.let(AppEntry::Course)
        TARGET_WIDGETS -> AppEntry.WidgetSettings(intent.getIntExtra(EXTRA_WIDGET_ID, -1).takeIf { it >= 0 })
        TARGET_WRITE -> AppEntry.WritePreview
        TARGET_CHECK -> AppEntry.BackgroundCheck
        TARGET_EVENTS -> AppEntry.ManualEvents(intent.getStringExtra(EXTRA_KIND)?.let { name -> ManualEventKind.entries.firstOrNull { it.name == name } })
        else -> null
    }
}
