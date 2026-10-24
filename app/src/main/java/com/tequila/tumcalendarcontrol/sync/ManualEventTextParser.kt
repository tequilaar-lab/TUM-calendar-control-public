/*
 * Aufgabe: Aus geteiltem Text (E-Mail, Moodle, Nachricht) einen Entwurf für einen eigenen Campustermin ableiten: Titel,
 *   Datum, Zeit bzw. Zeitspanne und Art (Prüfung/Frist/Termin). Der Nutzer prüft und speichert selbst.
 * Ein-/Ausgabe: Text, optionaler Betreff, heutiges Datum -> ManualEventDraft (fehlende Teile null).
 * Nebenwirkungen: Keine; reine Berechnung, der Text wird nicht gespeichert.
 * Nicht zuständig: Speichern (CalendarControlState.addManualEvent), Formular oder Empfang des Teilens.
 */
package com.tequila.tumcalendarcontrol.sync

import java.time.LocalDate
import java.time.LocalTime

/** Entwurf; null = im Text nicht gefunden (Formular behält dann seine Vorgaben). */
data class ManualEventDraft(
    val title: String,
    val date: LocalDate?,
    val start: LocalTime?,
    val end: LocalTime?,
    val kind: ManualEventKind,
)

object ManualEventTextParser {
    private const val TITLE_LIMIT = 60
    private val Url = Regex("""https?://\S+""")
    /**
     * Schlüsselwörter je Art: deutsche als Wortanfang (auch Zusammensetzungen wie „Klausurtermin“), englische nur als
     * ganzes Wort (sonst „example“ = „exam“), chinesische als Teilzeichenkette. Adressen werden vorher entfernt.
     */
    private val ExamWords = Keywords(listOf("prüfung", "pruefung", "klausur"), listOf("exam", "exams", "examination", "midterm"), listOf("考试", "测验"))
    private val DeadlineWords = Keywords(listOf("abgabe", "frist", "fällig", "faellig", "einreich"), listOf("due", "deadline", "submission", "submit"), listOf("截止", "提交"))

    private class Keywords(prefixes: List<String>, words: List<String>, private val cjk: List<String>) {
        private val latin = Regex("""(?<![\p{L}])(?:${prefixes.joinToString("|")})|\b(?:${words.joinToString("|")})\b""")
        fun foundIn(text: String): Boolean = latin.containsMatchIn(text) || cjk.any { it in text }
    }
    private val Months = mapOf(
        "jan" to 1, "januar" to 1, "january" to 1, "feb" to 2, "februar" to 2, "february" to 2, "mär" to 3, "mar" to 3, "märz" to 3,
        "maerz" to 3, "march" to 3, "apr" to 4, "april" to 4, "mai" to 5, "may" to 5, "jun" to 6, "juni" to 6, "june" to 6,
        "jul" to 7, "juli" to 7, "july" to 7, "aug" to 8, "august" to 8, "sep" to 9, "sept" to 9, "september" to 9,
        "okt" to 10, "oct" to 10, "oktober" to 10, "october" to 10, "nov" to 11, "november" to 11, "dez" to 12, "dec" to 12,
        "dezember" to 12, "december" to 12,
    )
    private val MonthNames = Months.keys.sortedByDescending { it.length }.joinToString("|")

    /** Datumsformen mit Gruppen (Jahr?, Monat, Tag) je Muster; die früheste Fundstelle im Text gilt. */
    private val DatePatterns: List<Pair<Regex, (MatchResult) -> Triple<Int?, Int, Int>?>> = listOf(
        Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""") to { m -> Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) },
        Regex("""(?:(\d{4})\s*年\s*)?(\d{1,2})\s*月\s*(\d{1,2})\s*[日号]""") to { m -> Triple(m.groupValues[1].toIntOrNull(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) },
        Regex("""\b(\d{1,2})\.(\d{1,2})\.(\d{2,4})?""") to { m -> Triple(m.groupValues[3].toIntOrNull()?.let { if (it < 100) 2000 + it else it }, m.groupValues[2].toInt(), m.groupValues[1].toInt()) },
        Regex("""\b(\d{1,2})\.?\s+($MonthNames)\.?(?:\s+(\d{4}))?""", RegexOption.IGNORE_CASE) to { m ->
            Months[m.groupValues[2].lowercase()]?.let { Triple(m.groupValues[3].toIntOrNull(), it, m.groupValues[1].toInt()) }
        },
        Regex("""\b($MonthNames)\.?\s+(\d{1,2})(?:st|nd|rd|th)?,?(?:\s+(\d{4}))?""", RegexOption.IGNORE_CASE) to { m ->
            Months[m.groupValues[1].lowercase()]?.let { Triple(m.groupValues[3].toIntOrNull(), it, m.groupValues[2].toInt()) }
        },
    )
    private val TimeRange = Regex("""(\d{1,2})[:.](\d{2})\s*(?:uhr)?\s*(?:-|–|—|bis|to|until|至|到)\s*(\d{1,2})[:.](\d{2})""", RegexOption.IGNORE_CASE)
    private val TimeClock = Regex("""\b(\d{1,2})[:.](\d{2})\b""")
    private val TimeAmPm = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)""", RegexOption.IGNORE_CASE)
    private val TimeUhr = Regex("""\b(\d{1,2})\s*uhr\b""", RegexOption.IGNORE_CASE)

    fun parse(text: String, subject: String?, today: LocalDate): ManualEventDraft {
        val body = text.replace('\r', '\n')
        val lower = listOfNotNull(subject, body).joinToString("\n").lowercase().replace(Url, " ")
        val kind = when {
            DeadlineWords.foundIn(lower) -> ManualEventKind.DEADLINE
            ExamWords.foundIn(lower) -> ManualEventKind.EXAM
            else -> ManualEventKind.NORMAL
        }
        // Datum zuerst; seine Stelle wird für die Zeitsuche geleert (sonst würde „02.11“ als Uhrzeit gelesen).
        val dateHit = DatePatterns.mapNotNull { (regex, read) -> regex.find(body)?.let { m -> read(m)?.let { m to it } } }.minByOrNull { it.first.range.first }
        val date = dateHit?.second?.let { (year, month, day) -> resolveDate(year, month, day, today) }
        val rest = dateHit?.first?.let { body.replaceRange(it.range, " ".repeat(it.value.length)) } ?: body
        val (start, end) = times(rest)
        return ManualEventDraft(title(subject, body), date, start, end, kind)
    }

    /** Ohne Jahr: nächstes Vorkommen ab heute; ungültige Daten -> null. */
    private fun resolveDate(year: Int?, month: Int, day: Int, today: LocalDate): LocalDate? = runCatching {
        if (year != null) LocalDate.of(year, month, day)
        else LocalDate.of(today.year, month, day).let { if (it.isBefore(today)) it.plusYears(1) else it }
    }.getOrNull()

    /** Zeitspanne bevorzugt, sonst die erste Uhrzeit (24 h, am/pm oder „14 Uhr“). */
    private fun times(text: String): Pair<LocalTime?, LocalTime?> {
        TimeRange.find(text)?.let { m ->
            val start = time(m.groupValues[1], m.groupValues[2])
            val end = time(m.groupValues[3], m.groupValues[4])
            if (start != null && end != null) return start to end
        }
        val single = TimeClock.find(text)?.let { time(it.groupValues[1], it.groupValues[2]) }
            ?: TimeAmPm.find(text)?.let { m ->
                val hour = m.groupValues[1].toInt() % 12 + if (m.groupValues[3].lowercase().startsWith("p")) 12 else 0
                time(hour.toString(), m.groupValues[2].ifEmpty { "0" })
            }
            ?: TimeUhr.find(text)?.let { time(it.groupValues[1], "0") }
        return single to null
    }

    private fun time(hour: String, minute: String): LocalTime? = runCatching { LocalTime.of(hour.toInt(), minute.toInt()) }.getOrNull()

    /** Betreff, sonst erste nichtleere Zeile ohne Adressen; auf TITLE_LIMIT Zeichen gekürzt. */
    private fun title(subject: String?, body: String): String {
        val raw = subject?.takeIf(String::isNotBlank) ?: body.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty).orEmpty()
        return raw.replace(Url, "").replace(Regex("""\s+"""), " ").trim().take(TITLE_LIMIT).trim()
    }
}
