/*
 * Aufgabe: Tippziele der Widgets prüfen (Gerätefund 1.4.1: Antippen öffnete die zuletzt benutzte Seite): jede Fläche,
 *   auch der freie Rand, öffnet ein ausdrückliches Ziel; Kurs-Widgets außerhalb von Terminen die Startseite.
 * Ein-/Ausgabe: synthetische Termine -> gezeichnete RemoteViews, Klick -> gestarteter Intent -> AppEntry.
 * Nebenwirkungen: Nur Robolectric-Aktivitätsstarts und private Test-Präferenzen.
 * Nicht zuständig: Anwenden des Ziels in der App (AppEntryNavigationTest) oder Widget-Inhalt.
 */
package com.tequila.tumcalendarcontrol.widget

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.tequila.tumcalendarcontrol.R
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.i18n.AppLanguage
import com.tequila.tumcalendarcontrol.i18n.AppLanguagePreferences
import com.tequila.tumcalendarcontrol.sync.ManualEventKind
import com.tequila.tumcalendarcontrol.ui.AppEntry
import com.tequila.tumcalendarcontrol.ui.AppEntryIntents
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetClickTargetTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-10-21T08:30:00Z")
    private val event = WidgetEvent("a|1", "uid-a", "Synthetik A", now.plusSeconds(3_600), now.plusSeconds(9_000), AttendanceState.MUST_ATTEND, "Raum 0000.01.101")

    @Before fun chinese() = AppLanguagePreferences(context).save(AppLanguage.CHINESE)

    private fun render(size: WidgetSize, events: List<WidgetEvent>, width: Int = 320, height: Int = 211): View {
        val frame = WidgetFrame(events, events.isNotEmpty(), WidgetSettings(blockHeightPercent = 80), now, zone, width, height)
        return ScheduleWidgetRenderer.render(activity, size, frame).apply(activity, FrameLayout(activity))
    }

    /** Antippen einer Fläche und das gestartete Ziel lesen (null = nichts gestartet). */
    private fun tap(view: View, id: Int): AppEntry? {
        view.findViewById<View>(id).performClick()
        return AppEntryIntents.parse(shadowOf(activity).nextStartedActivity)
    }

    @Test fun `Kurs-Widgets ausserhalb von Terminen öffnen die Startseite`() {
        for (size in listOf(WidgetSize.MEDIUM, WidgetSize.LARGE)) {
            val view = render(size, listOf(event), height = 340)
            assertEquals("$size Block", AppEntry.Overview, tap(view, R.id.widget_root))
            assertEquals("$size Rand", AppEntry.Overview, tap(view, R.id.widget_frame))
        }
        val empty = render(WidgetSize.MEDIUM, emptyList())
        assertEquals("ohne Daten", AppEntry.Overview, tap(empty, R.id.widget_frame))
    }

    @Test fun `Wohin jetzt öffnet überall den Kurs`() {
        val view = render(WidgetSize.SMALL, listOf(event), 155, 211)
        val course = AppEntry.Course("uid-a")
        assertEquals(course, tap(view, R.id.widget_body))
        assertEquals(course, tap(view, R.id.widget_root))
        assertEquals(course, tap(view, R.id.widget_frame))
        assertEquals("ohne Termin: Startseite", AppEntry.Overview, tap(render(WidgetSize.SMALL, emptyList(), 155, 211), R.id.widget_frame))
    }

    @Test fun `Countdown-Rand wie Block`() {
        val view = render(WidgetSize.COUNTDOWN, emptyList(), 155, 211)
        assertEquals(AppEntry.ManualEvents(ManualEventKind.EXAM), tap(view, R.id.widget_root))
        assertEquals(AppEntry.ManualEvents(ManualEventKind.EXAM), tap(view, R.id.widget_frame))
    }

    /** Prüf-Widget ohne eingerichtete Prüfung: Block und Rand öffnen die Seite der Hintergrundprüfung (nie Prüfung starten). */
    @Test fun `Prüf-Widget-Rand wie Block`() {
        val view = render(WidgetSize.CHECK, emptyList(), 73, 101)
        assertEquals(AppEntry.BackgroundCheck, tap(view, R.id.widget_root))
        assertEquals(AppEntry.BackgroundCheck, tap(view, R.id.widget_frame))
    }
}
