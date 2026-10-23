/*
 * Aufgabe: Datumseingabe mit Systemdatumswahl: Feld bleibt frei editierbar, „Datum auswählen“ öffnet den Dialog und füllt ISO-Datum ein.
 * Ein-/Ausgabe: Beschriftung, Text und Rückruf -> Eingabefeld mit Wahlknopf; Dialog im aktuellen Hell/Dunkel-Thema.
 * Nebenwirkungen: Öffnet nur auf ausdrückliches Antippen den Systemdatumsdialog.
 * Nicht zuständig: Prüfung oder Auslegung des Datums (bestehende Parser), Speicherung.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import android.app.DatePickerDialog
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.R
import com.tequila.tumcalendarcontrol.ui.design.DesignField
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors
import com.tequila.tumcalendarcontrol.ui.design.designPressable
import java.time.LocalDate
import java.time.ZoneId

/** Eingabefeld mit „Datum auswählen“ rechts; vorhandener gültiger Text dient als Startdatum des Dialogs. */
@Composable
internal fun DateInputField(label: String, value: String, darkTheme: Boolean, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = localizedContext()
    val colors = LocalDesignColors.current
    DesignField(
        label, value, onValueChange, modifier, singleLine = true,
        trailing = {
            DesignText(
                tr(message(475)), 13, color = colors.accent, weight = FontWeight.Medium,
                modifier = Modifier.padding(end = 6.dp).heightIn(min = 48.dp)
                    .designPressable { showDatePicker(context, runCatching { LocalDate.parse(value.trim()) }.getOrNull(), darkTheme) { onValueChange(it.toString()) } }
                    .wrapContentHeight().padding(horizontal = 10.dp).testTag("date-pick"),
            )
        },
    )
}

/** Aktuelles Hell/Dunkel der App für den Systemdialog. */
@Composable
internal fun appUsesDarkTheme(state: CalendarControlState): Boolean = state.themeMode.usesDarkTheme(isSystemInDarkTheme())

/** Plattform-Datumsdialog im App-Thema; ohne Startwert heute (Europe/Berlin). */
internal fun showDatePicker(context: android.content.Context, current: LocalDate?, darkTheme: Boolean, onPicked: (LocalDate) -> Unit) {
    val seed = current ?: LocalDate.now(ZoneId.of("Europe/Berlin"))
    DatePickerDialog(
        context,
        if (darkTheme) R.style.Theme_TumCalendarControl_DatePicker_Dark else R.style.Theme_TumCalendarControl_DatePicker_Light,
        { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
        seed.year, seed.monthValue - 1, seed.dayOfMonth,
    ).show()
}
