/*
 * Aufgabe: Vorschläge für Kurznamen aus einem Kurstitel ableiten (Initialen mit Nummer, erstes Wort mit Nummer,
 *   gekürzter Titel ohne Klammerzusätze und Veranstaltungsart), damit nicht jeder Kurzname getippt werden muss.
 * Ein-/Ausgabe: Kurstitel -> höchstens drei verschiedene Vorschläge (je höchstens CourseShortNames.MAX_LENGTH Zeichen).
 * Nebenwirkungen: Keine; reine Berechnung, nichts wird gespeichert.
 * Nicht zuständig: Speichern (CourseShortNames) oder Bedienoberfläche.
 */
package com.tequila.tumcalendarcontrol.widget

object ShortNameSuggester {
    /** Veranstaltungsarten, die in Kurznamen nichts unterscheiden (nur entfernt, wenn andere Wörter bleiben). */
    private val KindWords = setOf("übung", "uebung", "vorlesung", "tutorium", "tutorübung", "zentralübung", "seminar", "exercise", "lecture", "tutorial", "vo", "ue", "vl")
    /** Füllwörter, die nicht in Initialen eingehen. */
    private val StopWords = setOf("und", "der", "die", "das", "des", "dem", "den", "für", "fuer", "in", "im", "mit", "zu", "zur", "von", "an", "auf", "and", "of", "the", "for", "to", "with", "a")
    private val Number = Regex("""\d+[a-z]?|[IVX]+""")

    fun suggest(title: String, max: Int = 3): List<String> {
        val cleaned = title.replace(Regex("""\[[^]]*]|\([^)]*\)"""), " ").replace(Regex("""[,:;/|–—]|\s-\s"""), " ").trim()
        val words = cleaned.split(Regex("""\s+""")).filter(String::isNotBlank)
        if (words.isEmpty()) return emptyList()
        val core = words.filterNot { it.lowercase() in KindWords }.ifEmpty { words }
        val numbers = core.filter { Number.matches(it) }
        val significant = core.filter { it.lowercase() !in StopWords && it !in numbers }
        val suffix = numbers.joinToString(" ").let { if (it.isEmpty()) "" else " $it" }
        val candidates = buildList {
            if (significant.size >= 2) add(significant.joinToString("") { it.first().uppercase() } + numbers.joinToString(""))
            significant.firstOrNull()?.let { add(shorten(it) + suffix) }
            add(fit(core.joinToString(" ")))
        }
        return candidates.map { it.trim() }.filter { it.isNotEmpty() && it != title.trim() && it.length <= CourseShortNames.MAX_LENGTH }.distinct().take(max)
    }

    /** Einzelwort, das nicht in einen Kurznamen passt, auf sechs Zeichen mit Punkt kürzen. */
    private fun shorten(word: String): String = if (word.length <= CourseShortNames.MAX_LENGTH) word else word.take(6) + "."

    /** Auf ganze Wörter bis zur Höchstlänge kürzen. */
    private fun fit(text: String): String {
        if (text.length <= CourseShortNames.MAX_LENGTH) return text
        val out = StringBuilder()
        for (word in text.split(' ')) {
            if (out.length + word.length + (if (out.isEmpty()) 0 else 1) > CourseShortNames.MAX_LENGTH) break
            if (out.isNotEmpty()) out.append(' ')
            out.append(word)
        }
        return out.toString().ifEmpty { text.take(CourseShortNames.MAX_LENGTH) }
    }
}
