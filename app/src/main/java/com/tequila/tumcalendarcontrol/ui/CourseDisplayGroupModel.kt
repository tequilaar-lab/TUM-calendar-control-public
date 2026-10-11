/*
 * Aufgabe: Rein visuellen Kursname-/Lehrtyp-/Terminbaum erstellen.
 * Ein-/Ausgabe: Kurse und Anzeigenamen -> rein visueller Kurs-/Typ-/Terminbaum.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Synchronisierungsidentität anhand von Titel/Lehrtyp, Zusammenlegen von UIDs/Instanzen, Quellenfilter oder Benutzeraktionen.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import com.tequila.tumcalendarcontrol.domain.Course
import com.tequila.tumcalendarcontrol.domain.displayTitle
import java.util.Locale

/** Visuelle Gruppierung nach Kursname ohne Lehrtyp; Course.id behält jede Mitgliedsidentität. */
internal data class CourseDisplayGroup(
    val key: String,
    val displayTitle: String,
    val teachingTypes: List<CourseTeachingTypeGroup>,
) {
    val courses: List<Course> get() = teachingTypes.flatMap(CourseTeachingTypeGroup::courses)
    val teachingTypeCount: Int get() = teachingTypes.size
}

/** Typzeile ist Browserknoten, kein Synchronisierungstyp oder Kalenderfarbe; unbekannte Suffixe in sonstige Termine. */
internal data class CourseTeachingTypeGroup(
    val key: String,
    val label: String,
    val courses: List<Course>,
)

internal object CourseDisplayGroupModel {
    /** Leerzeichen/Großschreibung teilen visuelle Gruppen nicht; Sortierung ausschließlich zum Browsen. */
    fun groups(courses: List<Course>, typeNames: Map<String, String> = emptyMap()): List<CourseDisplayGroup> = courses
        .map { course -> course to parseTitle(course.displayTitle) }
        .groupBy { (_, parsed) -> normalizedKey(parsed.courseName) }
        .map { (key, members) ->
            val title = members.first().second.courseName.ifBlank { message(328) }
            val types = members
                .groupBy { (_, parsed) -> parsed.type }
                .map { (type, typeMembers) ->
                    CourseTeachingTypeGroup(
                        key = "${(key)}:${(normalizedKey(type.key))}",
                        label = typeNames["${(key)}:${(normalizedKey(type.key))}"] ?: type.label,
                        courses = typeMembers.map { it.first }.sortedWith(compareBy<Course> { it.startsAt }.thenBy { it.id }),
                    )
                }
                .sortedWith(compareBy<CourseTeachingTypeGroup> { it.label == TitleRules.OTHER_TYPE.label }.thenBy(CourseTeachingTypeGroup::label))
            CourseDisplayGroup(key = key, displayTitle = title, teachingTypes = types)
        }
        .sortedWith(compareBy<CourseDisplayGroup> { it.displayTitle.lowercase(Locale.ROOT) }.thenBy(CourseDisplayGroup::key))

    /** Gleiche visuelle Gruppierung für Änderungsübersicht; niemals Synchronisierungsidentität. */
    fun titleGroupingKey(title: String): String = normalizedKey(parseTitle(title).courseName)

    /** Nur standardisierte TUM-Lehrtypen am Titelende erkennen; keine normalen Wörter oder Orte als Typ. */
    private fun parseTitle(rawTitle: String): ParsedCourseTitle {
        val title = rawTitle.trim().replace(Regex("\\s+"), " ").ifBlank { message(328) }
        val bracket = TitleRules.BRACKET_SUFFIX.find(title)
        if (bracket != null) {
            val type = recognizedType(bracket.groupValues[1])
            if (type != null) return ParsedCourseTitle(title.removeRange(bracket.range).cleanName(), type)
        }
        val suffix = TitleRules.TYPE_SUFFIX.find(title)
        if (suffix != null) {
            val type = TeachingType(suffix.groupValues[1].uppercase(Locale.ROOT))
            return ParsedCourseTitle(title.removeRange(suffix.range).cleanName(), type)
        }
        val grouped = TitleRules.TYPE_BEFORE_GROUP.find(title)
        if (grouped != null) {
            return ParsedCourseTitle(title.substring(0, grouped.range.first).cleanName(), TeachingType(grouped.groupValues[1].uppercase(Locale.ROOT)))
        }
        // Fallback nur bei unbekanntem Standardtyp; vollständige Wörter verhindern PR-Erkennung in Praktikumsbericht.
        val inferred = when {
            TitleRules.VI_TOKEN.containsMatchIn(title) -> TeachingType("VI")
            TitleRules.PRACTICAL_TOKEN.containsMatchIn(title) -> TeachingType("PR")
            else -> TitleRules.OTHER_TYPE
        }
        return ParsedCourseTitle(title, inferred)
    }

    private fun recognizedType(bracketContent: String): TeachingType? = TitleRules.TYPE_TOKEN.find(bracketContent)
        ?.groupValues?.get(1)?.uppercase(Locale.ROOT)?.let(::TeachingType)

    private fun String.cleanName(): String = trim().trimEnd(',', ';', ':', '/', '-', '–', '—').trim().ifBlank { message(328) }
    private fun normalizedKey(title: String): String = title.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT).ifBlank { "(untitled)" }

    private data class ParsedCourseTitle(val courseName: String, val type: TeachingType)
    private data class TeachingType(val label: String) { val key: String get() = label.lowercase(Locale.ROOT) }

    private object TitleRules {
        val OTHER_TYPE = TeachingType(message(329))
        private const val TYPE_NAMES = "VO|UE|PR|SE|TU|EX|KO|VL|VI|ÜB|VÜ"
        val VI_TOKEN = Regex("(?i)(?<![\\p{L}\\p{N}])VI(?![\\p{L}\\p{N}])")
        val PRACTICAL_TOKEN = Regex("(?i)(?<![\\p{L}\\p{N}])(?:Projektpraktikum|Praktikum)(?![\\p{L}\\p{N}])")
        val TYPE_TOKEN = Regex("(?i)(?<![A-ZÄÖÜ])(${(TYPE_NAMES)})(?![A-ZÄÖÜ])")
        val BRACKET_SUFFIX = Regex("\\s*[\\(\\[]([^\\)\\]]+)[\\)\\]]\\s*$")
        val TYPE_SUFFIX = Regex("(?i)(?:\\s|[,;:/\\-–—])+($TYPE_NAMES)\\s*$")
        val TYPE_BEFORE_GROUP = Regex("(?i)\\s+($TYPE_NAMES)\\s*,\\s*(?:Standardgruppe|Gruppe\\b.*|Group\\b.*)$")
    }
}

/** Nur visuelle Gruppenschlüssel speichern; Auswahl verändert keine Gruppen oder Kursmitglieder. */
internal fun Set<String>.toggleCourseDisplayGroup(key: String): Set<String> =
    if (key in this) this - key else this + key

internal fun Set<String>.toggleCourseTeachingTypeGroup(key: String): Set<String> =
    if (key in this) this - key else this + key
