/*
 * Aufgabe: Widget „Auf Änderungen prüfen“ prüfen: Entscheidung beim Antippen, Statustexte (nie, Ergebnis mit Zeit,
 *   „Prüft…“ nur kurz und nur ohne neueren Stand, nicht eingerichtet), kompakt ohne Titel, Anmeldung im Manifest.
 * Ein-/Ausgabe: Synthetische Zustände -> Texte, RemoteViews und Manifestabfrage.
 * Nebenwirkungen: Keine; es wird keine Prüfung gestartet.
 * Nicht zuständig: Prüflogik des Workers.
 */
package com.tequila.tumcalendarcontrol.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.tequila.tumcalendarcontrol.R
import com.tequila.tumcalendarcontrol.i18n.AppLanguage
import com.tequila.tumcalendarcontrol.i18n.AppLanguagePreferences
import com.tequila.tumcalendarcontrol.sync.SourceCheckResult
import com.tequila.tumcalendarcontrol.sync.SourceCheckStatus
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
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
class CheckWidgetTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-10-24T09:15:00Z")
    private fun state(enabled: Boolean = true, configured: Boolean = true, result: SourceCheckResult = SourceCheckResult.UNCHANGED, checkedAt: Instant? = now.minusSeconds(3_600), started: Instant? = null) =
        CheckWidgetRenderer.State(enabled, configured, SourceCheckStatus(checkedAt, result), started, now, zone)

    @Before fun chinese() = AppLanguagePreferences(context).save(AppLanguage.CHINESE)

    @Test fun `Antippen prüft nur bei aktivierter Prüfung und eingerichteter Quelle`() {
        assertEquals(CheckWidgetRenderer.Action.CHECK_NOW, CheckWidgetRenderer.action(checkEnabled = true, sourceConfigured = true))
        assertEquals(CheckWidgetRenderer.Action.OPEN_SETTINGS, CheckWidgetRenderer.action(checkEnabled = true, sourceConfigured = false))
        assertEquals(CheckWidgetRenderer.Action.OPEN_SETTINGS, CheckWidgetRenderer.action(checkEnabled = false, sourceConfigured = true))
    }

    @Test fun `Statustexte`() {
        fun text(s: CheckWidgetRenderer.State) = com.tequila.tumcalendarcontrol.i18n.localizedMessage(
            com.tequila.tumcalendarcontrol.i18n.localizedAppContext(context, AppLanguage.CHINESE), CheckWidgetRenderer.statusText(s))
        assertEquals("上次 10-24 10:15 · 无变化", text(state()))
        assertEquals("还没有检查过", text(state(result = SourceCheckResult.NEVER, checkedAt = null)))
        assertEquals("检查中…", text(state(started = now.minusSeconds(60))))
        assertEquals("Neuerer Stand gewinnt", "上次 10-24 11:14 · 有变化", text(state(result = SourceCheckResult.CHANGED, checkedAt = now.minusSeconds(60), started = now.minusSeconds(120))))
        assertEquals("Nach 10 Minuten nicht mehr „Prüft…“", "上次 10-24 10:15 · 无变化", text(state(started = now.minusSeconds(900))))
        assertEquals("后台检查未开启，点此设置", text(state(enabled = false)))
    }

    /** 1×1 (HONOR etwa 73×101 dp): zweizeilig Uhrzeit bzw. Datum und Ergebnis statt abgeschnittener langer Zeile. */
    @Test fun `kompakter Status`() {
        fun text(s: CheckWidgetRenderer.State) = com.tequila.tumcalendarcontrol.i18n.localizedMessage(
            com.tequila.tumcalendarcontrol.i18n.localizedAppContext(context, AppLanguage.CHINESE), CheckWidgetRenderer.compactStatusText(s))
        assertEquals("10:15\n无变化", text(state()))
        assertEquals("10-23\n有变化", text(state(result = SourceCheckResult.CHANGED, checkedAt = now.minusSeconds(86_400))))
        assertEquals("检查中…", text(state(started = now.minusSeconds(60))))
        assertEquals("未开启", text(state(enabled = false)))
    }

    @Test fun `kompakt ohne Titel, größer mit Titel`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        fun view(w: Int, h: Int) = CheckWidgetRenderer.render(activity, WidgetFrame(emptyList(), false, WidgetSettings(), now, zone, w, h), state()).apply(activity, FrameLayout(activity))
        assertEquals(View.GONE, view(80, 100).findViewById<View>(R.id.check_title).visibility)
        val big = view(160, 160)
        assertEquals(View.VISIBLE, big.findViewById<View>(R.id.check_title).visibility)
        assertEquals("检查课程更新", big.findViewById<TextView>(R.id.check_title).text.toString())
    }

    @Test fun `alle Widgets angemeldet`() {
        // Robolectric füllt installedProviders nicht; die Empfänger der Widget-Aktualisierung zeigen die Anmeldung.
        val receivers = context.packageManager.queryBroadcastReceivers(android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(context.packageName), 0)
            .map { it.activityInfo.name.substringAfterLast('.') }
        assertEquals(listOf("ScheduleWidgetCheck", "ScheduleWidgetCountdown", "ScheduleWidgetGrid", "ScheduleWidgetLarge", "ScheduleWidgetMedium", "ScheduleWidgetSmall", "ScheduleWidgetTomorrow"), receivers.sorted())
    }
}
