/*
 * Aufgabe: Ausdrücklich behaltene Kursmomentaufnahmen in privaten Präferenzen speichern.
 * Ein-/Ausgabe: Erfolgreiche iCal-Vorschau -> wiederherstellbare Kurse, Instanzen und aktive Datenversion.
 * Nebenwirkungen: Nur app-private SharedPreferences, keine Quellenadressen oder Original-ICS.
 * Nicht zuständig: Abonnementadressen, Original-ICS, OAuth, Zielkalenderdaten, automatische Ausgabe oder Titelerkennung.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import android.content.Context
import com.tequila.tumcalendarcontrol.sync.IcalReadOnlyPreview
import com.tequila.tumcalendarcontrol.sync.IcalSkipReason
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceCourseCatalog
import java.time.Instant
import java.util.Base64

/** Beide Slots sind erfolgreich gelesene Quellen; nur aktiver Slot in Vorschau. */
enum class SourceSnapshotSlot { LATEST, PREVIOUS }

/** Nur Katalog und Instanzidentität speichern, keine Originaldatei oder Abonnementadresse. */
data class SavedSourceSnapshot(
    val capturedAt: Instant,
    val sourceEventCount: Int,
    val skippedByReason: Map<IcalSkipReason, Int>,
    val occurrences: List<SourceCalendarOccurrence>,
) {
    fun toPreview(): IcalReadOnlyPreview = IcalReadOnlyPreview(
        sourceEventCount = sourceEventCount,
        manageableOccurrenceCount = occurrences.size,
        skippedByReason = skippedByReason,
        firstManageableStart = occurrences.minOfOrNull(SourceCalendarOccurrence::startsAt),
        lastManageableEnd = occurrences.maxOfOrNull(SourceCalendarOccurrence::endsAt),
        courses = SourceCourseCatalog.from(occurrences),
        occurrences = occurrences,
    )

    companion object {
        fun from(preview: IcalReadOnlyPreview, capturedAt: Instant = Instant.now()): SavedSourceSnapshot = SavedSourceSnapshot(
            capturedAt = capturedAt,
            sourceEventCount = preview.sourceEventCount,
            skippedByReason = preview.skippedByReason,
            occurrences = preview.occurrences,
        )
    }
}

/** Neue Lesung behält eine Vorgängerversion; Wechsel nur aktive Referenz, kein implizites Lesen/Schreiben. */
data class SavedSourceSnapshots(
    val latest: SavedSourceSnapshot? = null,
    val previous: SavedSourceSnapshot? = null,
    val activeSlot: SourceSnapshotSlot = SourceSnapshotSlot.LATEST,
) {
    val active: SavedSourceSnapshot? get() = when (activeSlot) {
        SourceSnapshotSlot.LATEST -> latest ?: previous
        SourceSnapshotSlot.PREVIOUS -> previous ?: latest
    }

    fun storeFresh(snapshot: SavedSourceSnapshot): SavedSourceSnapshots = copy(
        latest = snapshot,
        previous = latest,
        activeSlot = SourceSnapshotSlot.LATEST,
    )

    fun select(slot: SourceSnapshotSlot): SavedSourceSnapshots = when {
        slot == SourceSnapshotSlot.PREVIOUS && previous == null -> this
        else -> copy(activeSlot = slot)
    }
}

/** Private Präferenzfelder Base64-kodieren; Kursinhalt darf nicht Teil des Präferenzschlüssels werden. */
internal class SourceSnapshotPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private var loaded: SavedSourceSnapshots? = null
    val hasStoredData: Boolean get() = preferences.contains(KEY)
    var recoveryNotice: String? = null
        private set

    fun load(): SavedSourceSnapshots {
        val raw = preferences.getString(KEY, null)
        val decoded = SourceSnapshotCodec.decode(raw)
        if (raw != null && SourceSnapshotCodec.encode(decoded) != raw) {
            // Einzige wiederherstellbare Kopie nicht überschreiben; beschädigtes/altes Format nur privat behalten.
            preferences.edit().putString("recovery_original", raw).apply()
            recoveryNotice = if (decoded.active == null) message(651) else message(652)
        }
        loaded = decoded
        return decoded
    }

    fun save(snapshots: SavedSourceSnapshots) {
        if (snapshots == loaded) return
        preferences.edit().putString(KEY, SourceSnapshotCodec.encode(snapshots)).apply()
        loaded = snapshots
    }

    private companion object {
        const val FILE_NAME = "source_snapshots"
        const val KEY = "saved_source_snapshots_v1"
    }
}

/** Unit-testbarer kompakter Codec; Fehler liefert leere Daten, keine Inhaltsprotokollierung oder Ausnahme. */
internal object SourceSnapshotCodec {
    private const val VERSION = "v1"
    private const val LATEST = "latest"
    private const val PREVIOUS = "previous"
    private const val ACTIVE = "active"
    private const val OCCURRENCE = "occurrence"
    private const val SKIP = "skip"

    fun encode(snapshots: SavedSourceSnapshots): String = buildList {
        add(listOf(VERSION, ACTIVE, snapshots.activeSlot.name).joinToString("|"))
        snapshots.latest?.let { addAll(encodeSnapshot(LATEST, it)) }
        snapshots.previous?.let { addAll(encodeSnapshot(PREVIOUS, it)) }
    }.joinToString("\n")

    fun decode(encoded: String?): SavedSourceSnapshots = runCatching {
        if (encoded.isNullOrBlank()) return SavedSourceSnapshots()
        val rows = encoded.lineSequence().filter(String::isNotBlank).map { it.split("|") }.toList()
        require(rows.firstOrNull()?.firstOrNull() == VERSION)
        val active = rows.firstOrNull { it.getOrNull(1) == ACTIVE }?.getOrNull(2)
            ?.let(SourceSnapshotSlot::valueOf) ?: SourceSnapshotSlot.LATEST
        val latest = runCatching { decodeSnapshot(rows, LATEST) }.getOrNull()
        val previous = runCatching { decodeSnapshot(rows, PREVIOUS) }.getOrNull()
        SavedSourceSnapshots(latest, previous, if (latest == null && previous != null) SourceSnapshotSlot.PREVIOUS else active)
    }.getOrElse { SavedSourceSnapshots() }

    private fun encodeSnapshot(slot: String, snapshot: SavedSourceSnapshot): List<String> = buildList {
        add(listOf(VERSION, slot, "meta", snapshot.capturedAt.toEpochMilli(), snapshot.sourceEventCount).joinToString("|"))
        snapshot.skippedByReason.toSortedMap(compareBy(IcalSkipReason::name)).forEach { (reason, count) ->
            add(listOf(VERSION, slot, SKIP, reason.name, count).joinToString("|"))
        }
        snapshot.occurrences.sortedWith(compareBy(SourceCalendarOccurrence::sourceUid).thenBy(SourceCalendarOccurrence::instanceKey)).forEach { occurrence ->
            add(
                listOf(
                    VERSION, slot, OCCURRENCE, encodeField(occurrence.sourceUid), encodeField(occurrence.instanceKey),
                    encodeField(occurrence.title), occurrence.startsAt.toEpochMilli(), occurrence.endsAt.toEpochMilli(),
                    if (occurrence.isCancelled) "1" else "0", encodeField(occurrence.location),
                ).joinToString("|"),
            )
        }
    }

    private fun decodeSnapshot(rows: List<List<String>>, slot: String): SavedSourceSnapshot? {
        val slotRows = rows.filter { it.getOrNull(1) == slot }
        val meta = slotRows.firstOrNull { it.getOrNull(2) == "meta" } ?: return null
        val skipped = slotRows.filter { it.getOrNull(2) == SKIP }.associate { row ->
            IcalSkipReason.valueOf(row.requireField(3)) to row.requireField(4).toInt()
        }
        val occurrences = slotRows.filter { it.getOrNull(2) == OCCURRENCE }.map { row ->
            SourceCalendarOccurrence(
                sourceUid = decodeField(row.requireField(3)),
                instanceKey = decodeField(row.requireField(4)),
                title = decodeField(row.requireField(5)),
                startsAt = Instant.ofEpochMilli(row.requireField(6).toLong()),
                endsAt = Instant.ofEpochMilli(row.requireField(7).toLong()),
                isCancelled = row.requireField(8) == "1",
                location = decodeField(row.getOrNull(9) ?: error(message(653))),
            )
        }
        return SavedSourceSnapshot(
            capturedAt = Instant.ofEpochMilli(meta.requireField(3).toLong()),
            sourceEventCount = meta.requireField(4).toInt(),
            skippedByReason = skipped,
            occurrences = occurrences,
        )
    }

    private fun List<String>.requireField(index: Int): String = getOrNull(index)?.takeIf(String::isNotBlank) ?: error(message(653))
    private fun encodeField(value: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun decodeField(value: String): String = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
}

/** Änderungen nur per UID/Instanz; Namen erklären lediglich neue Kurse mit bekanntem/neuem Titel. */
data class SourceSnapshotDifference(
    val addedOccurrenceCount: Int,
    val removedOccurrenceCount: Int,
    val changedOccurrenceCount: Int,
    val sameTitleNewCourseCount: Int,
    val newTitleCourseCount: Int,
) {
    val isEmpty: Boolean get() = addedOccurrenceCount == 0 && removedOccurrenceCount == 0 && changedOccurrenceCount == 0

    fun summary(): String = if (isEmpty) {
        message(654)
    } else {
        message(655, (addedOccurrenceCount), (removedOccurrenceCount), (changedOccurrenceCount), (sameTitleNewCourseCount), (newTitleCourseCount))
    }
}

internal object SourceSnapshotDifferenceModel {
    fun compare(previous: SavedSourceSnapshot, latest: SavedSourceSnapshot): SourceSnapshotDifference {
        val previousByIdentity = previous.occurrences.associateBy { it.identity }
        val latestByIdentity = latest.occurrences.associateBy { it.identity }
        val added = latestByIdentity.keys - previousByIdentity.keys
        val removed = previousByIdentity.keys - latestByIdentity.keys
        val changed = latestByIdentity.keys.intersect(previousByIdentity.keys).count { key -> latestByIdentity.getValue(key) != previousByIdentity.getValue(key) }
        val previousUids = previous.occurrences.map(SourceCalendarOccurrence::sourceUid).toSet()
        val previousTitles = previous.occurrences.map { CourseDisplayGroupModel.titleGroupingKey(it.title) }.toSet()
        val addedCourseFirsts = latest.occurrences
            .filter { it.sourceUid !in previousUids }
            .distinctBy(SourceCalendarOccurrence::sourceUid)
        return SourceSnapshotDifference(
            addedOccurrenceCount = added.size,
            removedOccurrenceCount = removed.size,
            changedOccurrenceCount = changed,
            sameTitleNewCourseCount = addedCourseFirsts.count { CourseDisplayGroupModel.titleGroupingKey(it.title) in previousTitles },
            newTitleCourseCount = addedCourseFirsts.count { CourseDisplayGroupModel.titleGroupingKey(it.title) !in previousTitles },
        )
    }
}
