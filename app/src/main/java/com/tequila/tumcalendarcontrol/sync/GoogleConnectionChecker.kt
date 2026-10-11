/*
 * Aufgabe: Zugriff auf gespeicherten Google-App-Kalender nur lesend prüfen.
 * Ein-/Ausgabe: Gespeicherte Konfiguration und Sitzungstoken -> bereinigtes Zugriffsergebnis.
 * Nebenwirkungen: Nur autorisierte lesende HTTPS-Prüfung, kein Erstellen oder Schreiben.
 * Nicht zuständig: OAuth-Tokenbeschaffung, Anlegen/Reparieren von Zielkalendern, Terminzugriffe, Diagnosespeicherung oder Ersatz der Benutzerbestätigung.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.i18n.message

sealed interface GoogleConnectionCheckResult {
    data object NoManagedCalendar : GoogleConnectionCheckResult
    data object Available : GoogleConnectionCheckResult
    data object Missing : GoogleConnectionCheckResult
    data object OwnershipUnverified : GoogleConnectionCheckResult
    data class Failed(val summary: String) : GoogleConnectionCheckResult

    val userSummary: String
        get() = when (this) {
            NoManagedCalendar -> message(74)
            Available -> message(75)
            Missing -> message(76)
            OwnershipUnverified -> message(77)
            is Failed -> summary
        }
}

class GoogleConnectionChecker(private val api: GoogleCalendarApi) {
    fun check(managedCalendarId: String): GoogleConnectionCheckResult {
        if (managedCalendarId.isBlank()) return GoogleConnectionCheckResult.NoManagedCalendar
        return runCatching { api.getCalendar(managedCalendarId) }
            .fold(
                onSuccess = { calendar ->
                    when {
                        calendar == null -> GoogleConnectionCheckResult.Missing
                        calendar.description == GOOGLE_OWNERSHIP_DESCRIPTION -> GoogleConnectionCheckResult.Available
                        else -> GoogleConnectionCheckResult.OwnershipUnverified
                    }
                },
                onFailure = { error ->
                    GoogleConnectionCheckResult.Failed(error.googleSafeSummary(message(78)))
                },
            )
    }
}
