/*
 * Aufgabe: Dynamische App-Verknüpfungen (langes Drücken auf das Symbol) veröffentlichen: Neu lesen, Prüfung/Frist
 *   hinzufügen, Ausgabe prüfen, Widget-Einstellungen. Dynamisch, weil das Startsymbol über mehrere Aliase wechselt.
 * Ein-/Ausgabe: Context und App-Sprache -> Liste der ShortcutInfoCompat bzw. deren Veröffentlichung.
 * Nebenwirkungen: Ersetzt nur die eigenen dynamischen Verknüpfungen; jede führt ausschließlich zu einer Seite (AppEntry).
 * Nicht zuständig: Navigation selbst, Lesen, Schreiben oder Widgets.
 */
package com.tequila.tumcalendarcontrol.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.tequila.tumcalendarcontrol.R
import com.tequila.tumcalendarcontrol.i18n.AppLanguagePreferences
import com.tequila.tumcalendarcontrol.i18n.localizedAppContext
import com.tequila.tumcalendarcontrol.i18n.localizedMessage
import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.sync.ManualEventKind
import com.tequila.tumcalendarcontrol.ui.AppEntry
import com.tequila.tumcalendarcontrol.ui.AppEntryIntents

object AppShortcuts {
    /** Reihenfolge = Anzeige im Menü (oben zuerst); höchstens vier, damit alle Startbildschirme sie zeigen. */
    fun build(context: Context): List<ShortcutInfoCompat> {
        val localized = localizedAppContext(context, AppLanguagePreferences(context).load())
        fun text(key: Int) = localizedMessage(localized, message(key))
        fun shortcut(id: String, short: Int, long: Int, icon: Int, entry: AppEntry, rank: Int) = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(text(short))
            .setLongLabel(text(long))
            .setIcon(IconCompat.createWithResource(context, icon))
            // Verknüpfungen brauchen eine Aktion; das Ziel steckt wie bei Hinweisen/Widgets in den Extras.
            .setIntent(AppEntryIntents.intent(context, entry).setAction(Intent.ACTION_VIEW))
            .setRank(rank)
            .build()
        return listOf(
            shortcut("read", 865, 866, R.drawable.ic_shortcut_refresh, AppEntry.SourceRange, 0),
            shortcut("exam", 867, 868, R.drawable.ic_shortcut_exam, AppEntry.ManualEvents(ManualEventKind.EXAM), 1),
            shortcut("write", 869, 870, R.drawable.ic_shortcut_write, AppEntry.WritePreview, 2),
            shortcut("widgets", 871, 872, R.drawable.ic_shortcut_widgets, AppEntry.WidgetSettings(), 3),
        )
    }

    /** Beim Start (und nach Sprachwechsel) neu setzen; Fehler des Startbildschirms sind unkritisch. */
    fun publish(context: Context) {
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, build(context)) }
    }
}
