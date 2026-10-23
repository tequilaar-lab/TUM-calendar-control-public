/*
 * Aufgabe: Lokales Profil bearbeiten: Anzeigename und Avatarfarbe als Entwurf, Speichern über schwebende Hauptaktion.
 * Ein-/Ausgabe: Gespeichertes Profil -> Entwurf -> gespeichertes Profil (App-Einstieg persistiert).
 * Nebenwirkungen: Keine externen Aktionen; kein Konto, keine Synchronisierung.
 * Nicht zuständig: Google/TUM-Konten, Darstellungseinstellungen oder Navigation außer Rückkehr.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.ui.design.DesignActionPage
import com.tequila.tumcalendarcontrol.ui.design.DesignChoice
import com.tequila.tumcalendarcontrol.ui.design.DesignField
import com.tequila.tumcalendarcontrol.ui.design.DesignNote
import com.tequila.tumcalendarcontrol.ui.design.DesignSection

/** Profil zunächst als Entwurf; erst Speichern aktualisiert den Zustand. Avatar rechts zeigt die Farbe mit dem Entwurfsnamen. */
@Composable
internal fun ProfileScreen(state: CalendarControlState) {
    var draftName by rememberSaveable(state.profile) { mutableStateOf(state.profile.displayName) }
    var draftTone by rememberSaveable(state.profile) { mutableStateOf(state.profile.avatarTone) }
    DesignActionPage(
        tr(message(579)),
        onAction = {
            state.updateProfile(UserProfile(draftName, draftTone))
            state.handleBack()
        },
        actionTag = "profile-save", contentTag = "profile-content",
    ) {
        DesignNote(tr(message(573)), tr(message(574)))
        DesignField(tr(message(575)), draftName, { draftName = it }, Modifier.testTag("profile-name"), singleLine = true)
        DesignSection(tr(message(576)), Modifier.padding(start = 4.dp))
        AvatarTone.entries.forEach { tone ->
            DesignChoice(
                tr(tone.label), tone == draftTone, Modifier.testTag("profile-tone-${tone.name}"),
                detail = tr(if (tone == draftTone) message(577) else message(578)),
                trailing = { ProfileAvatar(UserProfile(draftName, tone), modifier = Modifier.size(36.dp)) },
            ) { draftTone = tone }
        }
    }
}
