/*
 * Aufgabe: Vorschläge für einen Kurznamen als antippbare Chips unter dem Eingabefeld (nur solange das Feld leer ist).
 * Ein-/Ausgabe: Kurstitel und aktueller Eingabetext -> Chips; Antippen -> gewählter Vorschlag an den Aufrufer.
 * Nebenwirkungen: Keine; speichert nichts selbst.
 * Nicht zuständig: Erzeugen der Vorschläge (ShortNameSuggester) oder Speichern (CourseShortNames).
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.tequila.tumcalendarcontrol.ui.design.DesignChips
import com.tequila.tumcalendarcontrol.widget.ShortNameSuggester

@Composable
internal fun ShortNameSuggestions(title: String, current: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    if (current.isNotBlank()) return
    val suggestions = remember(title) { ShortNameSuggester.suggest(title) }
    if (suggestions.isEmpty()) return
    // Keine Vorauswahl (-1): Chips dienen nur als Abkürzung zum Eintippen.
    DesignChips(suggestions, -1, modifier) { onPick(suggestions[it]) }
}
