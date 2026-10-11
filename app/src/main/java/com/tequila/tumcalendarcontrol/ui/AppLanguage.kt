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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.R
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
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

@Composable
internal fun LanguageSelector() {
    val selection = LocalAppLanguage.current
    val context = localizedContext()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(context.getString(R.string.language_title))
        Text(context.getString(R.string.language_note))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AppLanguage.entries.forEach { language ->
                Button(onClick = { selection.select(language) }, modifier = Modifier.weight(1f)) {
                    Text((if (selection.language == language) "✓ " else "") + language.nativeName)
                }
            }
        }
    }
}
