/*
 * Aufgabe: Sprachspeicherung und Android-Ressourcen für alle drei Sprachen prüfen.
 * Ein-/Ausgabe: synthetische Präferenzen und Ressourcen -> JUnit-Prüfergebnisse.
 * Nebenwirkungen: ausschließlich Robolectric-App-Speicher; keine Konten/Netzwerke/Kalender.
 * Nicht zuständig: Geräteinstallation oder tatsächliche Kalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.AppLanguage
import com.tequila.tumcalendarcontrol.i18n.AppLanguagePreferences

import android.content.Context
import android.content.res.Configuration
import com.tequila.tumcalendarcontrol.R
import com.tequila.tumcalendarcontrol.i18n.messageSources
import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.i18n.renderMessage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppLanguageTest {
    @Test fun selectionSurvivesPreferenceRecreation() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("app_language", Context.MODE_PRIVATE).edit().clear().commit()
        AppLanguage.entries.forEach { language ->
            AppLanguagePreferences(context).save(language)
            assertEquals(language, AppLanguagePreferences(context).load())
        }
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("fr"))
    }

    @Test fun everyLocaleContainsEveryTemplateAndPreservesPlaceholders() {
        val context = RuntimeEnvironment.getApplication()
        val placeholder = Regex("\\{\\d+\\}")
        AppLanguage.entries.forEach { language ->
            val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language.tag))
            })
            messageSources.forEach { (key, source) ->
                val id = localized.resources.getIdentifier("msg_" + key.toString().padStart(4, '0'), "string", localized.packageName)
                assertTrue("Missing $key for $language", id != 0)
                val template = localized.getString(id)
                assertEquals("Placeholder mismatch $key $language", placeholder.findAll(source).map { it.value }.sorted().toList(), placeholder.findAll(template).map { it.value }.sorted().toList())
                if (language == AppLanguage.CHINESE) assertEquals("Canonical mismatch $key", source, template)
                else assertFalse("Chinese fallback $key $language", template.any { it in '\u3400'..'\u9fff' })
            }
            val owned = message(197)
            val result = renderMessage(owned) { key -> localized.getString(localized.resources.getIdentifier("msg_" + key.toString().padStart(4, '0'), "string", localized.packageName)) }
            assertEquals(when(language) { AppLanguage.CHINESE -> "取消"; AppLanguage.GERMAN -> "Abbrechen"; AppLanguage.ENGLISH -> "Cancel" }, result)
            assertTrue(localized.getString(R.string.language_title).isNotBlank())
        }
    }
}
