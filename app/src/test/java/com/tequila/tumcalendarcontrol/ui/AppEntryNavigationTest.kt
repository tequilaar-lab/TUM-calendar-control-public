/*
 * Aufgabe: Anwenden von Einstiegszielen prüfen: Die App landet auf der Zielseite, auch wenn sie zuvor auf einer anderen
 *   Seite offen war (Gerätefund 1.4.1); unbekannter Kurs führt zur Startseite mit Hinweis; Startseite als Intent-Ziel;
 *   ein offenes Ziel übersteht die Neuerstellung der Activity.
 * Ein-/Ausgabe: CalendarControlState mit Beispielkursen und synthetischen Zielen -> erreichte Seite, Hinweis, Intents.
 * Nebenwirkungen: Keine außer Robolectric-Kontext.
 * Nicht zuständig: Tippziele der Widgets (WidgetClickTargetTest) oder Widget-Einstellungen (WidgetSettingsPagesTest).
 */
package com.tequila.tumcalendarcontrol.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.sync.ManualEventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppEntryNavigationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Zuletzt auf einer anderen Seite (z. B. Einstellungen): jedes Ziel ersetzt sie. */
    private fun openElsewhere() = CalendarControlState().also { it.open(AppScreen.SETTINGS); it.openOutputCleanup() }

    @Test fun `Ziel ersetzt die zuletzt benutzte Seite`() {
        assertEquals(AppScreen.OVERVIEW, openElsewhere().applyEntry(AppEntry.Overview))
        val state = openElsewhere()
        assertEquals(AppScreen.DETAIL, state.applyEntry(AppEntry.Course("course-a")))
        assertNotEquals(message(906), state.feedback)
        assertEquals(AppScreen.MANUAL_EVENTS, openElsewhere().applyEntry(AppEntry.ManualEvents(ManualEventKind.EXAM)))
        assertEquals(AppScreen.BACKGROUND_CHECK, openElsewhere().applyEntry(AppEntry.BackgroundCheck))
        assertEquals(AppScreen.WIDGETS, openElsewhere().applyEntry(AppEntry.WidgetSettings(7)))
    }

    @Test fun `unbekannter Kurs öffnet die Startseite mit Hinweis`() {
        val state = openElsewhere()
        assertEquals(AppScreen.OVERVIEW, state.applyEntry(AppEntry.Course("nicht-vorhanden")))
        assertEquals(message(906), state.feedback)
    }

    /** Neuerstellung vor dem Anwenden behält das Ziel; nach dem Anwenden wird es nicht wiederholt. */
    @Test fun `Ziel übersteht Neuerstellung der Activity`() {
        val intent = AppEntryIntents.intent(context, AppEntry.WidgetSettings(3))
        assertEquals(AppEntry.WidgetSettings(3), AppEntryIntents.initial(intent, null))
        val pending = android.os.Bundle().also { AppEntryIntents.save(it, AppEntry.WidgetSettings(3)) }
        assertEquals("noch offen", AppEntry.WidgetSettings(3), AppEntryIntents.initial(intent, pending))
        val handled = android.os.Bundle().also { AppEntryIntents.save(it, null) }
        assertEquals("schon angewandt", null, AppEntryIntents.initial(intent, handled))
    }

    @Test fun `Startseite als Intent-Ziel`() {
        assertEquals(AppEntry.Overview, AppEntryIntents.parse(AppEntryIntents.intent(context, AppEntry.Overview)))
    }
}
