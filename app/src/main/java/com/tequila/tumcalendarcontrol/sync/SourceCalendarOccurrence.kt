/*
 * Aufgabe: Expandierten iCalendar-Quelltermin mit stabiler Identität darstellen.
 * Ein-/Ausgabe: Expandierte Quellfelder -> stabiler Quelltermin für Auswahl und Kandidaten.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Fixplatz-Entscheidungen, Teilnahmeregeln, Persistenz oder Zielkalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.i18n.message

import java.time.Instant

/** Aufgabe: stabile nicht erratbare Auswahlidentität. Ein-/Ausgabe: Quell-UID/Originalinstanz -> Auswahl-, Filter- und Regelobjekt. Keine Nebenwirkungen/Titel/Orte/Konten; keine Zusammenführung, Analyse, Regelablage oder Kalenderausgabe. */
data class SourceOccurrenceIdentity(
    val sourceUid: String,
    val instanceKey: String,
)

data class SourceCalendarOccurrence(
    val sourceUid: String,
    val instanceKey: String,
    val title: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val isCancelled: Boolean,
    val location: String = "",
) {
    /** Auswahl und Synchronisierung verwenden UID und Originalinstanz gemeinsam, damit andere Wiederholungen nicht versehentlich ausgewählt werden. */
    val identity: SourceOccurrenceIdentity get() = SourceOccurrenceIdentity(sourceUid, instanceKey)
}

data class IcalImportResult(
    val occurrences: List<SourceCalendarOccurrence>,
    val skipped: List<IcalSkippedEvent>,
)

data class IcalSkippedEvent(
    val sourceUid: String?,
    val reason: IcalSkipReason,
)

enum class IcalSkipReason(val label: String) {
    MISSING_UID(message(122)),
    MISSING_TIME(message(123)),
    NOT_CONFIRMED_FIXPLATZ(message(124)),
}
