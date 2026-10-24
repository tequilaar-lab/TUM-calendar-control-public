/*
 * Aufgabe: Öffentliche Deeplinks tumcal:// prüfen: erlaubte Ziele, Art bei Campusterminen, Kurs per Quell-UID,
 *   unbekannte/fremde Adressen ohne Ziel, Anmeldung im Manifest und Anwenden auf die Navigation.
 * Ein-/Ausgabe: synthetische URIs und Intents -> AppEntry bzw. erreichte Seite.
 * Nebenwirkungen: Keine außer Robolectric-Kontext.
 * Nicht zuständig: interne Einstiegsziele (AppEntryNavigationTest) oder Widgets.
 */
package com.tequila.tumcalendarcontrol.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.tequila.tumcalendarcontrol.sync.ManualEventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeepLinkTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun parse(link: String) = AppEntryIntents.parse(Intent(Intent.ACTION_VIEW, Uri.parse(link)))

    @Test fun `erlaubte Ziele`() {
        assertEquals(AppEntry.Overview, parse("tumcal://overview"))
        assertEquals(AppEntry.ManualEvents(null), parse("tumcal://events"))
        assertEquals(AppEntry.ManualEvents(ManualEventKind.EXAM), parse("tumcal://events?kind=exam"))
        assertEquals(AppEntry.ManualEvents(ManualEventKind.DEADLINE), parse("TUMCAL://Events?kind=Deadline"))
        assertEquals(AppEntry.BackgroundCheck, parse("tumcal://check"))
        assertEquals(AppEntry.WidgetSettings(), parse("tumcal://widgets"))
        assertEquals(AppEntry.ClassLive, parse("tumcal://live"))
        assertEquals(AppEntry.Course("uid-synthetik"), parse("tumcal://course/uid-synthetik"))
    }

    @Test fun `nichts Unbekanntes, kein Schreiben`() {
        assertNull(parse("tumcal://write"))
        assertNull(parse("tumcal://course/"))
        assertNull(parse("https://example.org/overview"))
        assertNull("nur ACTION_VIEW", AppEntryIntents.parse(Intent(Intent.ACTION_SEND, Uri.parse("tumcal://overview"))))
    }

    @Test fun `im Manifest angemeldet und Navigation`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tumcal://overview")).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(context.packageName)
        assertTrue(context.packageManager.queryIntentActivities(intent, 0).any { it.activityInfo.name.endsWith(".MainActivity") })
        val state = CalendarControlState().also { it.open(AppScreen.SETTINGS) }
        assertEquals(AppScreen.CLASS_LIVE, state.applyEntry(parse("tumcal://live")!!))
    }
}
