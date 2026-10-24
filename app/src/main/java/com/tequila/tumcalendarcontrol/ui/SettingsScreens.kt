/*
 * Aufgabe: Einstellungsseite (gruppierte Liste: Sprache, Kurse, Kalender, Einstellungen & Daten).
 * Ein-/Ausgabe: CalendarControlState und Rückrufe -> Navigations- und Einstellungsabsichten.
 * Nebenwirkungen: Keine externen Aktionen; Zieländerungen erst nach Glasbestätigung, sonst bestehende Zustandsaufrufe.
 * Nicht zuständig: Unterseiten (Darstellung, Profil, Verbindung, Prüfung, Sicherung, Termine), iCal-Abruf, Google-Netzwerk.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.BuildConfig
import com.tequila.tumcalendarcontrol.ui.design.DesignCheck
import com.tequila.tumcalendarcontrol.ui.design.DesignConfirmDialog
import com.tequila.tumcalendarcontrol.ui.design.DesignConfirmRequest
import com.tequila.tumcalendarcontrol.ui.design.DesignFrostCard
import com.tequila.tumcalendarcontrol.ui.design.DesignNote
import com.tequila.tumcalendarcontrol.ui.design.DesignPillButton
import com.tequila.tumcalendarcontrol.ui.design.DesignRow
import com.tequila.tumcalendarcontrol.ui.design.DesignSection
import com.tequila.tumcalendarcontrol.ui.design.DesignSegmented
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors

/** Freier Text auf der Titellinie (4 dp innerhalb der Blockkanten). */
private val TextInset = Modifier.padding(horizontal = 4.dp)

/** Schmal eine Liste; breit links Liste, rechts Erklärungen (keine doppelten Aktionen). Zieländerung mit Glasbestätigung. */
@Composable
internal fun SettingsScreen(state: CalendarControlState) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < 720.dp) {
            SettingsList(state, Modifier.fillMaxSize())
        } else {
            Row(Modifier.fillMaxSize()) {
                SettingsList(state, Modifier.weight(0.6f).fillMaxHeight())
                Box(Modifier.padding(top = 12.dp, bottom = 24.dp).width(1.dp).fillMaxHeight().background(LocalDesignColors.current.ink.copy(alpha = .10f)))
                SettingsWideSupportPane(state, Modifier.weight(0.4f).fillMaxHeight())
            }
        }
    }
    DesignConfirmDialog(state.pendingSyncDestinationChange?.let { change ->
        DesignConfirmRequest(
            kicker = tr(message(719)), title = tr(change.title), body = tr(change.consequence(state.syncDestinations)), note = tr(message(571)),
            cancelLabel = tr(message(197)), confirmLabel = tr(message(572)), danger = false,
            onCancel = state::cancelSyncDestinationChange, onConfirm = state::confirmSyncDestinationChange,
        )
    })
}

@Composable
private fun SettingsList(state: CalendarControlState, modifier: Modifier) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 8.dp).testTag("settings-list"),
        contentPadding = PaddingValues(bottom = FloatingNavigationContentClearance),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Sprache zuerst (wie bisher): bei falscher Sprache muss der Wechsel ohne Suchen erreichbar sein.
        item(key = "language") { Box(Modifier.padding(bottom = 16.dp)) { LanguageSelector() } }
        courseItems(state)
        calendarItems(state)
        preferenceItems(state)
        item(key = "footer") {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DesignNote(tr(message(504)), tr(message(505)))
                DesignNote(tr(message(514)), tr(message(515)))
                DesignText(tr(message(520)) + " · " + tr(message(521, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)), 11, TextInset, muted = true, mono = true)
                state.feedback?.let { DesignText(tr(it), 12, TextInset.testTag("settings-feedback"), muted = true) }
            }
        }
    }
}

/** Kurse: lesen, auswählen, Datenversion, Verbindung, eigene Campustermine. */
private fun LazyListScope.courseItems(state: CalendarControlState) {
    item(key = "courses") {
        Column {
            DesignSection(tr(message(146)), TextInset)
            val range = state.privateSyncConfiguration.sourceOutputWindow?.let {
                if (it.startsOn == null && it.endsOn == null) message(507) else message(508)
            } ?: message(509)
            DesignRow(tr(message(506)), detail = tr(range), tag = "settings-read", inset = 4.dp, onClick = state::openSourceRange)
            val courses = if (!state.sourceCatalogLoaded) message(511) else message(512, state.sourceSnapshotLabel, state.sourceCourses.size, state.selectedSourceOccurrences.size)
            DesignRow(tr(message(163)), detail = tr(courses), tag = "settings-select", inset = 4.dp, onClick = state::openSourceCourseSelector)
            if (state.sourceCatalogLoaded) SourceSnapshotSelection(state)
            DesignRow(tr(message(160)), tag = "settings-connection", inset = 4.dp, onClick = state::openSyncConfiguration)
            DesignRow(tr(message(165)), value = state.ruleLedger.manualEvents.size.toString(), tag = "settings-events", inset = 4.dp, onClick = state::openManualEvents)
        }
    }
}

/** Benutzer wählt erfolgreiche Datenversion; Wechsel ohne Netzwerk oder Kalenderänderung. */
@Composable
private fun SourceSnapshotSelection(state: CalendarControlState) {
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DesignText(tr(message(536)), 12, TextInset, muted = true)
        DesignText(tr(message(537, state.sourceSnapshotLabel)), 13, TextInset)
        if (state.hasPreviousSourceSnapshot) {
            val slots = listOf(SourceSnapshotSlot.LATEST, SourceSnapshotSlot.PREVIOUS)
            DesignSegmented(listOf(tr(message(539)), tr(message(541))), slots.indexOf(state.activeSourceSnapshotSlot), Modifier.testTag("settings-snapshot")) {
                state.selectSourceSnapshot(slots[it])
            }
            state.sourceSnapshotDifference?.let { DesignNote(tr(message(542)), tr(it.summary())) }
            DesignRow(tr(message(773)), tag = "settings-snapshot-changes", inset = 4.dp, onClick = state::openSourceChanges)
        }
    }
}

/** Kalender: Standardziele (Änderung mit Bestätigung), Google-Verbindung, Schreiben und getrennte Bereinigung. */
private fun LazyListScope.calendarItems(state: CalendarControlState) {
    item(key = "calendars") {
        Column(Modifier.padding(top = 16.dp)) {
            DesignSection(tr(message(724)), TextInset)
            DesignText(tr(message(568)), 12, TextInset.padding(vertical = 8.dp), muted = true)
            SyncDestination.entries.forEach { destination ->
                val enabled = state.syncDestinations.isEnabled(destination)
                DesignCheck(
                    tr(destination.title), enabled, TextInset.testTag("settings-destination-${destination.name}"),
                    detail = tr(if (enabled) destination.enabledSummary else destination.disabledSummary),
                ) { state.requestSyncDestinationChange(destination, !enabled) }
            }
            DesignText(tr(message(518)), 12, TextInset.padding(vertical = 8.dp), muted = true)
            if (state.syncDestinations.localCalendarEnabled) DesignNote(tr(message(569)), tr(message(570)))
            if (state.syncDestinations.googleCalendarEnabled) GoogleAuthorizationPanel(state)
            DesignRow(tr(message(519)), tag = "settings-write", inset = 4.dp, onClick = state::prepareCalendarWrite)
            if (state.managedOutputTargetsForCleanup.isNotEmpty()) {
                DesignRow(tr(message(167)), danger = true, tag = "settings-cleanup", inset = 4.dp, onClick = state::openOutputCleanup)
            }
        }
    }
}

/** Google-Status direkt neben den Zielen, damit Rückmeldungen auf langen Seiten sichtbar bleiben. */
@Composable
private fun GoogleAuthorizationPanel(state: CalendarControlState) {
    DesignFrostCard(Modifier.padding(vertical = 8.dp).testTag("settings-google")) {
        DesignText(tr(state.googleAuthorizationStatus.title), 16)
        DesignText(tr(state.googleAuthorizationDetail), 12, muted = true)
        val label = when (state.googleAuthorizationStatus) {
            GoogleAuthorizationStatus.NOT_CONNECTED -> message(423)
            GoogleAuthorizationStatus.AUTHORIZING -> message(559)
            GoogleAuthorizationStatus.AUTHORIZED -> message(560)
            GoogleAuthorizationStatus.FAILED -> message(561)
        }
        DesignPillButton(tr(label), primary = false, onClick = state::requestGoogleAuthorization)
        if (state.hasGoogleAuthorization) {
            val checkTitle = when (state.googleConnectionVerification) {
                GoogleConnectionVerification.NOT_CHECKED -> message(562)
                GoogleConnectionVerification.CHECKING -> message(563)
                GoogleConnectionVerification.VERIFIED -> message(564)
                GoogleConnectionVerification.ACTION_REQUIRED -> message(565)
                GoogleConnectionVerification.FAILED -> message(78)
            }
            DesignText(tr(checkTitle), 14)
            DesignText(tr(state.googleConnectionCheckDetail), 12, muted = true)
            DesignPillButton(
                tr(if (state.googleConnectionCheckInProgress) message(566) else message(567)), primary = false,
                enabled = !state.googleConnectionCheckInProgress, onClick = state::requestGoogleConnectionCheck,
            )
        }
    }
}

/** Einstellungen & Daten: Darstellung, Hintergrundprüfung, Sicherung. */
private fun LazyListScope.preferenceItems(state: CalendarControlState) {
    item(key = "preferences") {
        Column(Modifier.padding(top = 16.dp)) {
            DesignSection(tr(message(725)), TextInset)
            val palette = if (state.themeMode == ThemeMode.DARK) state.darkPaletteStyle.label else state.lightPaletteStyle.label
            DesignRow(tr(message(580)), detail = tr(message(728)), value = tr(palette), tag = "settings-appearance", inset = 4.dp) { state.open(AppScreen.APPEARANCE) }
            DesignRow(tr(message(811)), detail = tr(message(835)), tag = "settings-widgets", inset = 4.dp) { state.open(AppScreen.WIDGETS) }
            DesignRow(tr(message(168)), tag = "settings-background", inset = 4.dp, onClick = state::openBackgroundCheck)
            DesignRow(tr(message(516)), tag = "settings-backup", inset = 4.dp, onClick = state::openRuleBackup)
        }
    }
}

/** Breit rechts: Lage und Schutzregeln als Erklärung; konsistent mit der linken Liste. */
@Composable
private fun SettingsWideSupportPane(state: CalendarControlState, modifier: Modifier) {
    val targets = buildList {
        if (state.syncDestinations.localCalendarEnabled) add(message(522))
        if (state.syncDestinations.googleCalendarEnabled) add(message(523))
    }.let { com.tequila.tumcalendarcontrol.i18n.joinMessages(it, "、") }.ifBlank { message(524) }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 8.dp).padding(bottom = FloatingNavigationContentClearance),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DesignNote(tr(message(525)), tr(message(526)))
        DesignNote(tr(message(527)), tr(if (state.sourceCatalogLoaded) message(528, state.sourceSnapshotLabel, state.sourceCourses.size, state.selectedSourceOccurrences.size) else message(529)))
        DesignNote(tr(message(530)), tr(targets))
        DesignNote(tr(message(531)), tr(message(532)))
    }
}
