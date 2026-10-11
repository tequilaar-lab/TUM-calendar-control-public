/*
 * Aufgabe: gespeicherte, ausschließlich app-eigene Ergebniszusammenfassungen wieder lokalisierbar machen.
 * Ein-/Ausgabe: bekannter kanonischer Hinweis -> unveränderter String mit Nachrichtenherkunft.
 * Nebenwirkungen: nur schwache Nachrichtenreferenzen im Prozess.
 * Nicht zuständig: Nutzereingaben, Titel, Orte, Rohdaten oder Änderungen am gespeicherten Format.
 */
package com.tequila.tumcalendarcontrol.i18n

private data class RestorePattern(val key: Int, val regex: Regex, val argumentIndices: List<Int>)
private val marker = Regex("\\{(\\d+)\\}")
private val restorePatterns by lazy {
    messageSources.entries.filter { (_, source) -> source.any { it in '\u3400'..'\u9fff' } }
        .sortedByDescending { (_, source) -> marker.replace(source, "").length }
        .map { (key, source) ->
            var offset = 0
            val indices = mutableListOf<Int>()
            val pattern = buildString {
                append("^")
                marker.findAll(source).forEach { match ->
                    append(Regex.escape(source.substring(offset, match.range.first)))
                    append("(.*?)")
                    indices += match.groupValues[1].toInt()
                    offset = match.range.last + 1
                }
                append(Regex.escape(source.substring(offset)))
                append("$")
            }
            RestorePattern(key, Regex(pattern, RegexOption.DOT_MATCHES_ALL), indices)
        }
}

/** Nur für vertrauenswürdige App-Ergebnisfelder aufrufen, niemals auf beliebige Benutzerdaten. */
fun restoreOwnedMessage(value: String): String = restore(value, 0)

private fun restore(value: String, depth: Int): String {
    if (depth > 8) return value
    for (candidate in restorePatterns) {
        val match = candidate.regex.matchEntire(value) ?: continue
        val arguments = arrayOfNulls<String>(candidate.argumentIndices.maxOrNull()?.plus(1) ?: 0)
        candidate.argumentIndices.forEachIndexed { index, argument -> arguments[argument] = restore(match.groupValues[index + 1], depth + 1) }
        val restored = message(candidate.key, *arguments)
        if (restored == value) return restored
    }
    if (value.contains('；')) return joinMessages(value.split('；').map { restore(it, depth + 1) })
    return value
}
