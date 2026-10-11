/*
 * Aufgabe: lokale Sprachpräferenz und Android-Ressourcen für Anzeige/Benachrichtigung bereitstellen.
 * Ein-/Ausgabe: Context und Sprachwahl -> lokalisierter Context und App-Nachricht.
 * Nebenwirkungen: nur app-private Sprachpräferenz; keine Rechte, Netzwerke oder Kalender.
 * Nicht zuständig: Compose-Zustand, fachliche Regeln oder Übersetzen von Nutzerdaten.
 */
package com.tequila.tumcalendarcontrol.i18n

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

internal enum class AppLanguage(val tag: String, val nativeName: String) {
    CHINESE("zh", "中文"), GERMAN("de", "Deutsch"), ENGLISH("en", "English");
    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: ENGLISH
    }
}
internal class AppLanguagePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("app_language", Context.MODE_PRIVATE)
    fun load(): AppLanguage = AppLanguage.fromTag(preferences.getString("language", Locale.getDefault().language))
    fun save(language: AppLanguage) { preferences.edit().putString("language", language.tag).apply() }
}

internal fun localizedAppContext(context: Context, language: AppLanguage): Context =
    context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language.tag)) })

internal fun localizedMessage(context: Context, value: String): String = renderMessage(value) { key ->
    val id = context.resources.getIdentifier("msg_" + key.toString().padStart(4, '0'), "string", context.packageName)
    check(id != 0) { "Missing message resource: $key" }
    context.getString(id)
}
