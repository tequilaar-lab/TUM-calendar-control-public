/*
 * Aufgabe: Aktionen des CalendarControlState zur Google-Autorisierung und Verbindungsprüfung (nur Absichten und Ergebnisse;
 *   Token bleiben im Prozess; aus CalendarControlState ausgelagert, Verhalten unverändert).
 * Ein-/Ausgabe: Nutzerabsichten und Autorisierungs-/Prüfergebnisse -> Zustand des Ausgabeablaufs, Rückmeldungen.
 * Nebenwirkungen: Keine externen Aktionen; Autorisierung und Prüfung führt CalendarControlApp aus.
 * Nicht zuständig: Tokenspeicherung, Kalenderzugriff oder Darstellung.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.sync.GoogleConnectionCheckResult
import com.tequila.tumcalendarcontrol.sync.OutputTarget

/** Google-Autorisierung nur nach ausdrücklichem Antippen; Token bleibt nur im Prozess. */
fun CalendarControlState.requestGoogleAuthorization() {
    val rejection = outputWorkflow.requestGoogleAuthorization(syncDestinations.googleCalendarEnabled || OutputTarget.GOOGLE_CALENDAR in currentWriteTargets)
    if (rejection != null) {
        feedback = rejection
        return
    }
    feedback = googleAuthorizationDetail
}

fun CalendarControlState.resolveGoogleAuthorization(outcome: GoogleAuthorizationOutcome) {
    feedback = outputWorkflow.resolveGoogleAuthorization(outcome)
}

/** Nur lesende Verbindungsabsicht; App-Einstieg prüft eigenes Ziel, erstellt oder schreibt nichts. */
fun CalendarControlState.requestGoogleConnectionCheck() {
    outputWorkflow.requestGoogleConnectionCheck()?.let { feedback = it }
}

fun CalendarControlState.resolveGoogleConnectionCheck(token: Long, result: GoogleConnectionCheckResult) {
    if (outputWorkflow.resolveGoogleConnectionCheck(token, result)) feedback = result.userSummary
}

/** Nur strukturiert erkannter Google-401-Pfad; Kalender, Zuordnungen und andere Ziele unverändert. */
fun CalendarControlState.invalidateGoogleAuthorizationAfterUnauthorized(): String {
    val summary = outputWorkflow.invalidateGoogleAuthorization()
    feedback = summary
    return summary
}

/** Nur bereinigtes Token-Cache-Ergebnis anzeigen; keine Token, Konten oder Google-Antworten speichern. */
fun CalendarControlState.resolveRejectedGoogleTokenCacheClear(cleared: Boolean) {
    outputWorkflow.resolveRejectedGoogleTokenCacheClear(cleared)?.let { feedback = it }
}

fun CalendarControlState.consumeGoogleAuthorizationRequest() = outputWorkflow.consumeGoogleAuthorizationRequest()
