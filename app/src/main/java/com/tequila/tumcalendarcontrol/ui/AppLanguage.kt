/*
 * Aufgabe: drei Anzeigesprachen wählen, lokal speichern und Compose-Ressourcen bereitstellen.
 * Ein-/Ausgabe: Sprachwahl/Systemsprache -> lokalisierte App-Texte; Benutzereingaben bleiben unverändert.
 * Nebenwirkungen: nur app-private Sprachpräferenz; kein Neustart, keine Berechtigungen oder Kalenderzugriffe.
 * Nicht zuständig: fachliche Zustände, Kursübersetzung, Synchronisierung oder Kontoeinstellungen.
 */
package com.tequila.tumcalendarcontrol.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.R
import androidx.compose.ui.platform.testTag
import com.tequila.tumcalendarcontrol.ui.design.DesignSection
import com.tequila.tumcalendarcontrol.ui.design.DesignSegmented
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.i18n.AppLanguage
import com.tequila.tumcalendarcontrol.i18n.localizedAppContext
import com.tequila.tumcalendarcontrol.i18n.localizedMessage

internal data class LanguageSelection(val language: AppLanguage = AppLanguage.CHINESE, val select: (AppLanguage) -> Unit = {})
internal val LocalAppLanguage = staticCompositionLocalOf { LanguageSelection() }
/** Nutzt sprachspezifische Ressourcen ohne Activity-Neustart; dadurch bleiben Entwürfe erhalten. */
@Composable
internal fun localizedContext(): Context {
    val context = LocalContext.current
    val language = LocalAppLanguage.current.language
    return remember(context, language) {
        localizedAppContext(context, language)
    }
}

@Composable
internal fun tr(value: String): String {
    val context = localizedContext()
    return localizedMessage(context, value)
}

/** Sprache als Segmentsteuerung (Titel und Hinweis aus Sprachressourcen); Wechsel wirkt sofort und wird gespeichert. */
@Composable
internal fun LanguageSelector() {
    val selection = LocalAppLanguage.current
    val context = localizedContext()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DesignSection(context.getString(R.string.language_title), Modifier.padding(start = 4.dp))
        DesignText(context.getString(R.string.language_note), 12, Modifier.padding(horizontal = 4.dp), muted = true)
        DesignSegmented(AppLanguage.entries.map { it.nativeName }, AppLanguage.entries.indexOf(selection.language), Modifier.testTag("settings-language")) {
            selection.select(AppLanguage.entries[it])
        }
    }
}
