/*
 * Aufgabe: App-Nachrichten mit Schlüssel und Argumenten bis zur Anzeige verfolgen.
 * Ein-/Ausgabe: Schlüssel/Argumente -> unveränderter kanonischer String; Anzeige -> lokalisierter String.
 * Nebenwirkungen: schwache, synchronisierte Referenzen im Prozess; keine Speicherung oder Netzwerkzugriffe.
 * Nicht zuständig: Übersetzen fremder Texte, Kursdaten, Kalenderfelder oder Ändern fachlicher Regeln.
 */
package com.tequila.tumcalendarcontrol.i18n

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

private data class MessageRecord(val key: Int?, val arguments: List<Any?>, val separator: String = "")
private class IdentityReference(value: String, queue: ReferenceQueue<String>? = null) : WeakReference<String>(value, queue) {
    private val identityHash = System.identityHashCode(value)
    override fun hashCode(): Int = identityHash
    override fun equals(other: Any?): Boolean = this === other ||
        (other is IdentityReference && get() != null && get() === other.get())
}
private val queue = ReferenceQueue<String>()
private val records = mutableMapOf<IdentityReference, MessageRecord>()
private val placeholder = Regex("\\{(\\d+)\\}")

/** Behält String-Verträge bestehender Modelle; nur diese konkreten Objekte sind übersetzbar. */
fun message(key: Int, vararg arguments: Any?): String {
    val source = messageSources.getValue(key)
    val value = formatMessage(source, arguments.toList()) { it.toString() }
    // Separate Identität verhindert, dass gleichlautende Benutzereingaben übersetzt werden.
    val owned = String(value.toCharArray())
    synchronized(records) {
        while (true) {
            val stale = queue.poll() ?: break
            records.remove(stale)
        }
        records[IdentityReference(owned, queue)] = MessageRecord(key, arguments.toList())
    }
    return owned
}

/** Rekursive Argumente erhalten ihre Herkunft; beliebige Kursnamen bleiben unverändert. */
fun renderMessage(value: String, template: (Int) -> String): String {
    val record = synchronized(records) { records[IdentityReference(value)] } ?: return value
    val renderArgument: (Any?) -> String = { argument ->
        if (argument is String) renderMessage(argument, template) else argument.toString()
    }
    return if (record.key == null) record.arguments.joinToString(renderMessage(record.separator, template), transform = renderArgument)
    else formatMessage(template(record.key), record.arguments, renderArgument)
}

private fun formatMessage(source: String, arguments: List<Any?>, render: (Any?) -> String): String =
    placeholder.replace(source) { match -> render(arguments.getOrNull(match.groupValues[1].toInt())) }

/** Verknüpft Anzeigehinweise, ohne ihre Herkunft oder den kanonischen Wert zu verlieren. */
fun joinMessages(parts: Iterable<String>, separator: String = "；"): String {
    val values = parts.toList()
    val owned = String(values.joinToString(separator).toCharArray())
    val localizedSeparator = when (separator) {
        "；" -> message(677)
        "、" -> message(678)
        else -> separator
    }
    synchronized(records) { records[IdentityReference(owned, queue)] = MessageRecord(null, values, localizedSeparator) }
    return owned
}

fun concatenateMessages(vararg parts: String): String = joinMessages(parts.toList(), "")
