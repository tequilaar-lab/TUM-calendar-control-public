/*
 * Aufgabe: Private Quellen-/Google-Konfiguration darstellen und ohne Netzwerk formatprüfen.
 * Ein-/Ausgabe: Benutzerkonfiguration -> validierte Werte und reine Formatprobleme.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Geheimnisspeicherung, OAuth, Abonnementabruf oder Kalendererstellung.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.i18n.message

import java.net.URI

data class PrivateSyncConfiguration(
    val tumSubscriptionUrl: String = "",
    val sourceRangeStartDate: String = "",
    val sourceRangeEndDate: String = "",
    val googleOAuthClientId: String = "",
    val googleCalendarName: String = DEFAULT_GOOGLE_CALENDAR_NAME,
    /** Nur Kennungen selbst erstellter Ziele speichern; keine Kontonamen, Token oder Kalenderinhalte. */
    val localCalendarId: String = "",
    val googleCalendarId: String = "",
) {
    val hasTumSubscription: Boolean get() = tumSubscriptionUrl.isNotBlank()
    val hasGoogleClient: Boolean get() = googleOAuthClientId.isNotBlank()
    val sourceOutputWindow: SourceOutputDateWindow?
        get() = SourceOutputDateWindow.parseOrNull(sourceRangeStartDate, sourceRangeEndDate)

    /** Konfiguration zeigt keine Adressbestandteile; auch Hostname ist private Information. */
    fun maskedTumSubscription(): String = if (hasTumSubscription) message(110) else message(111)

    fun validate(): List<ConfigurationProblem> = buildList {
        if (hasTumSubscription) {
            val uri = runCatching { URI(tumSubscriptionUrl.trim()) }.getOrNull()
            if (uri?.scheme != "https" || uri.host.isNullOrBlank()) {
                add(ConfigurationProblem.INVALID_TUM_SUBSCRIPTION)
            }
        }
        if (hasGoogleClient && !googleOAuthClientId.trim().endsWith(".apps.googleusercontent.com")) {
            add(ConfigurationProblem.INVALID_GOOGLE_CLIENT_ID)
        }
        if (sourceOutputWindow == null) add(ConfigurationProblem.INVALID_SOURCE_OUTPUT_RANGE)
        if (googleCalendarName.trim().isBlank()) add(ConfigurationProblem.BLANK_GOOGLE_CALENDAR_NAME)
    }

    companion object {
        const val DEFAULT_GOOGLE_CALENDAR_NAME = "TUM 日历控制"
    }
}

enum class ConfigurationProblem(val message: String) {
    INVALID_TUM_SUBSCRIPTION(message(112)),
    INVALID_GOOGLE_CLIENT_ID(message(113)),
    INVALID_SOURCE_OUTPUT_RANGE(message(114)),
    BLANK_GOOGLE_CALENDAR_NAME(message(115)),
}
