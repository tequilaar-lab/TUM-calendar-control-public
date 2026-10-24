/*
 * Aufgabe: Verknüpfungen und Schnelleinstellung prüfen: vier Einträge mit Ziel (nur Navigation) in App-Sprache,
 *   Veröffentlichung, Kachel-Entscheidung, Anmeldung der Kachel; Einstiege öffnen die richtigen Seiten.
 * Ein-/Ausgabe: Robolectric-Kontext und synthetischer Zustand -> Verknüpfungen, Intents, Seiten.
 * Nebenwirkungen: Keine echten Prüfungen, Kalender oder Netzwerke.
 * Nicht zuständig: Darstellung durch echte Startbildschirme (Gerätetest).
 */
package com.tequila.tumcalendarcontrol.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.tequila.tumcalendarcontrol.i18n.AppLanguage
import com.tequila.tumcalendarcontrol.i18n.AppLanguagePreferences
import com.tequila.tumcalendarcontrol.sync.ManualEventKind
import com.tequila.tumcalendarcontrol.ui.AppEntry
import com.tequila.tumcalendarcontrol.ui.AppEntryIntents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppShortcutsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun chinese() = AppLanguagePreferences(context).save(AppLanguage.CHINESE)

    @Test fun `vier Verknüpfungen mit Zielen in App-Sprache`() {
        val shortcuts = AppShortcuts.build(context)
        assertEquals(listOf("read", "exam", "write", "widgets"), shortcuts.map { it.id })
        assertEquals(listOf("读取更新", "添加考试", "写入内容", "小组件"), shortcuts.map { it.shortLabel.toString() })
        assertTrue(shortcuts.all { it.intent.action == Intent.ACTION_VIEW })
        assertEquals(
            listOf(AppEntry.SourceRange, AppEntry.ManualEvents(ManualEventKind.EXAM), AppEntry.WritePreview, AppEntry.WidgetSettings()),
            shortcuts.map { AppEntryIntents.parse(it.intent) },
        )
    }

    @Test fun `Veröffentlichung setzt die dynamischen Verknüpfungen`() {
        AppShortcuts.publish(context)
        assertEquals(4, ShortcutManagerCompat.getDynamicShortcuts(context).size)
    }

    @Test fun `Kachel prüft nur bei aktivierter Prüfung und eingerichteter Quelle direkt`() {
        assertEquals(SourceCheckTileService.TileAction.CHECK_NOW, SourceCheckTileService.action(checkEnabled = true, sourceConfigured = true))
        assertEquals(SourceCheckTileService.TileAction.OPEN_SETTINGS, SourceCheckTileService.action(checkEnabled = true, sourceConfigured = false))
        assertEquals(SourceCheckTileService.TileAction.OPEN_SETTINGS, SourceCheckTileService.action(checkEnabled = false, sourceConfigured = true))
    }

    @Test fun `Kachel ist als Schnelleinstellung angemeldet`() {
        val services = context.packageManager.queryIntentServices(Intent("android.service.quicksettings.action.QS_TILE").setPackage(context.packageName), 0)
        assertEquals(listOf(SourceCheckTileService::class.java.name), services.map { it.serviceInfo.name })
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", services.single().serviceInfo.permission)
    }

    @Test fun `neue Einstiege im Hin- und Rückweg`() {
        listOf(AppEntry.WritePreview, AppEntry.BackgroundCheck).forEach { assertEquals(it, AppEntryIntents.parse(AppEntryIntents.intent(context, it))) }
    }
}
