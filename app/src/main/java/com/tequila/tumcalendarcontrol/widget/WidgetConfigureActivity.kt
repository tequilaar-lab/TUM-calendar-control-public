/*
 * Aufgabe: Einrichtungs-/Neukonfigurationseinstieg der Widgets: Einrichtung sofort bestätigen und die App auf der
 *   Seite „Startbildschirm-Widgets“ öffnen (gemeinsame Einstellungen aller Widgets).
 * Ein-/Ausgabe: Systemaufruf mit Widget-ID -> RESULT_OK mit derselben ID; Start der App mit Einstiegsziel.
 * Nebenwirkungen: Nur Aktivitätsstart; keine Daten, Netzwerke oder Kalender.
 * Nicht zuständig: Einstellungsoberfläche selbst oder Zeichnen der Widgets.
 */
package com.tequila.tumcalendarcontrol.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import com.tequila.tumcalendarcontrol.ui.AppEntry
import com.tequila.tumcalendarcontrol.ui.AppEntryIntents

class WidgetConfigureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Einrichtung ist optional: sofort bestätigen, damit das Widget auch ohne Änderung platziert bleibt.
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        // Gleich dieses Widget bearbeiten (eigene Einstellungen und passende Vorschau).
        startActivity(AppEntryIntents.intent(this, AppEntry.WidgetSettings(id.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID })))
        ScheduleWidgets.refreshAll(this)
        finish()
    }
}
