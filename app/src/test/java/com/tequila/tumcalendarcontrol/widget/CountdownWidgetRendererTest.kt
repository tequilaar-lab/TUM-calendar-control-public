/*
 * Aufgabe: Countdown-Widget prüfen: klein (große Zahl), breit (zwei daneben), hoch (Liste mit Rest), flach, leer,
 *   Anheftung mit Rückfall, Arten an/aus, Grau ausgeblendet, Aktualisierungsränder; Bilder hell und dunkel.
 * Ein-/Ausgabe: Synthetische Prüfungen/Fristen und feste Zeit -> angewandte RemoteViews und PNG-Dateien.
 * Nebenwirkungen: Nur lokale Testbilder.
 * Nicht zuständig: Countdown-Berechnung (DeadlineCountdownTest).
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
import com.tequila.tumcalendarcontrol.sync.ManualEventKind
import com.tequila.tumcalendarcontrol.sync.ManualScheduleItem
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
class CountdownWidgetRendererTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = LocalDateTime.of(2026, 10, 21, 10, 30).atZone(zone).toInstant()
    private fun at(day: Int, month: Int, hour: Int) = LocalDateTime.of(2026, month, day, hour, 0).atZone(zone).toInstant()
    /** Prüfung: 2 Stunden ab start; Frist: wie in der App fällig um start, im Kalender die 30 Minuten davor. */
    private fun item(id: String, kind: ManualEventKind, start: Instant, state: AttendanceState = AttendanceState.MUST_ATTEND) =
        if (kind == ManualEventKind.DEADLINE) ManualScheduleItem(id, "Synthetik $id", start.minusSeconds(1_800), start, state, kind)
        else ManualScheduleItem(id, "Synthetik $id", start, start.plusSeconds(7_200), state, kind)
    private val items = listOf(
        item("analysis", ManualEventKind.EXAM, at(2, 11, 9)),
        item("blatt", ManualEventKind.DEADLINE, at(22, 10, 12)),
        item("mess", ManualEventKind.EXAM, at(10, 11, 14)),
        item("grau", ManualEventKind.EXAM, at(5, 11, 9), AttendanceState.STOP_ATTENDING),
        item("treffen", ManualEventKind.NORMAL, at(23, 10, 9)),
        item("physik", ManualEventKind.EXAM, at(20, 11, 9)),
    )

    @Before fun chinese() = AppLanguagePreferences(context).save(AppLanguage.CHINESE)

    private fun render(width: Int, height: Int, settings: WidgetSettings = WidgetSettings(), data: List<ManualScheduleItem> = items): View {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val host = FrameLayout(activity)
        activity.setContentView(host)
        val view = ScheduleWidgetRenderer.render(activity, WidgetSize.COUNTDOWN, WidgetFrame(emptyList(), false, settings, now, zone, width, height, data)).apply(activity, host)
        val density = activity.resources.displayMetrics.density
        host.addView(view, FrameLayout.LayoutParams((width * density).toInt(), (height * density).toInt()))
        host.measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY))
        host.layout(0, 0, host.measuredWidth, host.measuredHeight)
        return view
    }

    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()
    private fun View.visible(id: Int) = findViewById<View>(id).visibility == View.VISIBLE
    private fun View.children(id: Int) = findViewById<ViewGroup>(id).childCount

    /** Nächstes Ziel: Frist morgen 12:00 (26 Stunden); der Kurs-Inhalt spielt keine Rolle (hasData = false). */
    @Test fun `klein zeigt das nächste Ziel groß`() {
        val view = render(170, 170)
        assertEquals("截止", view.text(R.id.cd_kind))
        assertEquals("26", view.text(R.id.cd_number))
        assertEquals("小时", view.text(R.id.cd_unit))
        assertEquals("明天 12:00", view.text(R.id.cd_when))
        assertFalse(view.visible(R.id.cd_side))
        view.captureRoboImage("src/test/screenshots/widget_countdown_170x170.png")
    }

    @Test fun `breit zwei daneben, hoch Liste mit Rest, flach ohne Zeitpunkt`() {
        val wide = render(330, 170)
        assertEquals(2, wide.children(R.id.cd_side))
        wide.captureRoboImage("src/test/screenshots/widget_countdown_wide_330x170.png")
        val tall = render(330, 270)
        assertEquals("Grau ausgeblendet: 3 weitere", 3, tall.children(R.id.cd_list))
        tall.captureRoboImage("src/test/screenshots/widget_countdown_tall_330x270.png")
        val short = render(330, 230)
        assertEquals(1, short.children(R.id.cd_list))
        assertEquals("还有 2 项", short.text(R.id.widget_footer))
        val flat = render(330, 70)
        assertFalse(flat.visible(R.id.cd_when))
    }

    @Test fun `Anheftung mit Rückfall und Arten an aus`() {
        assertEquals("Synthetik mess", render(170, 170, WidgetSettings(countdownPinnedId = "mess")).text(R.id.cd_title))
        assertEquals("Synthetik blatt", render(170, 170, WidgetSettings(countdownPinnedId = "gelöscht")).text(R.id.cd_title))
        val examsOnly = render(170, 170, WidgetSettings(countdownDeadlines = false))
        assertEquals("考试", examsOnly.text(R.id.cd_kind))
        assertEquals("12", examsOnly.text(R.id.cd_number))
        assertEquals("天", examsOnly.text(R.id.cd_unit))
    }

    @Test fun `erneutes Anwenden stapelt die Liste nicht`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val frame = WidgetFrame(emptyList(), false, WidgetSettings(), now, zone, 330, 270, items)
        val view = ScheduleWidgetRenderer.render(activity, WidgetSize.COUNTDOWN, frame).apply(activity, FrameLayout(activity))
        repeat(3) { ScheduleWidgetRenderer.render(activity, WidgetSize.COUNTDOWN, frame).reapply(activity, view) }
        assertEquals(3, view.children(R.id.cd_list))
        // Danach schmal: Liste und Seitenspalte müssen verschwinden statt stehen zu bleiben.
        ScheduleWidgetRenderer.render(activity, WidgetSize.COUNTDOWN, frame.copy(widthDp = 170, heightDp = 170)).reapply(activity, view)
        assertFalse(view.visible(R.id.cd_list))
        assertFalse(view.visible(R.id.cd_side))
    }

    @Test fun `leer mit Hinweis`() {
        val view = render(170, 170, data = listOf(items.single { it.kind == ManualEventKind.NORMAL }))
        assertTrue(view.visible(R.id.widget_empty))
        assertTrue(view.text(R.id.widget_empty).startsWith("还没有考试或截止"))
    }

    @Test fun `Aktualisierung an Zielzeiten, in der letzten Stunde alle 5 Minuten`() {
        assertEquals(at(22, 10, 12), ScheduleWidgets.countdownRefresh(items, now))
        val soon = listOf(item("bald", ManualEventKind.DEADLINE, now.plusSeconds(1_200)))
        assertEquals(now.plusSeconds(300), ScheduleWidgets.countdownRefresh(soon, now))
        assertEquals(null, ScheduleWidgets.countdownRefresh(emptyList(), now))
    }

    @Config(qualifiers = "w412dp-h892dp-night-420dpi")
    @Test fun `dunkel`() {
        render(330, 170).captureRoboImage("src/test/screenshots/widget_countdown_dark_330x170.png")
    }
}
