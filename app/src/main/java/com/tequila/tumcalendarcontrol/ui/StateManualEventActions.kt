/*
 * Aufgabe: Aktionen des CalendarControlState für eigene Campustermine: anlegen (mit Art und Frist-Regel) und entfernen
 *   (aus CalendarControlState ausgelagert, Verhalten unverändert).
 * Ein-/Ausgabe: Formularwerte bzw. Termin-ID -> Regelbestand, Rückmeldung; true = gespeichert.
 * Nebenwirkungen: Nur über commitUserData (private Präferenzen).
 * Nicht zuständig: Darstellung der Termine, Kalenderausgabe oder Widgets.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.sync.ManualEventKind
import com.tequila.tumcalendarcontrol.sync.ManualScheduleEditor
import com.tequila.tumcalendarcontrol.sync.ManualScheduleItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Eigene generierte Termin-ID; Europe/Berlin-Zeiten. Eingabefehler verändern keinen Bestand; true = gespeichert.
 * Frist: Ende = Fälligkeit, Beginn 30 Minuten davor (Start-Felder werden ignoriert).
 */
fun CalendarControlState.addManualEvent(
    title: String, date: String, startHour: String, startMinute: String, endHour: String, endMinute: String, attendance: AttendanceState,
    kind: ManualEventKind = ManualEventKind.NORMAL,
): Boolean {
    val item = runCatching {
        val day = LocalDate.parse(date)
        val end = LocalDateTime.of(day, java.time.LocalTime.of(endHour.toInt(), endMinute.toInt())).atZone(ZoneId.of("Europe/Berlin")).toInstant()
        val start = if (kind == ManualEventKind.DEADLINE) end.minus(java.time.Duration.ofMinutes(30))
        else LocalDateTime.of(day, java.time.LocalTime.of(startHour.toInt(), startMinute.toInt())).atZone(ZoneId.of("Europe/Berlin")).toInstant()
        require(end.isAfter(start))
        ManualScheduleItem(UUID.randomUUID().toString(), title.trim().ifBlank { error(message(315)) }, start, end, attendance, kind)
    }.getOrElse { feedback = message(316); return false }
    val nextLedger = ruleLedger.copy(manualEvents = ManualScheduleEditor().upsert(ruleLedger.manualEvents, item))
    if (!commitUserData(nextLedger)) return false
    invalidateWritePreview()
    feedback = message(317)
    return true
}

fun CalendarControlState.removeManualEvent(itemId: String) {
    val nextLedger = ruleLedger.copy(manualEvents = ManualScheduleEditor().remove(ruleLedger.manualEvents, itemId))
    if (!commitUserData(nextLedger)) return
    invalidateWritePreview()
    feedback = message(318)
}
