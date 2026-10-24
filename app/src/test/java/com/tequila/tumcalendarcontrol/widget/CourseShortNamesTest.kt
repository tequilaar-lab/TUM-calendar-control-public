/*
 * Aufgabe: Kurznamen fürs Widget „Woche“ prüfen: Speichern je Quell-UID, Bereinigen und Kürzen, Löschen bei leerer
 *   Eingabe; Wochenblöcke zeigen den Kurznamen, ohne Kurznamen den vollen Titel; andere Widgets bleiben beim Titel.
 * Ein-/Ausgabe: synthetische UIDs, Titel und Termine -> gespeicherte Werte und gezeichnete Blocktexte.
 * Nebenwirkungen: Nur private Test-Präferenzen.
 * Nicht zuständig: Eingabeoberflächen (CourseShortNamePagesTest).
 */
package com.tequila.tumcalendarcontrol.widget

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.tequila.tumcalendarcontrol.R
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.i18n.AppLanguage
import com.tequila.tumcalendarcontrol.i18n.AppLanguagePreferences
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CourseShortNamesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-10-21T08:30:00Z")
    private fun event(n: Int, title: String) =
        WidgetEvent("uid-$n|1", "uid-$n", title, now.plusSeconds(3_600L * n), now.plusSeconds(3_600L * n + 5_400), AttendanceState.MUST_ATTEND, "Raum $n")

    @Before fun chinese() = AppLanguagePreferences(context).save(AppLanguage.CHINESE)

    @Test fun `Speichern je UID, bereinigt und begrenzt`() {
        val store = CourseShortNames(context)
        store.set("uid-1", "  Ana 1 \n")
        assertEquals("Ana 1", store.get("uid-1"))
        store.set("uid-2", "Synthetik Regelungstechnik Übung")
        assertEquals(CourseShortNames.MAX_LENGTH, store.get("uid-2").length)
        assertEquals(mapOf("uid-1" to "Ana 1", "uid-2" to store.get("uid-2")), store.load())
        store.set("uid-1", "   ")
        assertEquals("leer entfernt", "", store.get("uid-1"))
        assertEquals(setOf("uid-2"), store.load().keys)
    }

    /** Woche: Kurzname statt langem Titel; ohne Kurzname bleibt der volle Titel. */
    @Test fun `Wochenblöcke zeigen den Kurznamen`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val events = listOf(event(1, "Synthetik Signalverarbeitung und Systemtheorie"), event(2, "Synthetik Praktikum"))
        val frame = WidgetFrame(events, true, WidgetSettings(), now, zone, 320, 340, shortNames = mapOf("uid-1" to "SVS"))
        val view = ScheduleWidgetRenderer.render(activity, WidgetSize.LARGE, frame).apply(activity, FrameLayout(activity))
        val texts = chipTexts(view)
        assertTrue(texts.toString(), texts.any { it.endsWith(" SVS") })
        assertTrue(texts.toString(), texts.any { it.endsWith(" Synthetik Praktikum") })
        assertTrue(texts.none { it.contains("Signalverarbeitung") })
    }

    @Test fun `Kurzname nur im Wochen-Widget`() {
        val frame = WidgetFrame(listOf(event(1, "Synthetik Lang")), true, WidgetSettings(), now, zone, 320, 340, shortNames = mapOf("uid-1" to "SL"))
        assertEquals("SL", ScheduleWidgetRenderer.weekTitle(frame.events.single(), frame))
        assertEquals("leerer Kurzname", "Synthetik Lang", ScheduleWidgetRenderer.weekTitle(frame.events.single(), frame.copy(shortNames = mapOf("uid-1" to " "))))
    }

    private fun chipTexts(view: View): List<String> {
        val out = mutableListOf<String>()
        fun walk(v: View) {
            if (v.id == R.id.chip_text && v is TextView) out += v.text.toString()
            if (v is android.view.ViewGroup) (0 until v.childCount).forEach { walk(v.getChildAt(it)) }
        }
        walk(view)
        return out
    }
}
