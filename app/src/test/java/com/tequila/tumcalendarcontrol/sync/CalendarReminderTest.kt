/*
 * Aufgabe: Erinnerung beim Schreiben prüfen: ohne Verwaltung exakt bisheriger Fingerabdruck und Verhalten (auch fremde
 *   Erinnerungen ignoriert), Zuweisung nur an grüne Termine, Ausführung KEEP/UPDATE beim Ein-, Um- und Ausschalten,
 *   Google-Anfragekörper und -Lesen, Übergang der Einstellung.
 * Ein-/Ausgabe: synthetische Termine und Speicher-Gateway -> Fingerabdrücke, Änderungsarten, JSON.
 * Nebenwirkungen: Keine (Fake-Gateway, kein Kalender, kein Netzwerk).
 * Nicht zuständig: echter Calendar Provider oder Google (Gerät/Konto, gesondert zu prüfen).
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CalendarReminderTest {
    private val identity = ManagedEventIdentity("uid-syn", "2026-10-21T08:00:00Z")
    private val start = Instant.parse("2026-10-21T08:00:00Z")
    private val must = DesiredCalendarEvent(identity, "Synthetik", start, start.plusSeconds(5_400), AttendanceState.MUST_ATTEND, "Raum")
    private val planner = SyncDiffPlanner()
    private val local = OwnedCalendarRef(OutputTarget.LOCAL_CALENDAR, "local", "tum-calendar-control")

    @Test fun `ohne Verwaltung unveränderter Fingerabdruck`() {
        val fingerprint = planner.run { must.fingerprint() }
        assertFalse(fingerprint.contains("reminder"))
        assertEquals("2026-09-status-colors-v1|Synthetik|${must.startsAt}|${must.endsAt}|MUST_ATTEND|Raum", fingerprint)
        // Vom Nutzer gesetzte Erinnerung zählt ohne Verwaltung nicht.
        val actual = must.copy(reminderMinutes = 5).comparableTo(must)
        assertEquals(fingerprint, planner.run { actual.fingerprint() })
    }

    @Test fun `nur grüne Termine erhalten die Erinnerung`() {
        val occurrences = listOf(
            SourceCalendarOccurrence("uid-g", "1", "Grün", start, start.plusSeconds(3_600), false),
            SourceCalendarOccurrence("uid-b", "1", "Blau", start, start.plusSeconds(3_600), false),
        )
        val rules = listOf(SyncRule("uid-g", true, AttendanceState.MUST_ATTEND), SyncRule("uid-b", true, AttendanceState.CHOOSE_TO_ATTEND))
        val selected = occurrences.map { it.identity }.toSet()
        val off = SyncCandidateFactory().build(occurrences, selected, rules, emptyList())
        assertTrue(off.all { it.reminderMinutes == null })
        val on = SyncCandidateFactory().build(occurrences, selected, rules, emptyList(), mustAttendReminder = 15).associateBy { it.identity.sourceUid }
        assertEquals(15, on.getValue("uid-g").reminderMinutes)
        assertEquals(NO_REMINDER, on.getValue("uid-b").reminderMinutes)
    }

    /** Einschalten aktualisiert einmal, Wiederholung behält; Ausschalten (NO_REMINDER) aktualisiert einmal, dann behält. */
    @Test fun `Ausführung beim Ein-, Um- und Ausschalten`() {
        val gateway = FakeOwnedCalendarGateway(setOf("local"))
        val executor = OwnedCalendarSyncExecutor()
        fun run(event: DesiredCalendarEvent, mappings: List<StoredEventMapping>) = executor.execute(local, listOf(event), mappings, gateway)
        val first = run(must, emptyList())
        val keep = run(must, first.mappings)
        assertEquals(listOf(SyncChangeType.KEEP), keep.changes.map { it.type })
        val on = run(must.copy(reminderMinutes = 15), keep.mappings)
        assertEquals(listOf(SyncChangeType.UPDATE), on.changes.map { it.type })
        assertEquals(listOf(SyncChangeType.KEEP), run(must.copy(reminderMinutes = 15), on.mappings).changes.map { it.type })
        val changed = run(must.copy(reminderMinutes = 30), on.mappings)
        assertEquals(listOf(SyncChangeType.UPDATE), changed.changes.map { it.type })
        val off = run(must.copy(reminderMinutes = NO_REMINDER), changed.mappings)
        assertEquals(listOf(SyncChangeType.UPDATE), off.changes.map { it.type })
        assertEquals(listOf(SyncChangeType.KEEP), run(must.copy(reminderMinutes = NO_REMINDER), off.mappings).changes.map { it.type })
    }

    @Test fun `Google Anfragekörper und Lesen`() {
        val api = GoogleCalendarRestApi("synthetic-token")
        val none = JSONObject(with(api) { must.jsonBody() })
        assertFalse("ohne Verwaltung kein Feld", none.has("reminders"))
        assertFalse("NO_REMINDER: Feld fehlt, PUT stellt Vorgabe her", JSONObject(with(api) { must.copy(reminderMinutes = NO_REMINDER).jsonBody() }).has("reminders"))
        val body = JSONObject(with(api) { must.copy(reminderMinutes = 15).jsonBody() })
        val reminders = body.getJSONObject("reminders")
        assertFalse(reminders.getBoolean("useDefault"))
        assertEquals(15, reminders.getJSONArray("overrides").getJSONObject(0).getInt("minutes"))
        assertEquals(15, googleReminder(body))
        assertNull("Kalendervorgabe", googleReminder(JSONObject().put("reminders", JSONObject().put("useDefault", true))))
        assertNull("ohne Feld", googleReminder(JSONObject()))
    }

    @Test fun `Übergang der Einstellung`() {
        assertNull("nie verwaltet, „keine“ bleibt unverwaltet", CalendarReminderPreferences.next(null, null))
        assertEquals(15, CalendarReminderPreferences.next(null, 15))
        assertEquals("einmal verwaltet: ausschalten entfernt", NO_REMINDER, CalendarReminderPreferences.next(15, null))
        assertEquals(NO_REMINDER, CalendarReminderPreferences.next(NO_REMINDER, null))
    }
}
