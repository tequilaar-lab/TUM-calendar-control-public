/*
 * Aufgabe: Begrenzten HTTPS-Abruf und RFC-5545-Analyse zur lesenden bereinigten Vorschau kombinieren.
 * Ein-/Ausgabe: Expliziter Leseauftrag -> bereinigte Vorschau mit Kursen/Instanzen.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Fixplatz-Ableitung, Quellentitel/URLs in Logs, Kalenderberechtigungen oder lokale/Google-Kalenderänderungen.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.i18n.message

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

data class IcalReadOnlyPreview(
    val sourceEventCount: Int,
    val manageableOccurrenceCount: Int,
    val skippedByReason: Map<IcalSkipReason, Int>,
    val firstManageableStart: Instant?,
    val lastManageableEnd: Instant?,
    /** Standardmäßig nur im Speicher. Nach ausdrücklichem Behalten private Katalogmomentaufnahme für Versionswahl; keine Original-ICS, Adressen, Logs oder Auditdateien. */
    val courses: List<SourceCourse>,
    /** Synchronisierungskandidaten; nach ausdrücklichem Behalten UID, Instanz und Anzeige privat speicherbar, niemals in Logs, Plänen oder Git. */
    val occurrences: List<SourceCalendarOccurrence> = emptyList(),
)

sealed interface IcalReadOnlyImportResult {
    data class Success(val preview: IcalReadOnlyPreview) : IcalReadOnlyImportResult
    data class Failure(val message: String) : IcalReadOnlyImportResult
}

class ReadOnlyIcalImportService(
    private val fetcher: HttpsIcalSourceFetcher = HttpsIcalSourceFetcher(),
    private val parser: IcalSourceParser = IcalSourceParser(),
    private val clock: Clock = Clock.systemUTC(),
) {
    /** Vergangene 120 bis kommende 180 Tage expandieren; unendliche RRULE-Auswertung verhindern. */
    fun preview(url: String): IcalReadOnlyImportResult = preview(url, SourceOutputDateWindow())

    fun preview(url: String, window: SourceOutputDateWindow): IcalReadOnlyImportResult {
        val bounds = sourceReadBounds(window, clock.instant())
            ?: return IcalReadOnlyImportResult.Failure(message(118))
        val text = when (val fetched = fetcher.fetch(url)) {
            is IcalFetchResult.Success -> fetched.text
            is IcalFetchResult.Failure -> return IcalReadOnlyImportResult.Failure(fetched.reason)
        }
        val parsed = runCatching {
            parser.parse(
                icsText = text,
                windowStart = bounds.first,
                windowEnd = bounds.second,
            )
        }.getOrElse { return IcalReadOnlyImportResult.Failure(message(119)) }
        val occurrences = parsed.occurrences
        val courses = SourceCourseCatalog.from(occurrences)
        return IcalReadOnlyImportResult.Success(
            IcalReadOnlyPreview(
                sourceEventCount = occurrences.size + parsed.skipped.size,
                manageableOccurrenceCount = occurrences.size,
                skippedByReason = parsed.skipped.groupingBy { it.reason }.eachCount(),
                firstManageableStart = occurrences.minOfOrNull(SourceCalendarOccurrence::startsAt),
                lastManageableEnd = occurrences.maxOfOrNull(SourceCalendarOccurrence::endsAt),
                courses = courses,
                occurrences = occurrences,
            ),
        )
    }
}

/** Unendliche Wiederholungen begrenzen; explizite Datumswahl bevorzugen, leere Grenze passend zum Standardfenster ergänzen. */
internal fun sourceReadBounds(window: SourceOutputDateWindow, now: Instant): Pair<Instant, Instant>? {
    val zone = java.time.ZoneId.of("Europe/Berlin")
    val start = window.startsOn?.atStartOfDay(zone)?.toInstant()
        ?: window.endsOn?.minusDays(300)?.atStartOfDay(zone)?.toInstant() ?: now.minus(120, ChronoUnit.DAYS)
    val end = window.endsOn?.plusDays(1)?.atStartOfDay(zone)?.toInstant()
        ?: window.startsOn?.plusDays(300)?.atStartOfDay(zone)?.toInstant() ?: now.plus(180, ChronoUnit.DAYS)
    return (start to end).takeIf { end.isAfter(start) && java.time.Duration.between(start, end).toDays() <= 1830 }
}
