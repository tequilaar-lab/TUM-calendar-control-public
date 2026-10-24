/*
 * Aufgabe: Kurze Aufstellung der Quelländerungen je Kurs für die Benachrichtigung der Hintergrundprüfung
 *   („Analysis 1：时间已变 · 地点已变“), wenige Kurse zuerst nach frühestem Termin, Rest gezählt.
 * Ein-/Ausgabe: SourceChangeSummary -> Textzeilen (Nachrichtenvorlagen, in der App-Sprache aufzulösen).
 * Nebenwirkungen: Keine; reine Formatierung. Titel dienen nur der Anzeige, nie der Identität.
 * Nicht zuständig: Erkennen der Änderungen (SourceChangeSummary) oder Senden der Benachrichtigung.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.sync.SourceChangeSummary

/** Höchstens maxCourses Zeilen „Kurs：Arten“, danach „另有 N 门课“; leer ohne Änderungen. */
internal fun SourceChangeSummary.courseDigest(maxCourses: Int = 3): List<String> {
    val byCourse = changes.groupBy { it.current.title.trim() }.entries.sortedBy { (_, list) -> list.minOf { it.current.startsAt } }
    val lines = byCourse.take(maxCourses).map { (title, list) -> message(940, title, list.flatMap { it.kinds }.toSet().badgeText()) }
    return if (byCourse.size > maxCourses) lines + message(941, byCourse.size - maxCourses) else lines
}
