/*
 * Aufgabe: Private Verbindungskonfiguration bearbeiten: Kursabonnement (verdeckt), Lesezeitraum-Hinweis, Google-Kalendername,
 *   Erinnerung beim Schreiben (wirkt sofort auf die nächste Schreibvorschau).
 * Ein-/Ausgabe: Gespeicherte Konfiguration und Entwurf -> validiert gespeicherte Konfiguration (schwebende Hauptaktion).
 * Nebenwirkungen: Keine externen Aktionen; Speichern über bestehenden Zustandsaufruf, danach zurück.
 * Nicht zuständig: iCal-Abruf, Google-Autorisierung (Einstellungsseite), Verschlüsselung oder Kalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.ui.design.DesignActionPage
import com.tequila.tumcalendarcontrol.ui.design.DesignField
import com.tequila.tumcalendarcontrol.ui.design.DesignNote
import com.tequila.tumcalendarcontrol.ui.design.DesignPillButton
import com.tequila.tumcalendarcontrol.ui.design.DesignSection
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.DesignChips
import com.tequila.tumcalendarcontrol.sync.CalendarReminderOptions
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors

/** Freier Text auf der Titellinie (4 dp innerhalb der Blockkanten). */
private val TextInset = Modifier.padding(horizontal = 4.dp)

/** Privaten Konfigurationsentwurf bearbeiten; gespeicherte Abonnementadresse niemals vorausfüllen oder anzeigen. */
@Composable
internal fun SyncConfigurationScreen(state: CalendarControlState) {
    val current = state.privateSyncConfiguration
    var replacementSubscriptionUrl by remember { mutableStateOf("") }
    var replacingSubscription by remember { mutableStateOf(!current.hasTumSubscription) }
    var calendarName by remember(current.googleCalendarName) { mutableStateOf(current.googleCalendarName) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    // Leere Ersatzadresse behält die gespeicherte; Speichern liest oder schreibt nichts.
    val save: () -> Unit = {
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
    }
    DesignActionPage(tr(message(558)), onAction = save, actionTag = "connection-save", contentTag = "connection-content") {
        DesignNote(tr(message(550)), tr(message(551, current.maskedTumSubscription())))
        if (replacingSubscription) {
            PrivateAddressInput(value = replacementSubscriptionUrl, onValueChange = { replacementSubscriptionUrl = it })
        } else {
            DesignPillButton(tr(message(552)), Modifier.testTag("connection-replace"), primary = false) { replacingSubscription = true }
        }
        DesignNote(tr(message(510)), tr(message(553)))
        DesignSection(tr(message(423)), TextInset)
        DesignText(tr(message(554)), 12, TextInset, muted = true)
        DesignField(tr(message(557)), calendarName, { calendarName = it }, Modifier.testTag("connection-calendar-name"), singleLine = true)
        DesignNote(tr(message(555)), tr(message(556)))
        // Erinnerung beim Schreiben: wirkt sofort auf die nächste Schreibvorschau (nicht auf „Speichern“ dieser Seite).
        DesignSection(tr(message(951)), TextInset)
        DesignText(tr(message(952)), 12, TextInset, muted = true)
        val reminder = state.calendarReminderMinutes
        DesignChips(
            listOf(tr(message(953))) + CalendarReminderOptions.map { tr(message(929, it)) },
            CalendarReminderOptions.indexOf(reminder).let { if (it < 0) 0 else it + 1 },
            Modifier.testTag("connection-reminder"),
        ) { index -> state.chooseCalendarReminder(if (index == 0) null else CalendarReminderOptions[index - 1]) }
        validationMessage?.let { DesignText(tr(it), 12, TextInset.testTag("connection-validation"), color = LocalDesignColors.current.danger) }
    }
}
