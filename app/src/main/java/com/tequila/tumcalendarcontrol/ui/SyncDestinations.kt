/*
 * Aufgabe: Unabhängige Ausgabeziele und lokale Konfigurationswerte definieren.
 * Ein-/Ausgabe: Ziel und Schalterwert -> unveränderliche SyncDestinations.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Kontoautorisierung, Kalenderberechtigungen, Kalendererstellung, Terminänderungen oder Netzwerksynchronisierung.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.i18n.concatenateMessages

/** Unabhängige Ausgabeziele verwenden dieselben lokal geprüften Ergebnisse. */
enum class SyncDestination(
    val title: String,
    val disabledSummary: String,
    val enabledSummary: String,
) {
    LOCAL_CALENDAR(
        title = message(522),
        disabledSummary = message(656),
        enabledSummary = message(657),
    ),
    GOOGLE_CALENDAR(
        title = message(523),
        disabledSummary = message(658),
        enabledSummary = message(659),
    ),
}

/** Ausgabeschalter zunächst als Absicht; vor Bestätigung keine Präferenz-, Rechte- oder Autorisierungsänderung. */
data class PendingSyncDestinationChange(
    val destination: SyncDestination,
    val enabled: Boolean,
) {
    val title: String get() = if (enabled) message(660, (destination.title)) else message(661, (destination.title))
    /** Beschreibt unbestätigte Ausgabeänderung; keine Konten, Termine oder Kalender lesen. */
    fun consequence(current: SyncDestinations): String = when (destination) {
        SyncDestination.LOCAL_CALENDAR -> if (enabled) {
            concatenateMessages(message(662), current.duplicateDisplayHint(otherEnabled = current.googleCalendarEnabled))
        } else {
            message(663)
        }
        SyncDestination.GOOGLE_CALENDAR -> if (enabled) {
            concatenateMessages(message(664), current.duplicateDisplayHint(otherEnabled = current.localCalendarEnabled))
        } else {
            message(665)
        }
    }
}

private fun SyncDestinations.duplicateDisplayHint(otherEnabled: Boolean): String =
    if (otherEnabled) {
        message(666)
    } else {
        ""
    }

/** Nur künftige Ausgabeabsicht speichern; alles standardmäßig aus, um unbeabsichtigte externe Ausgabe zu verhindern. */
data class SyncDestinations(
    val localCalendarEnabled: Boolean = false,
    val googleCalendarEnabled: Boolean = false,
) {
    fun isEnabled(destination: SyncDestination): Boolean = when (destination) {
        SyncDestination.LOCAL_CALENDAR -> localCalendarEnabled
        SyncDestination.GOOGLE_CALENDAR -> googleCalendarEnabled
    }

    fun withEnabled(destination: SyncDestination, enabled: Boolean): SyncDestinations = when (destination) {
        SyncDestination.LOCAL_CALENDAR -> copy(localCalendarEnabled = enabled)
        SyncDestination.GOOGLE_CALENDAR -> copy(googleCalendarEnabled = enabled)
    }
}
