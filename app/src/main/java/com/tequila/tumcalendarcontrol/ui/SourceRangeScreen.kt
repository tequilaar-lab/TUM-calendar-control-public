/*
 * Aufgabe: Lesezeitraum bearbeiten, getrennt vom Zeitraum bereits gelesener Daten (neues Erscheinungsbild).
 * Ein-/Ausgabe: Private Konfiguration und gelesene Zusammenfassung -> validierter Leseentwurf oder Leseabsicht.
 * Nebenwirkungen: Nur ausdrücklich geöffneter Datumsdialog; Speichern/Lesen über den App-Einstieg.
 * Nicht zuständig: iCal-Netzwerk, Quellen-Parsing, Google-Autorisierung oder Kalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import android.app.DatePickerDialog
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.R
import com.tequila.tumcalendarcontrol.ui.design.DesignActionPage
import com.tequila.tumcalendarcontrol.ui.design.DesignFrostCard
import com.tequila.tumcalendarcontrol.ui.design.DesignMetrics
import com.tequila.tumcalendarcontrol.ui.design.DesignNote
import com.tequila.tumcalendarcontrol.ui.design.DesignPillButton
import com.tequila.tumcalendarcontrol.ui.design.DesignRow
import com.tequila.tumcalendarcontrol.ui.design.DesignSection
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DateText = DateTimeFormatter.ISO_LOCAL_DATE
private val BerlinZone = ZoneId.of("Europe/Berlin")

/** Freier Text auf der Titellinie (4 dp innerhalb der Blockkanten). */
private val TextInset = Modifier.padding(horizontal = 4.dp)

/**
 * Lesezeitraum für den nächsten Lesevorgang, gespeicherte Kurse und erneutes Lesen.
 * Schwebende Hauptaktion: mit gespeicherten Kursen „weiter ohne Neulesen“, sonst „Kurse lesen“.
 */
@Composable
fun SourceRangeScreen(state: CalendarControlState) {
    val configuration = state.privateSyncConfiguration
    val usesDarkTheme = state.themeMode.usesDarkTheme(isSystemInDarkTheme())
    var startDate by rememberSaveable(configuration.sourceRangeStartDate) { mutableStateOf(configuration.sourceRangeStartDate) }
    var endDate by rememberSaveable(configuration.sourceRangeEndDate) { mutableStateOf(configuration.sourceRangeEndDate) }
    var validation by remember { mutableStateOf<List<String>>(emptyList()) }
    val hasSaved = state.sourcePreview != null
    val context = localizedContext()
    DesignActionPage(
        actionLabel = tr(if (hasSaved) message(633) else if (state.sourceReadInProgress) message(635) else message(506)),
        onAction = { validation = if (hasSaved) state.continueWithSavedCourses() else state.requestSourceReadForRange(startDate, endDate) },
        actionEnabled = !state.sourceReadInProgress,
        actionTag = "source-range-action",
        contentTag = "source-range-content",
    ) {
        // Nächster Lesezeitraum: wirkt nur auf neues Lesen, kürzt keine gespeicherten Kurse.
        DesignSection(tr(message(711)), TextInset)
        DesignRow(tr(message(465)), detail = tr(startDate.ifBlank { message(474) }), value = tr(message(475)), tag = "source-range-start", inset = 4.dp) {
            showDatePicker(context, startDate, usesDarkTheme) { startDate = it }
        }
        DesignRow(tr(message(466)), detail = tr(endDate.ifBlank { message(474) }), value = tr(message(475)), tag = "source-range-end", inset = 4.dp) {
            showDatePicker(context, endDate, usesDarkTheme) { endDate = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DesignPillButton(tr(message(642)), Modifier.weight(1f), primary = false) { startDate = LocalDate.now(BerlinZone).format(DateText); endDate = "" }
            DesignPillButton(tr(message(643)), Modifier.weight(1f), primary = false) {
                val today = LocalDate.now(BerlinZone)
                startDate = today.format(DateText)
                endDate = today.plusDays(89).format(DateText)
            }
        }
        DesignRow(tr(message(644)), inset = 4.dp) { startDate = ""; endDate = "" }
        DesignPillButton(tr(message(632)), primary = false) { validation = state.saveSourceOutputRange(startDate, endDate) }
        validation.forEach { DesignText(tr(it), 12, TextInset.testTag("source-range-validation"), color = LocalDesignColors.current.danger) }
        DesignText(tr(message(631)), 12, TextInset, muted = true)
        SavedCoursesCard(state)
        // Erneut lesen nur mit Bestätigung: neue Datenversion, aktuelle Auswahl wird geleert.
        if (state.sourceReloadConfirmationPending) {
            SourceReloadConfirmationCard(state)
        } else if (hasSaved) {
            DesignPillButton(tr(message(638)), Modifier.testTag("source-reload"), primary = false, enabled = !state.sourceReadInProgress) {
                validation = state.requestSourceReadForRange(startDate, endDate)
            }
        }
        DesignNote(tr(message(636)), tr(message(637)))
    }
}

/** Gespeicherte Kurse: Version, Kurs- und Terminzahl, gelesener Zeitraum; ohne Daten ein Hinweis. */
@Composable
private fun SavedCoursesCard(state: CalendarControlState) {
    val preview = state.sourcePreview
    DesignFrostCard(Modifier.testTag("source-saved-card")) {
        DesignSection(tr(if (preview == null) message(647) else state.sourceSnapshotLabel))
        if (preview == null) {
            DesignText(tr(message(645)), 13, muted = true)
        } else {
            val occurrences = state.currentSourceOccurrences
            DesignMetrics(
                tr(message(146)) to SourceBulkSelection.unfilteredGroups(occurrences, emptySet()).size.toString(),
                tr(message(712)) to SourceBulkSelection.allSelectable(occurrences).size.toString(),
            )
            DesignText(tr(message(646, preview.firstManageableStart.toLocalDateText(), preview.lastManageableEnd.toLocalDateText(), preview.manageableOccurrenceCount)), 13, muted = true)
            DesignText(tr(message(634, state.sourceSnapshotLabel)), 12, muted = true)
        }
    }
}

/** Neulesebestätigung speichert nur neue Datenversion; eine frühere bleibt wählbar. Fortfahren ist keine Synchronisierung. */
@Composable
private fun SourceReloadConfirmationCard(state: CalendarControlState) {
    DesignFrostCard(Modifier.testTag("source-reload-confirmation")) {
        DesignText(tr(message(638)), 16)
        DesignText(tr(message(639)), 12, muted = true)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DesignPillButton(tr(message(197)), Modifier.weight(1f), primary = false) { state.resolveSourceReloadConfirmation(false) }
            DesignPillButton(tr(message(640)), Modifier.weight(1f)) { state.resolveSourceReloadConfirmation(true) }
        }
    }
}

private fun Instant?.toLocalDateText(): String = this?.atZone(BerlinZone)?.toLocalDate()?.format(DateText) ?: message(648)

/** Plattform-Datumsdialog verwendet aufgelösten App-Hell/Dunkelmodus. */
private fun showDatePicker(context: android.content.Context, it: String, usesDarkTheme: Boolean, onPicked: (String) -> Unit) {
    val initial = runCatching { LocalDate.parse(it) }.getOrElse { LocalDate.now(BerlinZone) }
    DatePickerDialog(
        context,
        if (usesDarkTheme) R.style.Theme_TumCalendarControl_DatePicker_Dark else R.style.Theme_TumCalendarControl_DatePicker_Light,
        { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day).format(DateText)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}
