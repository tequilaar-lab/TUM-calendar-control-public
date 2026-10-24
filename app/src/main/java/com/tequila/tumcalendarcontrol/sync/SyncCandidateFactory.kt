/*
 * Aufgabe: Quelltermine, eigene Termine und Dreizustandsregeln zu deterministischen Zielkandidaten verbinden.
 * Ein-/Ausgabe: Ausgewählte Quellinstanzen, eigene Termine und Regeln -> deterministische Zielkandidaten.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: iCal-Lesen, Quellenpersistenz, Kalendererstellung, Differenzausführung oder Identitätsableitung anhand von Titeln.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.i18n.message

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.ZoneId

class SyncCandidateFactory(private val zoneId: ZoneId = ZoneId.of("Europe/Berlin")) {
    /** Nur ausdrücklich gewählte aktive Termine als Kandidaten; ohne Regel blau, abgesagte Termine ausgeschlossen. Lesegrenze nur bei Momentaufnahme, keine zweite Ausgabefilterung. Identität immer UID plus Originalinstanz. */
    fun build(
        occurrences: List<SourceCalendarOccurrence>,
        selectedSourceOccurrences: Set<SourceOccurrenceIdentity>,
        rules: List<SyncRule>,
        manualEvents: List<ManualScheduleItem>,
        overrides: Map<String, com.tequila.tumcalendarcontrol.domain.CourseLocalOverride> = emptyMap(),
        /** Erinnerungen: null = nicht verwaltet; sonst grüne Termine mit diesem Wert (N oder NO_REMINDER), übrige NO_REMINDER. */
        mustAttendReminder: Int? = null,
    ): List<DesiredCalendarEvent> {
        fun reminderFor(state: AttendanceState): Int? = mustAttendReminder?.let { if (state == AttendanceState.MUST_ATTEND) it else NO_REMINDER }
        val rulesByUid = rules.associateBy(SyncRule::sourceUid)
        val sourceEvents = occurrences.asSequence()
            .filter { it.identity in selectedSourceOccurrences }
            .mapNotNull { occurrence ->
            val rule = rulesByUid[occurrence.sourceUid]
                ?: SyncRule(occurrence.sourceUid, hasConfirmedFixplatz = true, defaultState = AttendanceState.CHOOSE_TO_ATTEND)
            SyncRuleResolver.resolve(rule, occurrence, zoneId)?.let { state ->
                val override = overrides[occurrence.sourceUid]
                val meeting = override?.meetingTime
                val originalDay = occurrence.startsAt.atZone(zoneId).toLocalDate()
                val newDay = meeting?.let { originalDay.plusDays((it.weekday.ordinal + 1 - originalDay.dayOfWeek.value).toLong()) }
                val start = meeting?.let { newDay!!.atTime(it.startHour, it.startMinute).atZone(zoneId).toInstant() } ?: occurrence.startsAt
                val end = meeting?.let { newDay!!.atTime(it.endHour, it.endMinute).atZone(zoneId).toInstant() } ?: occurrence.endsAt
                DesiredCalendarEvent(
                    identity = ManagedEventIdentity(occurrence.sourceUid, occurrence.instanceKey),
                    title = override?.title ?: occurrence.title,
                    startsAt = start,
                    endsAt = end,
                    attendanceState = state,
                    location = override?.location ?: occurrence.location,
                    reminderMinutes = reminderFor(state),
                )
            }
        }.toList()
        val manual = manualEvents.map { item ->
            DesiredCalendarEvent(
                identity = ManagedEventIdentity("manual:${(item.id)}", item.id),
                title = item.title,
                startsAt = item.startsAt,
                endsAt = item.endsAt,
                attendanceState = item.attendanceState,
                reminderMinutes = reminderFor(item.attendanceState),
            )
        }
        // Doppelt expandierte iCal-Instanzen nur über stabile Quellenidentität zusammenfassen.
        return (sourceEvents + manual)
            .distinctBy(DesiredCalendarEvent::identity)
            .sortedWith(compareBy<DesiredCalendarEvent> { it.startsAt }.thenBy { it.identity.sourceUid }.thenBy { it.identity.instanceKey })
    }
}
