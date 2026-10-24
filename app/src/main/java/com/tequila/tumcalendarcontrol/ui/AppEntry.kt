/*
 * Aufgabe: Einstiegsziele von außen (Hinweis der Quellenprüfung, Startbildschirm-Widgets) als Intent kodieren und lesen.
 * Ein-/Ausgabe: Ziel -> expliziter Intent auf MainActivity; Intent -> Ziel oder null.
 * Nebenwirkungen: Keine; nur Intent-Aufbau und -Auswertung. Ziele navigieren ausschließlich, sie lösen keine Aktion aus.
 * Nicht zuständig: Navigation selbst (CalendarControlState), PendingIntent-Flags der Aufrufer oder Widget-Darstellung.
 */
package com.tequila.tumcalendarcontrol.ui

import android.content.Context
import android.content.Intent
import com.tequila.tumcalendarcontrol.MainActivity
import com.tequila.tumcalendarcontrol.sync.ManualEventKind

/** Wohin die App beim Öffnen springt; reine Navigation, auch bei Aufruf durch fremde Apps unbedenklich. */
sealed interface AppEntry {
    /** Seite „Kurse lesen“ – erneutes Lesen bleibt eine ausdrückliche, ggf. bestätigte Aktion. */
    data object SourceRange : AppEntry
    /** Kursdetails anhand der Quell-UID; unbekannte Kurse lassen die aktuelle Seite unverändert. */
    data class Course(val courseId: String) : AppEntry
    /** Einstellungsseite der Startbildschirm-Widgets (aus der Widget-Einrichtung). */
    data object WidgetSettings : AppEntry
    /** Campustermine; kind gibt die vorgewählte Art vor (z. B. Prüfung), null = unverändert. */
    data class ManualEvents(val kind: ManualEventKind? = null) : AppEntry
}

object AppEntryIntents {
    private const val EXTRA_TARGET = "com.tequila.tumcalendarcontrol.entry"
    private const val EXTRA_COURSE = "com.tequila.tumcalendarcontrol.entry.course"
    private const val TARGET_SOURCE_RANGE = "source-range"
    private const val TARGET_COURSE = "course"
    private const val TARGET_WIDGETS = "widgets"
    private const val TARGET_EVENTS = "events"
    private const val EXTRA_KIND = "com.tequila.tumcalendarcontrol.entry.kind"

    fun intent(context: Context, entry: AppEntry?): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            when (entry) {
                AppEntry.SourceRange -> putExtra(EXTRA_TARGET, TARGET_SOURCE_RANGE)
                is AppEntry.Course -> { putExtra(EXTRA_TARGET, TARGET_COURSE); putExtra(EXTRA_COURSE, entry.courseId) }
                AppEntry.WidgetSettings -> putExtra(EXTRA_TARGET, TARGET_WIDGETS)
                is AppEntry.ManualEvents -> { putExtra(EXTRA_TARGET, TARGET_EVENTS); entry.kind?.let { putExtra(EXTRA_KIND, it.name) } }
                null -> Unit
            }
        }

    fun parse(intent: Intent?): AppEntry? = when (intent?.getStringExtra(EXTRA_TARGET)) {
        TARGET_SOURCE_RANGE -> AppEntry.SourceRange
        TARGET_COURSE -> intent.getStringExtra(EXTRA_COURSE)?.takeIf(String::isNotBlank)?.let(AppEntry::Course)
        TARGET_WIDGETS -> AppEntry.WidgetSettings
        TARGET_EVENTS -> AppEntry.ManualEvents(intent.getStringExtra(EXTRA_KIND)?.let { name -> ManualEventKind.entries.firstOrNull { it.name == name } })
        else -> null
    }
}
