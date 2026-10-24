/*
 * Aufgabe: Widget-Darstellung prüfen: drei Größen bei niedriger und hoher Höhe, Leerzustand, Grau ausgeblendet,
 *   Hintergrund nach Deckkraft/Ecke; Bilder je Größe zur Sichtprüfung (hell und dunkel).
 * Ein-/Ausgabe: Synthetische Termine -> angewandte RemoteViews (echte Views) und PNG-Dateien.
 * Nebenwirkungen: Nur lokale Testbilder; keine Kalender, Konten oder Netzwerke.
 * Nicht zuständig: Inhaltslogik (ScheduleWidgetModelTest) oder Darstellung durch echte Startbildschirme.
 */
package com.tequila.tumcalendarcontrol.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.tequila.tumcalendarcontrol.R
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric
import androidx.activity.ComponentActivity
import com.tequila.tumcalendarcontrol.i18n.AppLanguage
import com.tequila.tumcalendarcontrol.i18n.AppLanguagePreferences
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
class ScheduleWidgetRendererTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-10-21T08:30:00Z")
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDate.of(2026, 10, day).atTime(hour, minute).atZone(zone).toInstant()
    private fun ev(key: String, day: Int, hour: Int, state: AttendanceState = AttendanceState.MUST_ATTEND, minute: Int = 0) =
        WidgetEvent(key, "uid-$key", "Synthetik $key", at(day, hour, minute), at(day, hour, minute).plusSeconds(5_400), state, "Raum (0000.01.10$key)".take(40))
    private val events = listOf(
        ev("1", 19, 10), ev("2", 20, 12, AttendanceState.CHOOSE_TO_ATTEND), ev("3", 21, 8), ev("4", 21, 10),
        ev("5", 21, 13, AttendanceState.CHOOSE_TO_ATTEND), ev("6", 21, 16, AttendanceState.STOP_ATTENDING), ev("7", 22, 9), ev("8", 23, 14), ev("9", 23, 16),
    )

    /** Widgets folgen der App-Sprache; Tests prüfen chinesische Texte. */
    @Before fun chinese() = AppLanguagePreferences(context).save(AppLanguage.CHINESE)

    /** Bilder brauchen eine Activity als Fenster; die Widget-Ansicht liegt mit fester Größe darin. */
    private fun render(size: WidgetSize, width: Int, height: Int, settings: WidgetSettings = WidgetSettings(), data: List<WidgetEvent> = events): View {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val host = FrameLayout(activity)
        activity.setContentView(host)
        val view = ScheduleWidgetRenderer.render(activity, size, WidgetFrame(data, data.isNotEmpty(), settings, now, zone, width, height)).apply(activity, host)
        val density = activity.resources.displayMetrics.density
        host.addView(view, FrameLayout.LayoutParams((width * density).toInt(), (height * density).toInt()))
        host.measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY))
        host.layout(0, 0, host.measuredWidth, host.measuredHeight)
        // Bild auf das Widget selbst zuschneiden (nicht das ganze Fenster).
        return view
    }

    private fun View.text(id: Int): String = (findViewById<TextView>(id)).text.toString()
    private fun View.visible(id: Int): Boolean = findViewById<View>(id).visibility == View.VISIBLE
    private fun View.rowCount(): Int = findViewById<ViewGroup>(R.id.widget_rows).childCount

    @Test fun `klein laufender Termin mit Raum, flach ohne Zeit`() {
        val tall = render(WidgetSize.SMALL, 170, 170)
        assertEquals("进行中 · 还剩 60 分钟", tall.text(R.id.widget_status))
        assertEquals("Synthetik 4", tall.text(R.id.widget_title))
        assertTrue(tall.visible(R.id.widget_location))
        tall.captureRoboImage("src/test/screenshots/widget_small_170x170.png")
        val flat = render(WidgetSize.SMALL, 170, 60)
        assertFalse(flat.visible(R.id.widget_time))
        assertFalse(flat.visible(R.id.widget_location))
        flat.captureRoboImage("src/test/screenshots/widget_small_170x60.png")
    }

    @Test fun `mittel Zeilen nach Höhe, Grau ausgeblendet, einzeilig nur Kopf`() {
        val tall = render(WidgetSize.MEDIUM, 330, 240)
        // Heute sichtbar: 8, 10, 13 (16 Uhr ist grau und standardmäßig ausgeblendet).
        assertEquals(3, tall.rowCount())
        assertEquals("3 节", tall.text(R.id.widget_header_side))
        tall.captureRoboImage("src/test/screenshots/widget_medium_330x240.png")
        val short = render(WidgetSize.MEDIUM, 330, 110)
        assertEquals("Vergangene Zeile fällt zuerst weg", 1, short.rowCount())
        assertTrue(short.text(R.id.widget_footer).contains("还有 1 节"))
        val single = render(WidgetSize.MEDIUM, 330, 50)
        assertFalse(single.visible(R.id.widget_rows))
        assertTrue(single.text(R.id.widget_header_side).contains("10:00"))
        val withGray = render(WidgetSize.MEDIUM, 330, 240, WidgetSettings(hideStopAttending = false))
        assertEquals(4, withGray.rowCount())
    }

    @Test fun `groß sieben Tage, Zähler, flach nur Kopf und Zähler`() {
        val tall = render(WidgetSize.LARGE, 330, 380)
        assertEquals(7, tall.rowCount())
        assertEquals("必去 6 · 选择 2", tall.text(R.id.widget_footer))
        tall.captureRoboImage("src/test/screenshots/widget_large_330x380.png")
        val flat = render(WidgetSize.LARGE, 330, 90)
        assertFalse(flat.visible(R.id.widget_rows))
        assertTrue(flat.visible(R.id.widget_footer))
    }

    @Test fun `ohne geschriebene Termine Hinweis`() {
        val view = render(WidgetSize.MEDIUM, 330, 170, data = emptyList())
        assertTrue(view.visible(R.id.widget_empty))
        assertTrue(view.text(R.id.widget_empty).startsWith("还没有写入的课程"))
    }

    @Test fun `Hintergrund nach Deckkraft und Ecke`() {
        assertEquals(R.drawable.widget_bg_solid_r16, ScheduleWidgetRenderer.background(WidgetSettings(), null))
        assertEquals(R.drawable.widget_bg_glass_r0, ScheduleWidgetRenderer.background(WidgetSettings(translucent = true, corner = WidgetCorner.SQUARE), null))
        assertEquals(R.drawable.widget_bg_solid_r32, ScheduleWidgetRenderer.background(WidgetSettings(corner = WidgetCorner.ROUND), null))
        render(WidgetSize.MEDIUM, 330, 170, WidgetSettings(translucent = true, corner = WidgetCorner.ROUND)).captureRoboImage("src/test/screenshots/widget_medium_glass_round_330x170.png")
    }

    /**
     * Startbildschirme wenden Aktualisierungen auf die vorhandene Ansicht an (reapply): Zeilen dürfen sich nicht
     * stapeln, und ein früherer Leerzustand darf nicht stehen bleiben (Gerätefund 1.4.0, HONOR).
     */
    @Test fun `erneutes Anwenden stapelt nichts und setzt Sichtbarkeit zurück`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        fun frame(data: List<WidgetEvent>, height: Int) = WidgetFrame(data, data.isNotEmpty(), WidgetSettings(), now, zone, 330, height)
        val week = ScheduleWidgetRenderer.render(activity, WidgetSize.LARGE, frame(events, 380)).apply(activity, FrameLayout(activity))
        repeat(3) { ScheduleWidgetRenderer.render(activity, WidgetSize.LARGE, frame(events, 380)).reapply(activity, week) }
        assertEquals(7, week.rowCount())
        val list = ScheduleWidgetRenderer.render(activity, WidgetSize.MEDIUM, frame(emptyList(), 240)).apply(activity, FrameLayout(activity))
        assertTrue(list.visible(R.id.widget_empty))
        repeat(3) { ScheduleWidgetRenderer.render(activity, WidgetSize.MEDIUM, frame(events, 240)).reapply(activity, list) }
        assertEquals(3, list.rowCount())
        assertFalse("Leerzustand verschwindet", list.visible(R.id.widget_empty))
        assertTrue(list.visible(R.id.widget_body))
    }

    /** Schmales 2×2 (HONOR: 155×211 dp): Status nur mit Tag (Uhrzeit steht groß darunter), nur Beginn. */
    @Test fun `klein schmal mit Wochentag und nur Beginn`() {
        val later = listOf(ev("x", 24, 13))
        val view = render(WidgetSize.SMALL, 155, 211, data = later)
        assertEquals("周六", view.text(R.id.widget_status))
        assertEquals("13:00", view.text(R.id.widget_time))
        view.captureRoboImage("src/test/screenshots/widget_small_narrow_155x211.png")
    }

    @Config(qualifiers = "w412dp-h892dp-night-420dpi")
    @Test fun `dunkel folgt dem System`() {
        render(WidgetSize.LARGE, 330, 380).captureRoboImage("src/test/screenshots/widget_large_dark_330x380.png")
        render(WidgetSize.SMALL, 170, 170).captureRoboImage("src/test/screenshots/widget_small_dark_170x170.png")
    }
}
