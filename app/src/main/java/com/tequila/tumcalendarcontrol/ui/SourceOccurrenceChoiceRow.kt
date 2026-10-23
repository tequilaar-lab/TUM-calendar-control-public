/*
 * Aufgabe: Einzeltermin mit verständlichem Datum, Zeit und Ort als Häkchenzeile des Designsystems auswählbar darstellen.
 * Ein-/Ausgabe: Quelltermin, Auswahlstatus und Rückruf -> Einzelterminauswahlabsicht.
 * Nebenwirkungen: Keine externen Aktionen; reine Darstellung, Auswahl über Rückruf.
 * Nicht zuständig: Kursgruppen, Instanzidentitätserzeugung, Regelbearbeitung oder Kandidatenberechnung.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.ui.design.DesignCheck
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val OccurrenceDisplayZone = ZoneId.of("Europe/Berlin")
private val OccurrenceEndDisplayTime = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Häkchenzeile eines Termins. Mit Titel (Datumsansicht): Kursname, darunter Zeit und Ort;
 * ohne Titel (unter einer Kursgruppe): Zeit, darunter Ort.
 */
@Composable
internal fun SourceOccurrenceChoiceRow(
    occurrence: SourceCalendarOccurrence,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    val time = tr(occurrence.toOccurrenceText(tr(message(628)), java.util.Locale.forLanguageTag(LocalAppLanguage.current.language.tag)))
    val place = if (occurrence.location.isNotBlank()) tr(message(629, occurrence.location)) else ""
    DesignCheck(
        label = if (showTitle) tr(occurrence.title) else time,
        checked = selected,
        modifier = modifier.testTag("source-occurrence"),
        detail = if (showTitle) listOf(time, place).filter(String::isNotEmpty).joinToString("  ·  ") else place,
    ) { onToggle() }
}

internal fun SourceCalendarOccurrence.toOccurrenceText(pattern: String = message(628), locale: java.util.Locale = java.util.Locale.SIMPLIFIED_CHINESE): String =
    message(630, (DateTimeFormatter.ofPattern(pattern, locale).format(startsAt.atZone(OccurrenceDisplayZone))), (OccurrenceEndDisplayTime.format(endsAt.atZone(OccurrenceDisplayZone))))
