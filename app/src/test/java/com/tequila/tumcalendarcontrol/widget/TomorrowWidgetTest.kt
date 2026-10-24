/*
 * Aufgabe: Widget „Morgen“ prüfen: Auswahl der Termine von morgen, nächster Kurs bei freiem Tag, schmale und breite
 *   Darstellung, Klickziele; Bild der breiten Darstellung als Referenz.
 * Ein-/Ausgabe: synthetische Termine („jetzt“ Mi 21.10.2026 10:30 Berlin) -> Modell, gezeichnete Ansicht, PNG.
 * Nebenwirkungen: Nur Robolectric-Aktivität, private Test-Präferenzen und lokales Testbild.
 * Nicht zuständig: „Heute“ (ScheduleWidgetRendererTest) oder Einstellungen.
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
import org.junit.Assert.assertNull
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
class TomorrowWidgetTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-10-21T08:30:00Z")
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDate.of(2026, 10, day).atTime(hour, minute).atZone(zone).toInstant()
    private fun ev(key: String, day: Int, hour: Int, minute: Int = 0) =
        WidgetEvent(key, "uid-$key", "Synthetik $key", at(day, hour, minute), at(day, hour, minute).plusSeconds(5_400), AttendanceState.MUST_ATTEND, "Raum $key")
    private val events = listOf(ev("heute", 21, 13), ev("a", 22, 8, 15), ev("b", 22, 10), ev("c", 22, 14), ev("fr", 23, 14))

    @Before fun chinese() = AppLanguagePreferences(context).save(AppLanguage.CHINESE)

    @Test fun `Termine von morgen und nächster Kurs`() {
        val tomorrow = ScheduleWidgetModel.tomorrow(events, now, zone)
        assertEquals(LocalDate.of(2026, 10, 22), tomorrow.date)
        assertEquals(listOf("a", "b", "c"), tomorrow.events.map { it.key })
        assertNull(tomorrow.nextEvent)
        val free = ScheduleWidgetModel.tomorrow(listOf(ev("fr", 23, 14)), now, zone)
        assertEquals(emptyList<WidgetEvent>(), free.events)
        assertEquals("fr", free.nextEvent?.key)
    }

    private fun render(width: Int, height: Int, data: List<WidgetEvent> = events): Pair<ComponentActivity, View> {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val host = FrameLayout(activity)
        activity.setContentView(host)
        val view = ScheduleWidgetRenderer.render(activity, WidgetSize.TOMORROW, WidgetFrame(data, data.isNotEmpty(), WidgetSettings(), now, zone, width, height)).apply(activity, host)
        val d = activity.resources.displayMetrics.density
        host.addView(view, FrameLayout.LayoutParams((width * d).toInt(), (height * d).toInt()))
        host.measure(View.MeasureSpec.makeMeasureSpec((width * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((height * d).toInt(), View.MeasureSpec.EXACTLY))
        host.layout(0, 0, host.measuredWidth, host.measuredHeight)
        return activity to view
    }

    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()

    @Test fun `schmal erster Kurs, ganzes Widget öffnet ihn`() {
        val (activity, view) = render(155, 170)
        assertEquals("明天 · 3 节", view.text(R.id.widget_status))
        assertEquals("08:15", view.text(R.id.widget_time))
        assertEquals("Synthetik a", view.text(R.id.widget_title))
        view.findViewById<View>(R.id.widget_frame).performClick()
        assertEquals(AppEntry.Course("uid-a"), AppEntryIntents.parse(shadowOf(activity).nextStartedActivity))
    }

    @Test fun `freier Tag mit nächstem Kurs`() {
        val (_, narrow) = render(155, 170, listOf(ev("fr", 23, 14)))
        assertEquals("明天没有课\n下次上课：周五 14:00", narrow.text(R.id.widget_empty))
        val (_, wide) = render(330, 180, listOf(ev("fr", 23, 14)))
        assertEquals("明天没有课 · 下次上课：周五 14:00", wide.text(R.id.widget_footer))
        assertEquals(View.GONE, wide.findViewById<View>(R.id.widget_rows).visibility)
    }

    @Test fun `breit Liste wie Heute`() {
        val (activity, view) = render(330, 220)
        assertEquals("明天 · 10月22日（周四）", view.text(R.id.widget_header))
        assertEquals("3 节", view.text(R.id.widget_header_side))
        val rows = view.findViewById<ViewGroup>(R.id.widget_rows)
        assertEquals(3, rows.childCount)
        rows.getChildAt(1).performClick()
        assertEquals(AppEntry.Course("uid-b"), AppEntryIntents.parse(shadowOf(activity).nextStartedActivity))
        view.findViewById<View>(R.id.widget_frame).performClick()
        assertEquals(AppEntry.Overview, AppEntryIntents.parse(shadowOf(activity).nextStartedActivity))
        view.captureRoboImage("src/test/screenshots/widget_tomorrow_330x220.png")
    }
}
