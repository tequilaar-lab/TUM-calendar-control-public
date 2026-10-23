/*
 * Aufgabe: Einstellungen, Ausgabeziele, private Konfiguration, Profil und Darstellung rendern.
 * Ein-/Ausgabe: CalendarControlState und Rückrufe -> scrollbare Einstellungsseiten.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Seitenrouting, Kursansicht/-bearbeitung, Teilnahmeformulare, iCal-Abruf, Google-Netzwerk oder Calendar Provider.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.BuildConfig
import com.tequila.tumcalendarcontrol.domain.TumEventStatus
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.window.Dialog

/** Weitere Aktionen innerhalb vollständiger Überlagerung; keine eigene Überlagerungs- oder Rückkehranimation. */
@Composable
internal fun MoreScreen(state: CalendarControlState) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InformationCard(message(496), if (state.sourceCatalogLoaded) message(497) else message(498))
        InformationCard(message(499), message(500))
        InformationCard(message(501), message(502))
        Button(onClick = { state.open(AppScreen.SETTINGS) }, modifier = Modifier.fillMaxWidth()) {
            Text(tr(message(503)))
        }
    }
}

@Composable
internal fun SettingsScreen(state: CalendarControlState) {
    val palette = LocalTumPalette.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < 720.dp) {
            SettingsList(state, palette, Modifier.fillMaxSize())
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsList(state, palette, Modifier.weight(0.62f).fillMaxHeight())
                SettingsWideSupportPane(state, Modifier.weight(0.38f).fillMaxHeight())
            }
        }
    }
    state.pendingSyncDestinationChange?.let { change ->
        SyncDestinationChangeDialog(
            change = change,
            currentDestinations = state.syncDestinations,
            onDismiss = state::cancelSyncDestinationChange,
            onConfirm = state::confirmSyncDestinationChange,
        )
    }
}

/** Breite Ansicht: Aktionen links, Status rechts; Einstellungsflächen nicht unnötig verbreitern. */
@Composable
private fun SettingsList(state: CalendarControlState, palette: TumPalette, modifier: Modifier) {
    LazyColumn(
        modifier = modifier.testTag("settings-list"),
        contentPadding = PaddingValues(bottom = FloatingNavigationContentClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { InformationCard(message(504), message(505)) }
        item { LanguageSelector() }
        item { Button(onClick = state::openSourceRange, modifier = Modifier.fillMaxWidth()) { Text(tr(message(506))) } }
        item {
            val summary = state.privateSyncConfiguration.sourceOutputWindow?.let {
                if (it.startsOn == null && it.endsOn == null) message(507) else message(508)
            } ?: message(509)
            InformationCard(message(510), summary)
        }
        item {
            val courseSummary = when {
                !state.sourceCatalogLoaded -> message(511)
                else -> message(512, (state.sourceSnapshotLabel), (state.sourceCourses.size), (state.selectedSourceOccurrences.size))
            }
            InformationCard(message(513), courseSummary)
        }
        if (state.sourceCatalogLoaded) {
            item { SourceSnapshotSelectionCard(state) }
        }
        item { Button(onClick = state::openSourceCourseSelector, modifier = Modifier.fillMaxWidth()) { Text(tr(message(163))) } }
        item { Button(onClick = state::openSyncConfiguration, modifier = Modifier.fillMaxWidth()) { Text(tr(message(160))) } }
        item { InformationCard(message(514), message(515)) }
        item { Button(onClick = state::openManualEvents, modifier = Modifier.fillMaxWidth()) { Text(tr(message(430))) } }
        item { Button(onClick = state::openBackgroundCheck, modifier = Modifier.fillMaxWidth()) { Text(tr(message(168))) } }
        item { Button(onClick = state::openRuleBackup, modifier = Modifier.fillMaxWidth()) { Text(tr(message(516))) } }
        item { SyncDestinationSelector(state) }
        item { InformationCard(message(517), message(518)) }
        if (state.syncDestinations.googleCalendarEnabled) item { GoogleAuthorizationPanel(state) }
        item { Button(onClick = state::prepareCalendarWrite, modifier = Modifier.fillMaxWidth()) { Text(tr(message(519))) } }
        if (state.managedOutputTargetsForCleanup.isNotEmpty()) {
            item { Button(onClick = state::openOutputCleanup, modifier = Modifier.fillMaxWidth()) { Text(tr(message(167))) } }
        }
        item { InformationCard(message(520), message(521, (BuildConfig.VERSION_NAME), (BuildConfig.VERSION_CODE))) }
        item { ThemeModeSelector(state) }
        item { AppearancePaletteSelector(state) }
        item { Spacer(Modifier.height(4.dp)) }
        state.feedback?.let { feedback -> item { Text(tr(feedback), color = palette.onSurfaceVariant) } }
    }
}

/** Rechts nur Erklärung, keine doppelten Aktionen; konsistenter Zustand beider Spalten. */
@Composable
private fun SettingsWideSupportPane(state: CalendarControlState, modifier: Modifier) {
    val targets = buildList {
        if (state.syncDestinations.localCalendarEnabled) add(message(522))
        if (state.syncDestinations.googleCalendarEnabled) add(message(523))
    }.let { com.tequila.tumcalendarcontrol.i18n.joinMessages(it, "、") }.ifBlank { message(524) }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(bottom = FloatingNavigationContentClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InformationCard(message(525), message(526))
        InformationCard(
            message(527),
            if (state.sourceCatalogLoaded) message(528, (state.sourceSnapshotLabel), (state.sourceCourses.size), (state.selectedSourceOccurrences.size)) else message(529),
        )
        InformationCard(message(530), targets)
        InformationCard(message(531), message(532))
    }
}

@Composable
private fun SourceStatusSelector(state: CalendarControlState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr(message(533)), style = MiuixTheme.textStyles.title3)
        if (state.sourceCatalogLoaded) {
            Text(tr(message(534, (state.sourceSnapshotLabel))), color = LocalTumPalette.current.onSurfaceVariant)
        } else {
            Text(tr(message(535)), color = LocalTumPalette.current.onSurfaceVariant)
            TumEventStatus.entries.forEach { status ->
                Switch(checked = status in state.visibleSourceStatuses, onCheckedChange = { state.toggleSourceStatusVisible(status) })
                Text(tr(status.label), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Benutzer wählt erfolgreiche Datenversion; Wechsel ohne Netzwerk oder Kalenderänderung. */
@Composable
private fun SourceSnapshotSelectionCard(state: CalendarControlState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InformationCard(
            message(536),
            message(537, (state.sourceSnapshotLabel)),
        )
        if (state.hasPreviousSourceSnapshot) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { state.selectSourceSnapshot(SourceSnapshotSlot.LATEST) },
                    modifier = Modifier.weight(1f),
                ) { Text(tr(if (state.activeSourceSnapshotSlot == SourceSnapshotSlot.LATEST) message(538) else message(539))) }
                Button(
                    onClick = { state.selectSourceSnapshot(SourceSnapshotSlot.PREVIOUS) },
                    modifier = Modifier.weight(1f),
                ) { Text(tr(if (state.activeSourceSnapshotSlot == SourceSnapshotSlot.PREVIOUS) message(540) else message(541))) }
            }
            state.sourceSnapshotDifference?.let { difference ->
                InformationCard(message(542), difference.summary())
            }
        }
    }
}

/** Privaten Konfigurationsentwurf bearbeiten; gespeicherte Abonnementadresse niemals vorausfüllen. */
@Composable
internal fun SyncConfigurationScreen(state: CalendarControlState) {
    val current = state.privateSyncConfiguration
    var replacementSubscriptionUrl by remember { mutableStateOf("") }
    var replacingSubscription by remember { mutableStateOf(!current.hasTumSubscription) }
    var calendarName by remember(current.googleCalendarName) { mutableStateOf(current.googleCalendarName) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InformationCard(
            message(550),
            message(551, (current.maskedTumSubscription())),
        )
        if (replacingSubscription) {
            PrivateAddressInput(
                value = replacementSubscriptionUrl,
                onValueChange = { replacementSubscriptionUrl = it },
            )
        } else {
            Button(onClick = { replacingSubscription = true }, modifier = Modifier.fillMaxWidth()) { Text(tr(message(552))) }
        }
        InformationCard(message(510), message(553))
        InformationCard(message(423), message(554))
        InformationCard(message(555), message(556))
        TextField(
            value = calendarName,
            onValueChange = { calendarName = it },
            modifier = Modifier.fillMaxWidth(),
            label = tr(message(557)),
        )
        validationMessage?.let { Text(tr(it), color = LocalTumPalette.current.onSurfaceVariant) }
        Button(
            onClick = {
                val updated = current.copy(
                    tumSubscriptionUrl = replacementSubscriptionUrl.trim().ifBlank { current.tumSubscriptionUrl },
                    googleOAuthClientId = "",
                    googleCalendarName = calendarName,
                )
                val problems = updated.validate()
                if (problems.isEmpty()) {
                    state.updatePrivateSyncConfiguration(updated)
                    state.handleBack()
                } else {
                    validationMessage = com.tequila.tumcalendarcontrol.i18n.joinMessages(problems.map { it.message })
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(tr(message(558))) }
    }
}

/** Google-Status direkt neben Verbindung zeigen, damit Rückmeldungen auf langen Seiten sichtbar bleiben. */
@Composable
private fun GoogleAuthorizationPanel(state: CalendarControlState) {
    InformationCard(state.googleAuthorizationStatus.title, state.googleAuthorizationDetail)
    Button(onClick = state::requestGoogleAuthorization, modifier = Modifier.fillMaxWidth()) {
        val label = when (state.googleAuthorizationStatus) {
            GoogleAuthorizationStatus.NOT_CONNECTED -> message(423)
            GoogleAuthorizationStatus.AUTHORIZING -> message(559)
            GoogleAuthorizationStatus.AUTHORIZED -> message(560)
            GoogleAuthorizationStatus.FAILED -> message(561)
        }
        Text(tr(label))
    }
    if (state.hasGoogleAuthorization) {
        val checkTitle = when (state.googleConnectionVerification) {
            GoogleConnectionVerification.NOT_CHECKED -> message(562)
            GoogleConnectionVerification.CHECKING -> message(563)
            GoogleConnectionVerification.VERIFIED -> message(564)
            GoogleConnectionVerification.ACTION_REQUIRED -> message(565)
            GoogleConnectionVerification.FAILED -> message(78)
        }
        InformationCard(checkTitle, state.googleConnectionCheckDetail)
        Button(
            onClick = state::requestGoogleConnectionCheck,
            enabled = !state.googleConnectionCheckInProgress,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(tr(if (state.googleConnectionCheckInProgress) message(566) else message(567))) }
    }
}

/** Ausgabeziele unabhängig; Schalter speichert nur Zukunftserlaubnis und startet keine unkonfigurierte Synchronisierung. */
@Composable
private fun SyncDestinationSelector(state: CalendarControlState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr(message(530)), style = MiuixTheme.textStyles.title3)
        Text(tr(message(568)), color = LocalTumPalette.current.onSurfaceVariant)
        SyncDestination.entries.forEach { destination ->
            val enabled = state.syncDestinations.isEnabled(destination)
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tr(destination.title), style = MiuixTheme.textStyles.body1)
                        Text(
                            tr(if (enabled) destination.enabledSummary else destination.disabledSummary),
                            color = LocalTumPalette.current.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { requested ->
                            state.requestSyncDestinationChange(destination, requested)
                        },
                    )
                }
            }
        }
        InformationCard(message(569), message(570))
    }
}

@Composable
private fun SyncDestinationChangeDialog(
    change: PendingSyncDestinationChange,
    currentDestinations: SyncDestinations,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr(change.title), style = MiuixTheme.textStyles.title2)
                Text(tr(change.consequence(currentDestinations)))
                Text(tr(message(571)), color = LocalTumPalette.current.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(tr(message(197))) }
                    Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text(tr(message(572))) }
                }
            }
        }
    }
}

/** Profil zunächst als Entwurf; erst Speichern aktualisiert Zustand, App-Einstieg persistiert private Präferenzen. */
@Composable
internal fun ProfileScreen(state: CalendarControlState) {
    var draftName by androidx.compose.runtime.saveable.rememberSaveable(state.profile) { mutableStateOf(state.profile.displayName) }
    var draftTone by androidx.compose.runtime.saveable.rememberSaveable(state.profile) { mutableStateOf(state.profile.avatarTone) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InformationCard(message(573), message(574))
        TextField(
            value = draftName,
            onValueChange = { draftName = it },
            modifier = Modifier.fillMaxWidth(),
            label = tr(message(575)),
        )
        Text(tr(message(576)), style = MiuixTheme.textStyles.title3)
        AvatarTone.entries.forEach { tone ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { draftTone = tone },
                insideMargin = PaddingValues(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(UserProfile(draftName, tone), modifier = Modifier.size(40.dp))
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(tr(tone.label), style = MiuixTheme.textStyles.body1)
                        Text(
                            tr(if (tone == draftTone) message(577) else message(578)),
                            color = LocalTumPalette.current.onSurfaceVariant,
                        )
                    }
                    RadioButton(
                        selected = tone == draftTone,
                        onClick = { draftTone = tone },
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
        Button(
            onClick = {
                state.updateProfile(UserProfile(draftName, draftTone))
                state.handleBack()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(tr(message(579))) }
    }
}

@Composable
private fun ThemeModeSelector(state: CalendarControlState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr(message(580)), style = MiuixTheme.textStyles.title3)
        ThemeMode.entries.forEach { mode ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { state.themeMode = mode },
                insideMargin = PaddingValues(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.themeMode == mode,
                        onClick = { state.themeMode = mode },
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(tr(mode.label), style = MiuixTheme.textStyles.body1)
                        Text(
                            tr(if (mode == ThemeMode.SYSTEM) message(581) else message(582)),
                            color = LocalTumPalette.current.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
