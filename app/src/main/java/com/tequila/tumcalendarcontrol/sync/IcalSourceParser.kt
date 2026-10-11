/*
 * Aufgabe: RFC-5545-Quelle mit iCal4j analysieren und Wiederholungen im begrenzten Zeitfenster expandieren.
 * Ein-/Ausgabe: iCal-Text, Zeitfenster und optionale UID-Liste -> expandierte Termine und Ausschlussgründe.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Fixplatz-Ableitung, selbst entwickelte ICS-Syntax, Regelspeicherung oder Zielkalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.i18n.message

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Period
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Status
import java.io.StringReader
import java.time.Instant

class IcalSourceParser {
    /** Null-UID-Liste akzeptiert alle Quellkurse; andere Listen nur für alte explizite Filter. windowStart/windowEnd begrenzen unendliche RRULE-Auswertung. */
    fun parse(
        icsText: String,
        windowStart: Instant,
        windowEnd: Instant,
        allowedSourceUids: Set<String>? = null,
    ): IcalImportResult {
        require(windowEnd.isAfter(windowStart)) { message(84) }
        Ical4jRuntimeConfiguration.configureOfflineCache()
        val calendar = CalendarBuilder().build(StringReader(icsText))
        val candidates = mutableListOf<SourceCalendarOccurrence>()
        val skipped = mutableListOf<IcalSkippedEvent>()
        val overrides = mutableMapOf<String, SourceCalendarOccurrence>()

        calendar.components.filterIsInstance<VEvent>().forEach { event ->
            val uid = event.uid?.value
            if (uid.isNullOrBlank()) {
                skipped += IcalSkippedEvent(null, IcalSkipReason.MISSING_UID)
                return@forEach
            }
            if (allowedSourceUids != null && uid !in allowedSourceUids) {
                skipped += IcalSkippedEvent(uid, IcalSkipReason.NOT_CONFIRMED_FIXPLATZ)
                return@forEach
            }
            val recurrenceId = event.recurrenceId?.date?.toInstant()?.toString()
            val periods = event.calculateRecurrenceSet(
                Period(DateTime(windowStart.toEpochMilli()), DateTime(windowEnd.toEpochMilli())),
            )
            if (periods.isEmpty()) {
                skipped += IcalSkippedEvent(uid, IcalSkipReason.MISSING_TIME)
                return@forEach
            }
            periods.forEach { period ->
                val occurrence = period.toOccurrence(event, uid, recurrenceId)
                if (recurrenceId == null) candidates += occurrence else overrides["$uid|$recurrenceId"] = occurrence
            }
        }

        val merged = candidates.associateBy { "${it.sourceUid}|${it.instanceKey}" }.toMutableMap()
        overrides.forEach { (identity, occurrence) -> merged[identity] = occurrence }
        return IcalImportResult(
            occurrences = merged.values.sortedBy(SourceCalendarOccurrence::startsAt),
            skipped = skipped,
        )
    }

    private fun Period.toOccurrence(event: VEvent, uid: String, recurrenceId: String?): SourceCalendarOccurrence {
        val start = start.toInstant()
        val end = end.toInstant()
        return SourceCalendarOccurrence(
            sourceUid = uid,
            instanceKey = recurrenceId ?: start.toString(),
            title = event.summary?.value.orEmpty().ifBlank { message(85) },
            location = event.location?.value.orEmpty(),
            startsAt = start,
            endsAt = end,
            isCancelled = event.status?.value.equals(Status.VEVENT_CANCELLED.value, ignoreCase = true),
        )
    }

}
