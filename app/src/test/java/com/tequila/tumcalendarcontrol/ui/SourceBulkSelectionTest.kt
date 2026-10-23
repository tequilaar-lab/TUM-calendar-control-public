/*
 * Aufgabe: Schnellauswahl der Kursauswahl prüfen: „alle Kurse“ und „ganzer aktueller Kurs“.
 * Ein-/Ausgabe: Synthetische gespeicherte Termine -> Auswahlmengen im Zustand.
 * Nebenwirkungen: Keine; nur lokaler Testprozess.
 * Nicht zuständig: Darstellung, Regelbearbeitung oder Kalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceBulkSelectionTest {
    private val start = Instant.parse("2026-10-05T08:00:00Z")
    private fun occurrence(uid: String, instance: String, title: String, dayOffset: Long, cancelled: Boolean = false) =
        SourceCalendarOccurrence(uid, instance, title, start.plusSeconds(dayOffset * 86_400), start.plusSeconds(dayOffset * 86_400 + 5_400), cancelled, "Raum")

    // Kurs A: zwei Quellen (VO und UE) mit drei Terminen; Kurs B: ein Termin und ein abgesagter Termin.
    private val items = listOf(
        occurrence("a-vo", "a1", "Synthetik A", 0),
        occurrence("a-vo", "a2", "Synthetik A", 7),
        occurrence("a-ue", "a3", "Synthetik  A", 2),
        occurrence("b", "b1", "Synthetik B", 1),
        occurrence("b", "b2", "Synthetik B", 8, cancelled = true),
    )

    private fun state() = CalendarControlState(
        initialSourceSnapshots = SavedSourceSnapshots(latest = SavedSourceSnapshot(start, items.size, emptyMap(), items)),
        initialSyncDestinations = SyncDestinations(localCalendarEnabled = true),
    )

    private fun ids(vararg instances: String) = items.filter { it.instanceKey in instances }.map { it.identity }.toSet()

    @Test fun `全选包含全部未取消课次且不受搜索和筛选影响`() {
        val state = state()
        state.updateSourceBrowserQuery("B")
        state.updateSourceBrowserFilter(SourceCourseFilter.SELECTED)

        state.selectAllSourceOccurrences()

        assertEquals(ids("a1", "a2", "a3", "b1"), state.selectedSourceOccurrences)
        assertEquals(state.selectedSourceOccurrences, SourceBulkSelection.allSelectable(state.currentSourceOccurrences))
    }

    @Test fun `选中本课全部只追加当前课程且重复点击不变`() {
        val state = state()
        state.toggleSourceOccurrenceSelection(ids("b1").single())
        val groups = SourceBulkSelection.unfilteredGroups(state.currentSourceOccurrences, state.selectedSourceOccurrences)
        val courseA = SourceBulkSelection.currentGroup(groups, SourceBulkSelection.keyOf(items.first()))!!

        state.includeSourceOccurrences(SourceBulkSelection.courseAdditions(courseA))
        val once = state.selectedSourceOccurrences
        state.includeSourceOccurrences(SourceBulkSelection.courseAdditions(courseA))

        assertEquals(ids("a1", "a2", "a3", "b1"), once)
        assertEquals(once, state.selectedSourceOccurrences)
    }

    @Test fun `当前课程按显示组合并空白差异且默认第一组`() {
        val groups = SourceBulkSelection.unfilteredGroups(items, emptySet())

        assertEquals(listOf(3, 1), groups.map { it.totalOccurrenceCount })
        assertEquals("Synthetik A", SourceBulkSelection.currentGroup(groups, null)!!.displayTitle)
        assertEquals("Synthetik A", SourceBulkSelection.currentGroup(groups, "unbekannt")!!.displayTitle)
        assertEquals("Synthetik B", SourceBulkSelection.currentGroup(groups, SourceBulkSelection.keyOf(items[3]))!!.displayTitle)
    }
}
