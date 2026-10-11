/*
 * Aufgabe: Lokales Profil mit Name und bildloser Avatarfarbe definieren.
 * Ein-/Ausgabe: Name und Farbwert -> unveränderliches lokales UserProfile.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Bildavatare, Galerie, Kontoidentität, Netzwerkübertragung oder Kalenderberechtigungen.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

enum class AvatarTone(val label: String) {
    TUM_BLUE(message(669)),
    SEA_GREEN(message(670)),
    SUNSET(message(671)),
    LAVENDER(message(672)),
}

data class UserProfile(
    val displayName: String = message(673),
    val avatarTone: AvatarTone = AvatarTone.TUM_BLUE,
)
