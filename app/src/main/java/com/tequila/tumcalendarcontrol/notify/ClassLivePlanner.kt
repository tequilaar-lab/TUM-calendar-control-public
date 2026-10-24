/*
 * Aufgabe: Bestimmen, ob gerade eine Live-Kursbenachrichtigung zu zeigen ist (bevorstehend oder laufend) und wann erneut
 *   geprüft werden soll; ungenaue Wecker sind eingeplant (Hinweis eher früher, Phasenwechsel eher später).
 * Ein-/Ausgabe: geschriebene Termine, Einstellungen, Zeitpunkt -> LivePlan (aktueller Zustand, nächste Prüfung).
 * Nebenwirkungen: Keine; reine Berechnung.
 * Nicht zuständig: Laden der Termine, Benachrichtigung oder Wecker (ClassLiveScheduler).
 */
package com.tequila.tumcalendarcontrol.notify

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.widget.WidgetEvent
import java.time.Duration
import java.time.Instant

enum class LivePhase { UPCOMING, IN_PROGRESS }

data class LiveState(val event: WidgetEvent, val phase: LivePhase)

/**
 * current = was jetzt angezeigt wird (null = nichts). nextCheck = nächster Zeitpunkt, zu dem sich das ändert; early = der
 * Wecker darf bis zu EARLY_TOLERANCE früher kommen (Hinweisbeginn), sonst darf er später kommen (Beginn/Ende).
 */
data class LivePlan(val current: LiveState?, val nextCheck: Instant?, val early: Boolean)

object ClassLivePlanner {
    /** Ungenaue Wecker liefern innerhalb eines Fensters von bis zu 10 Minuten; so weit darf der Hinweis früher kommen. */
    val EARLY_TOLERANCE: Duration = Duration.ofMinutes(10)

    fun plan(events: List<WidgetEvent>, settings: ClassLiveSettings, now: Instant): LivePlan {
        if (!settings.enabled) return LivePlan(null, null, early = false)
        val lead = Duration.ofMinutes(settings.leadMinutes.toLong())
        val eligible = events
            .filter { it.state == AttendanceState.MUST_ATTEND || (settings.includeChooseToAttend && it.state == AttendanceState.CHOOSE_TO_ATTEND) }
            .sortedBy(WidgetEvent::startsAt)
        val running = eligible.firstOrNull { it.startsAt <= now && now < it.endsAt }
        // Bevorstehend: innerhalb von Vorlauf plus Toleranz (ein früh ausgelöster Wecker zeigt den Hinweis schon).
        val upcoming = eligible.firstOrNull { it.startsAt > now && !it.startsAt.minus(lead).minus(EARLY_TOLERANCE).isAfter(now) }
        val current = running?.let { LiveState(it, LivePhase.IN_PROGRESS) } ?: upcoming?.let { LiveState(it, LivePhase.UPCOMING) }
        val phaseChange = when (current?.phase) {
            LivePhase.UPCOMING -> current.event.startsAt
            LivePhase.IN_PROGRESS -> current.event.endsAt
            null -> null
        }
        val nextNotice = eligible.firstOrNull { it.startsAt.minus(lead).minus(EARLY_TOLERANCE).isAfter(now) }?.startsAt?.minus(lead)
        return when {
            phaseChange != null && (nextNotice == null || !nextNotice.isBefore(phaseChange)) -> LivePlan(current, phaseChange, early = false)
            nextNotice != null -> LivePlan(current, nextNotice, early = true)
            else -> LivePlan(current, phaseChange, early = false)
        }
    }
}
