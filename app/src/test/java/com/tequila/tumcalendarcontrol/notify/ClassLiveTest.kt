/*
 * Aufgabe: Live-Kursbenachrichtigung prüfen: Planung (aus, vorher, Vorlauf mit Toleranz, laufend, direkt folgender Kurs,
 *   blaue/graue Kurse), Texte und Fortschritt, Einplanung mit Benachrichtigung und Wecker, Live-Update erst ab API 36.1,
 *   angemeldete Berechtigung.
 * Ein-/Ausgabe: synthetische Termine („jetzt“ Mi 21.10.2026 10:00 Berlin) -> Plan, Texte, Robolectric-Benachrichtigung.
 * Nebenwirkungen: Nur private Test-Präferenzen, Robolectric-Benachrichtigungen und -Wecker.
 * Nicht zuständig: Einstellungsseite (ClassLivePagesTest).
 */
package com.tequila.tumcalendarcontrol.notify

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.sync.DesiredCalendarEvent
import com.tequila.tumcalendarcontrol.sync.ManagedEventIdentity
import com.tequila.tumcalendarcontrol.sync.OutputTarget
import com.tequila.tumcalendarcontrol.sync.RuleLedger
import com.tequila.tumcalendarcontrol.sync.RuleLedgerPreferences
import com.tequila.tumcalendarcontrol.sync.StoredEventMapping
import com.tequila.tumcalendarcontrol.widget.CourseShortNames
import com.tequila.tumcalendarcontrol.widget.WidgetEvent
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ClassLiveTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-10-21T08:00:00Z")
    private fun at(hour: Int, minute: Int = 0) = LocalDate.of(2026, 10, 21).atTime(hour, minute).atZone(zone).toInstant()
    private fun ev(key: String, hour: Int, minute: Int = 0, state: AttendanceState = AttendanceState.MUST_ATTEND, room: String = "Hörsaal 1 (0000.00.001)") =
        WidgetEvent(key, "uid-$key", "Synthetik $key", at(hour, minute), at(hour, minute).plus(Duration.ofMinutes(90)), state, room)
    private val on = ClassLiveSettings(enabled = true, leadMinutes = 15)

    @Test fun `aus oder ohne passende Kurse nichts`() {
        assertEquals(LivePlan(null, null, false), ClassLivePlanner.plan(listOf(ev("a", 10, 5)), ClassLiveSettings(), now))
        val gray = ClassLivePlanner.plan(listOf(ev("g", 10, 5, AttendanceState.STOP_ATTENDING)), on, now)
        assertNull(gray.current)
        assertNull(gray.nextCheck)
    }

    @Test fun `vorher Wecker zum Hinweis, im Vorlauf bevorstehend`() {
        // „jetzt“ 10:00, Kurs 11:00: Hinweis ab 10:45, Wecker darf früher kommen.
        val before = ClassLivePlanner.plan(listOf(ev("a", 11)), on, now)
        assertNull(before.current)
        assertEquals(at(10, 45) to true, before.nextCheck to before.early)
        // Kurs 10:20: innerhalb Vorlauf + Toleranz -> bevorstehend, nächste Prüfung zum Beginn (später erlaubt).
        val soon = ClassLivePlanner.plan(listOf(ev("b", 10, 20)), on, now)
        assertEquals(LivePhase.UPCOMING, soon.current?.phase)
        assertEquals(at(10, 20) to false, soon.nextCheck to soon.early)
    }

    @Test fun `laufend bis Ende, danach der nächste Hinweis`() {
        val plan = ClassLivePlanner.plan(listOf(ev("a", 9, 45), ev("b", 13)), on, now)
        assertEquals(LivePhase.IN_PROGRESS, plan.current?.phase)
        assertEquals("a", plan.current?.event?.key)
        assertEquals(at(11, 15), plan.nextCheck)
        // Direkt folgender Kurs (11:15): Hinweis 11:00 liegt vor dem Ende -> erst Hinweis wecken, laufend bleibt sichtbar.
        val chained = ClassLivePlanner.plan(listOf(ev("a", 9, 45), ev("c", 11, 15)), on, now)
        assertEquals(at(11, 0) to true, chained.nextCheck to chained.early)
        val atNotice = ClassLivePlanner.plan(listOf(ev("a", 9, 45), ev("c", 11, 15)), on, at(10, 55))
        assertEquals("a", atNotice.current?.event?.key)
        assertEquals(at(11, 15), atNotice.nextCheck)
    }

    @Test fun `blaue Kurse nur auf Wunsch`() {
        val blue = listOf(ev("b", 10, 10, AttendanceState.CHOOSE_TO_ATTEND))
        assertNull(ClassLivePlanner.plan(blue, on, now).current)
        assertEquals("b", ClassLivePlanner.plan(blue, on.copy(includeChooseToAttend = true), now).current?.event?.key)
    }

    @Test fun `Texte und Fortschritt`() {
        val upcoming = ClassLiveNotifier.content(LiveState(ev("a", 10, 20), LivePhase.UPCOMING), "SVS", zone)
        assertEquals(ClassLiveNotifier.Content("下一节 10:20：SVS", "Hörsaal 1 (0000.00.001)", "10:20"), upcoming)
        val running = ClassLiveNotifier.content(LiveState(ev("a", 9, 45), LivePhase.IN_PROGRESS), "SVS", zone)
        assertEquals(ClassLiveNotifier.Content("进行中：SVS", "Hörsaal 1 (0000.00.001) · 至 11:15", "11:15"), running)
        assertEquals("至 11:15", ClassLiveNotifier.content(LiveState(ev("a", 9, 45, room = ""), LivePhase.IN_PROGRESS), "SVS", zone).text)
        assertEquals(16, ClassLiveNotifier.progressPercent(LiveState(ev("a", 9, 45), LivePhase.IN_PROGRESS), now))
    }

    /** Geschriebener Kurs in 15 Minuten: Benachrichtigung mit Kurzname, Live-Update angefragt, Wecker zum Beginn. */
    @Test fun `Einplanung zeigt Benachrichtigung und stellt Wecker`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        com.tequila.tumcalendarcontrol.i18n.AppLanguagePreferences(app).save(com.tequila.tumcalendarcontrol.i18n.AppLanguage.CHINESE)
        val identity = ManagedEventIdentity("uid-live", "1")
        val written = DesiredCalendarEvent(identity, "Synthetik Signalverarbeitung", at(10, 15), at(11, 45), AttendanceState.MUST_ATTEND, "Hörsaal 1 (0000.00.001)")
        RuleLedgerPreferences(app).save(RuleLedger(mappings = listOf(StoredEventMapping(OutputTarget.LOCAL_CALENDAR, identity, "e1", "f1", writtenEvent = written))))
        CourseShortNames(app).set("uid-live", "SVS")
        ClassLiveSettingsStore(app).save(on)
        ClassLiveScheduler.refresh(app, now, zone)
        val posted = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals("下一节 10:15：SVS", posted.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(posted.flags and Notification.FLAG_ONGOING_EVENT != 0)
        val alarm = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.single()
        assertEquals(at(10, 15).toEpochMilli(), alarm.triggerAtTime)
        // Ausschalten entfernt Benachrichtigung und Wecker.
        ClassLiveSettingsStore(app).save(on.copy(enabled = false))
        ClassLiveScheduler.refresh(app, now, zone)
        assertTrue(shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.isEmpty())
        assertTrue(shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
    }

    /** Live-Update-Anfrage erst ab API 36.1 (Android 16 QPR2); 36.0 und älter nur normale dauerhafte Benachrichtigung. */
    @Test fun `Live-Update erst ab 36_1`() {
        assertFalse(ClassLiveNotifier.promotionAvailable(35) { 3500000 })
        assertFalse(ClassLiveNotifier.promotionAvailable(36) { android.os.Build.VERSION_CODES_FULL.BAKLAVA })
        assertTrue(ClassLiveNotifier.promotionAvailable(36) { android.os.Build.VERSION_CODES_FULL.BAKLAVA_1 })
        assertTrue(ClassLiveNotifier.promotionAvailable(37) { android.os.Build.VERSION_CODES_FULL.BAKLAVA_1 + 1 })
    }

    /** Testbenachrichtigung: synthetischer laufender Kurs, sofort sichtbar; Hochstufung im Robolectric-36 nicht gesetzt. */
    @Test fun `Testbenachrichtigung`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        com.tequila.tumcalendarcontrol.i18n.AppLanguagePreferences(app).save(com.tequila.tumcalendarcontrol.i18n.AppLanguage.CHINESE)
        assertNull("ohne aktive Benachrichtigung unbekannt", ClassLiveNotifier.isPromoted(app))
        ClassLiveNotifier.showTest(app, now, zone)
        val posted = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals("进行中：示例课程", posted.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(false, ClassLiveNotifier.isPromoted(app))
        // Gerätefund: Neuplanung beim Verlassen der App (Schalter aus) darf die Testbenachrichtigung nicht entfernen.
        ClassLiveScheduler.refresh(app, now, zone)
        assertEquals(1, shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.size)
    }

    @Test fun `ohne Benachrichtigungsberechtigung nichts gezeigt`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(ClassLiveNotifier.canPost(app))
    }

    @Test fun `Berechtigung für Live-Updates angemeldet`() {
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
        assertNotNull(info.requestedPermissions)
        assertTrue(Manifest.permission.POST_PROMOTED_NOTIFICATIONS in info.requestedPermissions!!.toList())
    }
}
