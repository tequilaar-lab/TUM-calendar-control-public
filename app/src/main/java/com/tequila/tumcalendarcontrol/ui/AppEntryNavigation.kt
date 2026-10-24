/*
 * Aufgabe: Ein Einstiegsziel von außen (Widget, Hinweis, Verknüpfung) auf die Navigation anwenden, sodass die App immer
 *   auf der Zielseite landet – nie still auf der zuletzt benutzten Seite (Gerätefund 1.4.1).
 * Ein-/Ausgabe: CalendarControlState und AppEntry -> geöffnete Seite; Protokollzeile nur mit Art des Ziels und Seitenname.
 * Nebenwirkungen: Nur Navigation und eine inhaltsfreie Logzeile (Tag TumEntry, ohne Kurs-ID oder Titel).
 * Nicht zuständig: Intent-Kodierung (AppEntryIntents), Lebenszyklus der Activity oder Inhalte der Seiten.
 */
package com.tequila.tumcalendarcontrol.ui

import android.util.Log
import com.tequila.tumcalendarcontrol.i18n.message

/**
 * Ziel öffnen und die erreichte Seite zurückgeben. Kursdetails für einen Kurs, der nicht (mehr) im Verzeichnis ist,
 * öffnen stattdessen die Startseite mit Hinweis.
 */
fun CalendarControlState.applyEntry(entry: AppEntry): AppScreen {
    when (entry) {
        AppEntry.Overview -> open(AppScreen.OVERVIEW)
        AppEntry.SourceRange -> openSourceRange()
        is AppEntry.Course -> if (!openCourse(entry.courseId)) { open(AppScreen.OVERVIEW); feedback = message(906) }
        is AppEntry.WidgetSettings -> { editingWidgetId = entry.widgetId; open(AppScreen.WIDGETS) }
        is AppEntry.ManualEvents -> { entry.kind?.let { manualEventsInitialKind = it }; openManualEvents() }
        AppEntry.WritePreview -> prepareCalendarWrite()
        AppEntry.BackgroundCheck -> openBackgroundCheck()
        AppEntry.ClassLive -> open(AppScreen.CLASS_LIVE)
        is AppEntry.ManualEventDraftEntry -> { manualEventDraft = entry.draft; manualEventsInitialKind = entry.draft.kind; openManualEvents() }
    }
    // Inhaltsfrei: nur Art des Ziels und Seite (für Geräteprüfungen), keine Kurs-ID.
    runCatching { Log.i(LOG_TAG, "${entry::class.simpleName} -> $screen") }
    return screen
}

private const val LOG_TAG = "TumEntry"
