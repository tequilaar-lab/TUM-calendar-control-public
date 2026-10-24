/*
 * Aufgabe: Widget „Stundenplan“ prüfen: Spalten Mo–Fr (Wochenende nur mit Terminen), Achse auf volle Stunden,
 *   Spuren für Überlappungen mit Grenze, Blockhöhen nach Dauer, Kurznamen, Klickziele, leere Woche; Bild als Referenz.
 * Ein-/Ausgabe: synthetische Termine (Woche 19.–25.10.2026, „jetzt“ Mi 10:30) -> Modell, gezeichnete Ansicht, PNG.
 * Nebenwirkungen: Nur Robolectric-Aktivität, private Test-Präferenzen und lokales Testbild.
 * Nicht zuständig: andere Widgets (ScheduleWidgetRendererTest) oder Einstellungen.
 */
package com.tequila.tumcalendarcontrol.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.tequila.tumcalendarcontrol.R
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.i18n.AppLanguage
import com.tequila.tumcalendarcontrol.i18n.AppLanguagePreferences
import com.tequila.tumcalendarcontrol.ui.AppEntry
import com.tequila.tumcalendarcontrol.ui.AppEntryIntents
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
class GridWidgetTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-10-21T08:30:00Z")
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDate.of(2026, 10, day).atTime(hour, minute).atZone(zone).toInstant()
    private fun ev(key: String, day: Int, hour: Int, minute: Int = 0, minutes: Long = 90, state: AttendanceState = AttendanceState.MUST_ATTEND) =
        WidgetEvent(key, "uid-$key", "Synthetik Kurs $key", at(day, hour, minute), at(day, hour, minute).plusSeconds(minutes * 60), state, "Raum $key")
    private val events = listOf(
        ev("1", 19, 8, 15), ev("2", 20, 10), ev("3", 21, 10), ev("4", 21, 10, 30, state = AttendanceState.CHOOSE_TO_ATTEND),
        ev("5", 22, 13), ev("6", 23, 16, minutes = 120),
    )

    @Before fun chinese() = AppLanguagePreferences(context).save(AppLanguage.CHINESE)

    private fun model(data: List<WidgetEvent>) = GridWidgetModel.layout(ScheduleWidgetModel.week(data, now, zone, calendarWeek = true), zone)

    @Test fun `Spalten, Achse und Spuren`() {
        val grid = model(events)
        assertEquals((19..23).toList(), grid.days.map { it.date.dayOfMonth })
        assertEquals("8:15 abgerundet", 8 * 60, grid.startMinute)
        assertEquals("18:00 (16 Uhr + 2 h)", 18 * 60, grid.endMinute)
        val wednesday = grid.days.first { it.date.dayOfMonth == 21 }
        assertTrue(wednesday.today)
        assertEquals("Überlappung nebeneinander", 2, wednesday.lanes.size)
        assertTrue(grid.days.first().past)
        // Wochenende erscheint nur mit Terminen.
        assertEquals(6, model(events + ev("7", 24, 9)).days.size)
    }

    @Test fun `höchstens drei Spuren, Rest gezählt`() {
        val crowded = (1..5).map { ev("c$it", 21, 10, it) }
        val day = model(crowded).days.first { it.date.dayOfMonth == 21 }
        assertEquals(GridWidgetModel.MAX_LANES, day.lanes.size)
        assertEquals(2, day.hidden)
        assertEquals(2, model(crowded).hidden)
    }

    @Test fun `ohne Termine Standardachse`() {
        val grid = model(emptyList())
        assertEquals(8 * 60 to 18 * 60, grid.startMinute to grid.endMinute)
        assertTrue(!grid.hasEvents)
    }

    private fun render(width: Int, height: Int, data: List<WidgetEvent> = events, shortNames: Map<String, String> = emptyMap()): Pair<ComponentActivity, View> {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val host = FrameLayout(activity)
        activity.setContentView(host)
        val frame = WidgetFrame(data, data.isNotEmpty(), WidgetSettings(), now, zone, width, height, shortNames = shortNames)
        val view = ScheduleWidgetRenderer.render(activity, WidgetSize.GRID, frame).apply(activity, host)
        val d = activity.resources.displayMetrics.density
        host.addView(view, FrameLayout.LayoutParams((width * d).toInt(), (height * d).toInt()))
        host.measure(View.MeasureSpec.makeMeasureSpec((width * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((height * d).toInt(), View.MeasureSpec.EXACTLY))
        host.layout(0, 0, host.measuredWidth, host.measuredHeight)
        return activity to view
    }

    private fun View.all(id: Int): List<View> {
        val out = mutableListOf<View>()
        fun walk(v: View) { if (v.id == id) out += v; if (v is ViewGroup) (0 until v.childCount).forEach { walk(v.getChildAt(it)) } }
        walk(this)
        return out
    }

    @Test fun `Blöcke nach Dauer, Kurzname und Klickziele`() {
        val (activity, view) = render(330, 290, shortNames = mapOf("uid-6" to "PRK"))
        assertEquals(5, view.findViewById<ViewGroup>(R.id.grid_days).childCount)
        val blocks = view.all(R.id.grid_block_root)
        assertEquals(events.size, blocks.size)
        val titles = view.all(R.id.grid_block_title).map { (it as TextView).text.toString() }
        assertTrue(titles.toString(), "PRK" in titles)
        // 120 Minuten sind höher als 90 Minuten.
        val long = blocks[titles.indexOf("PRK")]
        val short = blocks[titles.indexOf("Synthetik Kurs 1")]
        assertTrue("${long.height} > ${short.height}", long.height > short.height)
        long.performClick()
        assertEquals(AppEntry.Course("uid-6"), AppEntryIntents.parse(shadowOf(activity).nextStartedActivity))
        view.findViewById<View>(R.id.widget_frame).performClick()
        assertEquals(AppEntry.Overview, AppEntryIntents.parse(shadowOf(activity).nextStartedActivity))
        view.captureRoboImage("src/test/screenshots/widget_grid_330x290.png")
    }

    @Test fun `leere Woche mit Hinweis`() {
        val (_, view) = render(330, 290, data = listOf(ev("x", 12, 10)))
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_empty).visibility)
        assertEquals("本周没有课", (view.findViewById<TextView>(R.id.widget_empty)).text.toString())
    }
}
