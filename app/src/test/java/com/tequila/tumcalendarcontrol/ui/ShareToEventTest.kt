/*
 * Aufgabe: Teilen von Text an die App prüfen: Empfang im Manifest, Entwurf aus ACTION_SEND, Navigation zu den
 *   Campusterminen mit vorausgefülltem Formular und Hinweis; gespeichert wird erst durch den Nutzer.
 * Ein-/Ausgabe: synthetischer geteilter Text -> AppEntry, Zustand, Formularinhalt.
 * Nebenwirkungen: Nur Robolectric-Kontext und private Test-Präferenzen.
 * Nicht zuständig: Textanalyse im Detail (ManualEventTextParserTest).
 */
package com.tequila.tumcalendarcontrol.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.tequila.tumcalendarcontrol.i18n.AppLanguage
import com.tequila.tumcalendarcontrol.i18n.AppLanguagePreferences
import com.tequila.tumcalendarcontrol.sync.ManualEventKind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
class ShareToEventTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun share(text: String) = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)

    @Before fun chinese() = AppLanguagePreferences(context).save(AppLanguage.CHINESE)

    @Test fun `Empfang und Entwurf`() {
        assertTrue(context.packageManager.queryIntentActivities(share("x").setPackage(context.packageName), 0).any { it.activityInfo.name.endsWith(".MainActivity") })
        val entry = AppEntryIntents.parseShare(share("Klausur Synthetik am 02.11.2026 10:30-12:00"), LocalDate.of(2026, 10, 24)) as AppEntry.ManualEventDraftEntry
        assertEquals(ManualEventKind.EXAM, entry.draft.kind)
        assertNull("leerer Text", AppEntryIntents.parseShare(share("  "), LocalDate.of(2026, 10, 24)))
        assertNull("Bild statt Text", AppEntryIntents.parseShare(Intent(Intent.ACTION_SEND).setType("image/png"), LocalDate.of(2026, 10, 24)))
    }

    @Test fun `Formular vorausgefüllt, nichts gespeichert`() {
        val entry = AppEntryIntents.parseShare(share("Klausur Synthetik am 02.11.2026 10:30-12:00"), LocalDate.of(2026, 10, 24))!!
        val state = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT; it.open(AppScreen.SETTINGS) }
        assertEquals(AppScreen.MANUAL_EVENTS, state.applyEntry(entry))
        composeRule.setContent { CalendarControlSurface(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("events-from-share").assertExists()
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("events-title")) and hasText("Klausur Synthetik am 02.11.2026 10:30-12:00")).assertExists()
        assertNull("Entwurf übernommen", state.manualEventDraft)
        assertTrue("nichts gespeichert", state.ruleLedger.manualEvents.isEmpty())
    }
}
