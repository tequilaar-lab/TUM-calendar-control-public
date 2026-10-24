/*
 * Aufgabe: Schnelleinstellung „Auf Änderungen prüfen“: bei aktivierter Hintergrundprüfung sofort eine lesende
 *   Prüfung einreihen, sonst die Seite der Hintergrundprüfung öffnen; Untertitel zeigt den letzten Stand.
 * Ein-/Ausgabe: Antippen/Sichtbarwerden der Kachel -> einmalige Worker-Anfrage bzw. App-Einstieg; Kachelzustand.
 * Nebenwirkungen: Nur die bestehende lesende Prüfung (Ergebnis als Zahlenhinweis); nie Schreiben in Kalender.
 * Nicht zuständig: Prüflogik (PeriodicSourceCheckWorker), Einstellungen der Prüfung oder Navigation.
 */
package com.tequila.tumcalendarcontrol.shortcuts

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tequila.tumcalendarcontrol.i18n.AppLanguagePreferences
import com.tequila.tumcalendarcontrol.i18n.localizedAppContext
import com.tequila.tumcalendarcontrol.i18n.localizedMessage
import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.sync.PeriodicSourceCheckScheduler
import com.tequila.tumcalendarcontrol.sync.PrivateSyncConfigurationStore
import com.tequila.tumcalendarcontrol.sync.SourceCheckSettingsStore
import com.tequila.tumcalendarcontrol.ui.AppEntry
import com.tequila.tumcalendarcontrol.ui.AppEntryIntents
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SourceCheckTileService : TileService() {
    private fun text(key: Int, vararg args: Any?) = localizedMessage(localizedAppContext(this, AppLanguagePreferences(this).load()), message(key, *args))

    private fun ready(): Boolean = action(
        SourceCheckSettingsStore(this).loadSettings().enabled,
        runCatching { PrivateSyncConfigurationStore(this).load().hasTumSubscription }.getOrDefault(false),
    ) == TileAction.CHECK_NOW

    /** Was ein Antippen bewirkt: direkt prüfen nur bei aktivierter Prüfung und eingerichteter Quelle. */
    enum class TileAction { CHECK_NOW, OPEN_SETTINGS }

    companion object {
        fun action(checkEnabled: Boolean, sourceConfigured: Boolean): TileAction =
            if (checkEnabled && sourceConfigured) TileAction.CHECK_NOW else TileAction.OPEN_SETTINGS
    }

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val ready = ready()
        tile.label = text(873)
        // Aktionskachel ohne Ein/Aus-Zustand: immer „inaktiv“ (antippbar); der Untertitel erklärt den Stand.
        tile.state = Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (!ready) text(876) else SourceCheckSettingsStore(this).loadStatus().checkedAt
                ?.let { text(875, DateTimeFormatter.ofPattern("MM-dd HH:mm").format(it.atZone(ZoneId.systemDefault()))) }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (ready()) {
            PeriodicSourceCheckScheduler(this).runOnceNow()
            qsTile?.let { tile ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = text(874)
                tile.updateTile()
            }
            return
        }
        // Nicht eingerichtet: Seite der Hintergrundprüfung öffnen (Einschalten bleibt eine ausdrückliche Aktion dort).
        val intent = AppEntryIntents.intent(this, AppEntry.BackgroundCheck)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 2, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
