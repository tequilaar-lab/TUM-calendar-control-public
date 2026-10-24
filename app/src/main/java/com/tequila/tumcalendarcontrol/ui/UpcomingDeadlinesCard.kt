/*
 * Aufgabe: Karte „Demnächst“ auf der Startseite (bis zu drei Prüfungen/Fristen der nächsten 30 Tage) und gemeinsame
 *   Countdown-Texte für App und Widgets.
 * Ein-/Ausgabe: Eigene Termine, Uhr (austauschbar für Tests) -> Karte; Restzeit -> lokalisierter Text.
 * Nebenwirkungen: Keine; Antippen öffnet nur die Campustermine.
 * Nicht zuständig: Countdown-Berechnung (DeadlineCountdown), Erfassen/Löschen oder Kalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.sync.CountdownEntry
import com.tequila.tumcalendarcontrol.sync.CountdownRemaining
import com.tequila.tumcalendarcontrol.sync.DeadlineCountdown
import com.tequila.tumcalendarcontrol.ui.design.DesignFrostCard
import com.tequila.tumcalendarcontrol.ui.design.DesignSection
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors
import com.tequila.tumcalendarcontrol.ui.design.hapticClickable
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Uhr der Countdown-Anzeigen in der App; Tests setzen feste Werte (reproduzierbare Bilder). */
internal data class CountdownClock(val now: () -> Instant = Instant::now, val zone: () -> ZoneId = { ZoneId.of("Europe/Berlin") })
internal val LocalCountdownClock = staticCompositionLocalOf { CountdownClock() }

private val Horizon = Duration.ofDays(30)

/** Restzeit als Text (App und Widgets gemeinsam). */
internal fun CountdownRemaining.text(): String = when (this) {
    is CountdownRemaining.Days -> message(845, days)
    is CountdownRemaining.Hours -> message(846, hours)
    is CountdownRemaining.Minutes -> message(847, minutes)
    CountdownRemaining.InProgress -> message(848)
}

/** Ohne anstehende Prüfungen/Fristen in 30 Tagen nichts zeigen (keine leere Karte auf der Startseite). */
@Composable
internal fun UpcomingDeadlinesCard(state: CalendarControlState, modifier: Modifier = Modifier) {
    val clock = LocalCountdownClock.current
    val entries = DeadlineCountdown.upcoming(state.ruleLedger.manualEvents, clock.now(), clock.zone(), horizon = Horizon).take(3)
    if (entries.isEmpty()) return
    val open = { state.openManualEvents() }
    DesignFrostCard(modifier.testTag("upcoming-deadlines")) {
        DesignSection(tr(message(849)))
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            entries.forEach { CountdownRow(it, clock.zone(), open) }
        }
    }
}

@Composable
private fun CountdownRow(entry: CountdownEntry, zone: ZoneId, onClick: () -> Unit) {
    val colors = LocalDesignColors.current
    val locale = java.util.Locale.forLanguageTag(LocalAppLanguage.current.language.tag)
    val pattern = tr(message(628))
    Row(
        Modifier.fillMaxWidth().hapticClickable(onClick = onClick).padding(vertical = 8.dp).testTag("upcoming-deadline"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            DesignText(tr(entry.item.title), 15, maxLines = 1)
            DesignText(tr(message(147, entry.item.kind.label(), DateTimeFormatter.ofPattern(pattern, locale).format(entry.target.atZone(zone)))), 12, muted = true)
        }
        DesignText(tr(entry.remaining.text()), 14, Modifier.padding(start = 12.dp), color = colors.accent, weight = FontWeight.Medium)
    }
}
