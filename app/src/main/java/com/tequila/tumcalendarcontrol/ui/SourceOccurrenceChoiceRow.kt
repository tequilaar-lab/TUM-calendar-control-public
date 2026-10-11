/*
 * Aufgabe: Einzeltermin mit verständlichem Datum, Zeit und Ort auswählbar darstellen.
 * Ein-/Ausgabe: Quelltermin, Auswahlstatus und Rückruf -> Einzelterminauswahlabsicht.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Kursgruppen, Instanzidentitätserzeugung, Regelbearbeitung oder Kandidatenberechnung.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val OccurrenceDisplayZone = ZoneId.of("Europe/Berlin")
private val OccurrenceEndDisplayTime = DateTimeFormatter.ofPattern("HH:mm")

@androidx.compose.runtime.Composable
internal fun SourceOccurrenceChoiceRow(
    occurrence: SourceCalendarOccurrence,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onToggle)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(tr(occurrence.title), style = MiuixTheme.textStyles.body1)
            Text(tr(occurrence.toOccurrenceText(tr(message(628)), java.util.Locale.forLanguageTag(LocalAppLanguage.current.language.tag))), color = LocalTumPalette.current.onSurfaceVariant)
            if (occurrence.location.isNotBlank()) Text(tr(message(629, (occurrence.location))), color = LocalTumPalette.current.onSurfaceVariant)
        }
    }
}

internal fun SourceCalendarOccurrence.toOccurrenceText(pattern: String = message(628), locale: java.util.Locale = java.util.Locale.SIMPLIFIED_CHINESE): String =
    message(630, (DateTimeFormatter.ofPattern(pattern, locale).format(startsAt.atZone(OccurrenceDisplayZone))), (OccurrenceEndDisplayTime.format(endsAt.atZone(OccurrenceDisplayZone))))
