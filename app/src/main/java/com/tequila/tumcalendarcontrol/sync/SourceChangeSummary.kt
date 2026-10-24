/*
 * Aufgabe: Ausführliche Änderungen zwischen zwei Quelllesungen je Termin ermitteln (neu, entfallen, abgesagt,
 *   wieder angesetzt, Zeit, Ort, Titel) – für Änderungskarte, Unterschiedsseite und Hinweistext.
 * Ein-/Ausgabe: Termine der früheren und der neuen Lesung -> nach Beginn geordnete Änderungen mit Vorher/Nachher.
 * Nebenwirkungen: Keine; reine Berechnung ohne Speicherung oder Netzwerk.
 * Nicht zuständig: Kurzzusammenfassung der Datenversionen (SourceSnapshotDifferenceModel), Auswahl, Regeln oder Ausgabe.
 */
package com.tequila.tumcalendarcontrol.sync

/** Art einer Terminänderung; ein Termin kann mehrere Arten zugleich tragen (z. B. Zeit und Ort). */
enum class SourceChangeKind { ADDED, REMOVED, CANCELLED, RESTORED, TIME, LOCATION, TITLE }

/** Eine Änderung je Identität; before fehlt bei neuen, after bei entfallenen Terminen. */
data class SourceOccurrenceChange(
    val identity: SourceOccurrenceIdentity,
    val kinds: Set<SourceChangeKind>,
    val before: SourceCalendarOccurrence?,
    val after: SourceCalendarOccurrence?,
) {
    /** Darstellungsgrundlage: neuer Stand, bei entfallenen Terminen der letzte bekannte. */
    val current: SourceCalendarOccurrence get() = checkNotNull(after ?: before)
}

data class SourceChangeSummary(val changes: List<SourceOccurrenceChange>) {
    val isEmpty: Boolean get() = changes.isEmpty()
    private val byIdentity: Map<SourceOccurrenceIdentity, Set<SourceChangeKind>> by lazy { changes.associate { it.identity to it.kinds } }

    fun count(kind: SourceChangeKind): Int = changes.count { kind in it.kinds }

    /** Änderungsarten eines Termins des neuen Stands (leer = unverändert). */
    fun kindsOf(identity: SourceOccurrenceIdentity): Set<SourceChangeKind> = byIdentity[identity].orEmpty()

    /** Änderungen an bereits gewählten Terminen (inklusive entfallener), damit die Auswahl geprüft werden kann. */
    fun affecting(selection: Set<SourceOccurrenceIdentity>): Int = changes.count { it.identity in selection }

    companion object {
        val EMPTY = SourceChangeSummary(emptyList())

        /**
         * Vergleich ausschließlich über Quell-UID und Originalinstanz – nie über Titel oder Ort.
         * restrictToCommonSpan: Neue/entfallene Termine nur im gemeinsamen Zeitraum beider Lesungen zählen, wenn die
         * Lesungen unterschiedliche Zeiträume abdecken (Hintergrundprüfung gegen die gespeicherte, begrenzte Lesung).
         * Termine, die in beiden Lesungen vorkommen, werden immer verglichen.
         */
        fun between(
            previous: List<SourceCalendarOccurrence>,
            latest: List<SourceCalendarOccurrence>,
            restrictToCommonSpan: Boolean = false,
        ): SourceChangeSummary {
            val before = previous.associateBy { it.identity }
            val after = latest.associateBy { it.identity }
            val span = commonSpan(previous, latest)
            // Ohne gemeinsamen Zeitraum lässt sich bei Einschränkung nichts als neu/entfallen werten.
            fun inSpan(occurrence: SourceCalendarOccurrence) = !restrictToCommonSpan || (span != null && occurrence.startsAt in span)
            val changes = mutableListOf<SourceOccurrenceChange>()
            after.forEach { (identity, now) ->
                val old = before[identity]
                when {
                    old == null -> if (inSpan(now)) changes += SourceOccurrenceChange(identity, setOf(SourceChangeKind.ADDED), null, now)
                    else -> compare(old, now).takeIf(Set<SourceChangeKind>::isNotEmpty)?.let { changes += SourceOccurrenceChange(identity, it, old, now) }
                }
            }
            before.forEach { (identity, old) ->
                if (identity !in after && inSpan(old)) changes += SourceOccurrenceChange(identity, setOf(SourceChangeKind.REMOVED), old, null)
            }
            return SourceChangeSummary(changes.sortedWith(compareBy({ it.current.startsAt }, { it.current.sourceUid }, { it.current.instanceKey })))
        }

        private fun compare(old: SourceCalendarOccurrence, now: SourceCalendarOccurrence): Set<SourceChangeKind> = buildSet {
            if (!old.isCancelled && now.isCancelled) add(SourceChangeKind.CANCELLED)
            if (old.isCancelled && !now.isCancelled) add(SourceChangeKind.RESTORED)
            if (old.startsAt != now.startsAt || old.endsAt != now.endsAt) add(SourceChangeKind.TIME)
            if (old.location.trim() != now.location.trim()) add(SourceChangeKind.LOCATION)
            if (old.title.trim() != now.title.trim()) add(SourceChangeKind.TITLE)
        }

        /** Überschneidung der Zeiträume [frühester Beginn, spätester Beginn]; ohne Überschneidung leer. */
        private fun commonSpan(a: List<SourceCalendarOccurrence>, b: List<SourceCalendarOccurrence>): ClosedRange<java.time.Instant>? {
            if (a.isEmpty() || b.isEmpty()) return null
            val start = maxOf(a.minOf { it.startsAt }, b.minOf { it.startsAt })
            val end = minOf(a.maxOf { it.startsAt }, b.maxOf { it.startsAt })
            return if (start <= end) start..end else null
        }
    }
}
