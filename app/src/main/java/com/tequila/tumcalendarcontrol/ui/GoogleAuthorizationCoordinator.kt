/*
 * Aufgabe: Kurzlebigen Google-Zugriffstoken für selbst erstellte Kalender über Play services anfordern.
 * Ein-/Ausgabe: Explizite Autorisierung und Ergebnis-Intent -> Sitzungstoken oder bereinigter Fehler.
 * Nebenwirkungen: Google-Systemdialog nur bei authorize; Token bleibt im Prozess, keine Kalenderausgabe.
 * Nicht zuständig: Google-REST-Aufrufe, Kontodatenanzeige, Offline-Erneuerung von Tokens oder Synchronisierung außerhalb der Autorisierung.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import android.app.PendingIntent
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

sealed interface GoogleAuthorizationOutcome {
    data class AccessToken(val value: String) : GoogleAuthorizationOutcome
    data class LaunchConsent(val pendingIntent: PendingIntent) : GoogleAuthorizationOutcome
    data class Failure(val summary: String) : GoogleAuthorizationOutcome
}

class GoogleAuthorizationCoordinator(context: Context) {
    private val client = Identity.getAuthorizationClient(context.applicationContext)

    fun authorize(callback: (GoogleAuthorizationOutcome) -> Unit) {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GOOGLE_CALENDAR_APP_CREATED_SCOPE)))
            .build()
        client.authorize(request)
            .addOnSuccessListener {
                val outcome = it.toOutcome()
                Log.i(GoogleAuthorizationLogTag, "stage=authorization_result outcome=${outcome.logLabel}")
                callback(outcome)
            }
            .addOnFailureListener { error ->
                val summary = failureSummary(message(389), error)
                Log.w(GoogleAuthorizationLogTag, "stage=authorize_failed diagnostic=${failureDiagnostic(error)}")
                callback(GoogleAuthorizationOutcome.Failure(summary))
            }
    }

    fun resolveResult(resultCode: Int, data: Intent?, callback: (GoogleAuthorizationOutcome) -> Unit) {
        if (resultCode != Activity.RESULT_OK) {
            val outcome = GoogleAuthorizationOutcome.Failure(message(390, (resultCode)))
            Log.w(GoogleAuthorizationLogTag, "stage=consent_return_not_ok resultCode=$resultCode")
            callback(outcome)
            return
        }
        if (data == null) {
            val outcome = GoogleAuthorizationOutcome.Failure(message(391, (resultCode)))
            Log.w(GoogleAuthorizationLogTag, "stage=consent_return_empty_data resultCode=$resultCode")
            callback(outcome)
            return
        }
        val outcome = runCatching { client.getAuthorizationResultFromIntent(data).toOutcome() }
            .getOrElse { error ->
                Log.w(GoogleAuthorizationLogTag, "stage=consent_return_failed diagnostic=${failureDiagnostic(error)}")
                GoogleAuthorizationOutcome.Failure(failureSummary(message(392), error))
            }
        Log.i(GoogleAuthorizationLogTag, "stage=consent_return outcome=${outcome.logLabel}")
        callback(outcome)
    }

    /** Play-services-Cache nur nach bestätigtem Calendar-API-401 leeren; Autorisierung nicht widerrufen. */
    fun clearRejectedAccessToken(accessToken: String, onComplete: (Boolean) -> Unit) {
        val request = ClearTokenRequest.builder().setToken(accessToken).build()
        client.clearToken(request).addOnCompleteListener { result ->
            Log.i(GoogleAuthorizationLogTag, "stage=token_cache_cleared success=${result.isSuccessful}")
            onComplete(result.isSuccessful)
        }
    }

    private fun AuthorizationResult.toOutcome(): GoogleAuthorizationOutcome {
        val token = accessToken
        val consent = pendingIntent
        return when {
            !token.isNullOrBlank() -> GoogleAuthorizationOutcome.AccessToken(token)
            consent != null -> GoogleAuthorizationOutcome.LaunchConsent(consent)
            else -> GoogleAuthorizationOutcome.Failure(message(393))
        }
    }

    /** Nur öffentliche Systemstatuscodes anzeigen; keine Ausnahmeinhalte speichern/zeigen. */
    private fun failureSummary(stage: String, error: Throwable): String = when (error) {
        is ApiException -> message(394, (stage), (error.statusCode))
        else -> message(395, (stage), (error.javaClass.simpleName))
    }

    private fun failureDiagnostic(error: Throwable): String = when (error) {
        is ApiException -> "apiStatus=${(error.statusCode)}"
        else -> "exceptionType=${(error.javaClass.simpleName)}"
    }

    private companion object {
        const val GoogleAuthorizationLogTag = "TumGoogleAuth"
        const val GOOGLE_CALENDAR_APP_CREATED_SCOPE = "https://www.googleapis.com/auth/calendar.app.created"
    }
}

/** Nur bereinigte Phasen protokollieren; keine Token, Konten, Kalender oder Ausnahmeinhalte. */
private val GoogleAuthorizationOutcome.logLabel: String
    get() = when (this) {
        is GoogleAuthorizationOutcome.AccessToken -> "access_token_received"
        is GoogleAuthorizationOutcome.LaunchConsent -> "consent_required"
        is GoogleAuthorizationOutcome.Failure -> "failure"
    }
