/*
 * Aufgabe: Ausgabevorschau, Google-Autorisierung und Ausführungs-/Rücknahmeanfragen im Prozess halten.
 * Ein-/Ausgabe: Bestätigungen und Adapterrückrufe -> Sitzungstoken, Anfrageflags und Ausgabekandidaten.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Synchronisierungskandidaten, RuleLedger-Zugriffe, iCal-Parsing, Systemberechtigungen oder Gateways.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tequila.tumcalendarcontrol.sync.DesiredCalendarEvent
import com.tequila.tumcalendarcontrol.sync.ManagedSyncPreview
import com.tequila.tumcalendarcontrol.sync.GoogleConnectionCheckResult

enum class GoogleConnectionVerification {
    NOT_CHECKED, CHECKING, VERIFIED, ACTION_REQUIRED, FAILED,
}

/** Aufrufer prüft Voraussetzungen; Zustandsmaschine verwaltet nur laufende Anfrage und Token. */
internal class ManagedOutputWorkflowState {
    var preview by mutableStateOf<ManagedSyncPreview?>(null)
    var desiredEvents by mutableStateOf<List<DesiredCalendarEvent>>(emptyList())
    var syncRequested by mutableStateOf(false)
        private set
    var rollbackRequested by mutableStateOf(false)
        private set
    var inProgress by mutableStateOf(false)
        private set
    var googleAuthorizationRequested by mutableStateOf(false)
        private set
    var googleConnectionCheckRequested by mutableStateOf(false)
        private set
    var googleConnectionCheckInProgress by mutableStateOf(false)
        private set
    /** Lesende Prüfung mit eigener Nummer; nach Trennung/Neuauthorisierung alte Rückrufe ignorieren. */
    var googleConnectionCheckToken by mutableStateOf(0L)
        private set
    var googleConnectionCheckDetail by mutableStateOf(message(403))
        private set
    var googleConnectionVerification by mutableStateOf(GoogleConnectionVerification.NOT_CHECKED)
        private set
    private var googleAccessToken by mutableStateOf<String?>(null)
    var googleAuthorizationStatus by mutableStateOf(GoogleAuthorizationStatus.NOT_CONNECTED)
        private set
    var googleAuthorizationDetail by mutableStateOf(GoogleAuthorizationStatus.NOT_CONNECTED.defaultDetail)
        private set
    var localCalendarPermissionGranted by mutableStateOf(false)
    var operationToken by mutableStateOf(0L)
        private set

    val hasGoogleAuthorization: Boolean get() = googleAccessToken != null
    fun currentGoogleAccessToken(): String? = googleAccessToken

    /** Nur OAuth-Seitenabsicht; Token erst nach Adapterrückruf in aktuellen Prozess übernehmen. */
    fun requestGoogleAuthorization(enabled: Boolean): String? {
        if (!enabled) {
            googleAuthorizationStatus = GoogleAuthorizationStatus.FAILED
            googleAuthorizationDetail = message(404)
            return googleAuthorizationDetail
        }
        googleAccessToken = null
        googleConnectionVerification = GoogleConnectionVerification.NOT_CHECKED
        googleAuthorizationRequested = true
        googleAuthorizationStatus = GoogleAuthorizationStatus.AUTHORIZING
        googleAuthorizationDetail = GoogleAuthorizationStatus.AUTHORIZING.defaultDetail
        return null
    }

    fun resolveGoogleAuthorization(outcome: GoogleAuthorizationOutcome): String {
        googleAuthorizationRequested = false
        when (outcome) {
            is GoogleAuthorizationOutcome.AccessToken -> {
                googleAccessToken = outcome.value
                googleAuthorizationStatus = GoogleAuthorizationStatus.AUTHORIZED
                googleAuthorizationDetail = GoogleAuthorizationStatus.AUTHORIZED.defaultDetail
                googleConnectionVerification = GoogleConnectionVerification.NOT_CHECKED
            }
            is GoogleAuthorizationOutcome.Failure -> {
                googleAccessToken = null
                googleConnectionVerification = GoogleConnectionVerification.NOT_CHECKED
                googleAuthorizationStatus = GoogleAuthorizationStatus.FAILED
                googleAuthorizationDetail = outcome.summary
            }
            is GoogleAuthorizationOutcome.LaunchConsent -> {
                googleAuthorizationStatus = GoogleAuthorizationStatus.AUTHORIZING
                googleAuthorizationDetail = GoogleAuthorizationStatus.AUTHORIZING.defaultDetail
            }
        }
        return googleAuthorizationDetail
    }

    fun disconnectGoogle() {
        googleAuthorizationRequested = false
        googleAccessToken = null
        googleAuthorizationStatus = GoogleAuthorizationStatus.NOT_CONNECTED
        googleAuthorizationDetail = GoogleAuthorizationStatus.NOT_CONNECTED.defaultDetail
        cancelGoogleConnectionCheck()
    }

    /** Nur mit Sitzungstoken erlauben; tatsächliche Netzwerkprüfung am App-Einstieg koordinieren. */
    fun requestGoogleConnectionCheck(): String? {
        if (googleAccessToken.isNullOrBlank()) {
            googleConnectionCheckDetail = message(405)
            return googleConnectionCheckDetail
        }
        if (googleConnectionCheckInProgress) return null
        googleConnectionCheckToken += 1
        googleConnectionCheckRequested = true
        googleConnectionCheckInProgress = true
        googleConnectionCheckDetail = message(406)
        googleConnectionVerification = GoogleConnectionVerification.CHECKING
        return null
    }

    fun resolveGoogleConnectionCheck(token: Long, result: GoogleConnectionCheckResult): Boolean {
        if (token != googleConnectionCheckToken || !googleConnectionCheckInProgress) return false
        googleConnectionCheckRequested = false
        googleConnectionCheckInProgress = false
        googleConnectionCheckDetail = result.userSummary
        googleConnectionVerification = when (result) {
            GoogleConnectionCheckResult.Available -> GoogleConnectionVerification.VERIFIED
            GoogleConnectionCheckResult.NoManagedCalendar,
            GoogleConnectionCheckResult.Missing,
            GoogleConnectionCheckResult.OwnershipUnverified,
            -> GoogleConnectionVerification.ACTION_REQUIRED
            is GoogleConnectionCheckResult.Failed -> GoogleConnectionVerification.FAILED
        }
        return true
    }

    /** HTTP 401 beweist ungültigen Token; Speicherreferenz entfernen und ausdrückliche Neuverbindung verlangen. */
    fun invalidateGoogleAuthorization(): String {
        googleAuthorizationRequested = false
        googleAccessToken = null
        googleAuthorizationStatus = GoogleAuthorizationStatus.FAILED
        googleAuthorizationDetail = message(407)
        googleConnectionVerification = GoogleConnectionVerification.FAILED
        cancelGoogleConnectionCheck()
        return googleAuthorizationDetail
    }

    private fun cancelGoogleConnectionCheck() {
        googleConnectionCheckToken += 1
        googleConnectionCheckRequested = false
        googleConnectionCheckInProgress = false
        googleConnectionCheckDetail = message(403)
        googleConnectionVerification = GoogleConnectionVerification.NOT_CHECKED
    }

    /** Cache-Ergebnis nur derselben abgelaufenen Autorisierung zuordnen; neue Autorisierung nicht überschreiben. */
    fun resolveRejectedGoogleTokenCacheClear(cleared: Boolean): String? {
        if (googleAuthorizationStatus != GoogleAuthorizationStatus.FAILED || googleAccessToken != null) return null
        googleAuthorizationDetail = if (cleared) {
            message(408)
        } else {
            message(409)
        }
        return googleAuthorizationDetail
    }

    fun consumeGoogleAuthorizationRequest() { googleAuthorizationRequested = false }

    fun beginSync(): Long? {
        if (inProgress) return null
        operationToken += 1
        inProgress = true
        syncRequested = true
        rollbackRequested = false
        return operationToken
    }

    fun beginRollback(): Long? {
        if (inProgress) return null
        operationToken += 1
        inProgress = true
        syncRequested = false
        rollbackRequested = true
        return operationToken
    }

    /** Alter Hintergrundauftrag beendet Zustand nur bei gleichem Token; neue Aktion schützen. */
    fun finishOperation(token: Long): Boolean {
        if (token != operationToken || !inProgress) return false
        syncRequested = false
        rollbackRequested = false
        inProgress = false
        return true
    }
}
