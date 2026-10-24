/*
 * Aufgabe: Einstellungsseite der Startbildschirm-Widgets: Auswahl des platzierten Widgets (oder Standard für neue),
 *   Live-Vorschau in seiner echten Größe (echte RemoteViews), Inhalt je Art, Aussehen (auch „Systemaussehen folgen“),
 *   Blockgröße innerhalb der Zellen, Kurznamen für „Woche“ und „Stundenplan“ und „Zum Startbildschirm“ (nur beim Standard).
 * Ein-/Ausgabe: WidgetPreferences je Widget-ID und geschriebene Termine -> Bedienelemente; Änderungen -> speichern und neu zeichnen.
 * Nebenwirkungen: Nur private Widget-Einstellungen und Kurznamen; Hinzufügen fragt den Startbildschirm (Systemdialog,
 *   Nutzer bestätigt).
 * Nicht zuständig: Zeichnen/Daten der Widgets (widget-Paket), Regeln, Ausgabe oder Kalender.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.sync.RuleLedgerPreferences
import com.tequila.tumcalendarcontrol.ui.design.DesignCheck
import com.tequila.tumcalendarcontrol.ui.design.DesignChips
import com.tequila.tumcalendarcontrol.ui.design.DesignPage
import com.tequila.tumcalendarcontrol.ui.design.DesignPillButton
import com.tequila.tumcalendarcontrol.ui.design.DesignSection
import com.tequila.tumcalendarcontrol.ui.design.DesignSegmented
import com.tequila.tumcalendarcontrol.ui.design.DesignSlider
import com.tequila.tumcalendarcontrol.widget.BlockPercentRange
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors
import com.tequila.tumcalendarcontrol.widget.CountdownWidgetRenderer
import com.tequila.tumcalendarcontrol.widget.CourseShortNames
import com.tequila.tumcalendarcontrol.widget.GridWidgetModel
import com.tequila.tumcalendarcontrol.widget.ScheduleWidgetModel
import com.tequila.tumcalendarcontrol.sync.ManualEventKind
import com.tequila.tumcalendarcontrol.sync.ManualScheduleItem
import com.tequila.tumcalendarcontrol.ui.design.DesignChoice
import com.tequila.tumcalendarcontrol.ui.design.DesignField
import com.tequila.tumcalendarcontrol.widget.ScheduleWidgetRenderer
import com.tequila.tumcalendarcontrol.widget.ScheduleWidgets
import com.tequila.tumcalendarcontrol.widget.SystemWidgetLook
import com.tequila.tumcalendarcontrol.widget.WidgetCorner
import com.tequila.tumcalendarcontrol.widget.WidgetEvent
import com.tequila.tumcalendarcontrol.widget.WidgetFrame
import com.tequila.tumcalendarcontrol.widget.WidgetPreferences
import com.tequila.tumcalendarcontrol.widget.WidgetSettings
import com.tequila.tumcalendarcontrol.widget.WidgetSize
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

private val TextInset = Modifier.padding(horizontal = 4.dp)

/** Uhr und Zeitzone der Vorschau; Tests setzen feste Werte, damit Bilder nicht von der Uhrzeit abhängen. */
internal data class WidgetPreviewClock(val now: () -> Instant = Instant::now, val zone: () -> ZoneId = ZoneId::systemDefault)
internal val LocalWidgetPreviewClock = staticCompositionLocalOf { WidgetPreviewClock() }
private val CornerLabels = listOf(823, 824, 825, 826, 827)
/** Arten in der Vorschau des Standards (Reihenfolge = Segmente). */
private val PreviewTypes = listOf(WidgetSize.SMALL, WidgetSize.MEDIUM, WidgetSize.TOMORROW, WidgetSize.LARGE, WidgetSize.GRID, WidgetSize.COUNTDOWN, WidgetSize.CHECK)

/** Bearbeitungsziel „Standard für neue Widgets“ (kein platziertes Widget). */
private const val DefaultTarget = -1

/** Anzeigename einer Widget-Art (wie in der Widget-Liste des Startbildschirms). */
internal fun WidgetSize.label(): String = when (this) {
    WidgetSize.SMALL -> message(785)
    WidgetSize.MEDIUM -> message(787)
    WidgetSize.LARGE -> message(789)
    WidgetSize.COUNTDOWN -> message(857)
    WidgetSize.CHECK -> message(873)
    WidgetSize.GRID -> message(913)
    WidgetSize.TOMORROW -> message(918)
}

/**
 * Oben die platzierten Widgets (Art · Nummer, Größe) und „Standard für neue Widgets“; darunter Vorschau in der echten
 * Größe des gewählten Widgets und nur die Optionen, die für seine Art gelten. Änderungen gelten nur für das gewählte Ziel.
 */
@Composable
fun WidgetSettingsScreen(state: CalendarControlState) {
    val context = LocalContext.current
    val preferences = remember(context) { WidgetPreferences(context) }
    // Bei jedem Zeichnen frisch: Die Seite kann im Hintergrund offen bleiben, während Widgets hinzukommen (Gerätefund).
    val placed = ScheduleWidgets.placed(context)
    var target by rememberSaveable { mutableStateOf(state.editingWidgetId?.takeIf { id -> placed.any { it.id == id } } ?: placed.firstOrNull()?.id ?: DefaultTarget) }
    // Neuer Einstieg aus „Bearbeiten“ auf dem Startbildschirm: dieses Widget wählen, auch wenn die Seite schon offen war.
    LaunchedEffect(state.editingWidgetId) {
        val wanted = state.editingWidgetId ?: return@LaunchedEffect
        if (placed.any { it.id == wanted }) target = wanted
        state.editingWidgetId = null
    }
    val current = placed.firstOrNull { it.id == target }
    var settings by remember(target) { mutableStateOf(current?.let { preferences.load(it.id, it.size) } ?: preferences.load()) }
    var defaultPreview by rememberSaveable { mutableStateOf(WidgetSize.SMALL.name) }
    var pinFeedback by remember { mutableStateOf<String?>(null) }
    val size = current?.size ?: WidgetSize.valueOf(defaultPreview)
    // Eigene geschriebene Termine, sonst synthetische Beispiele (dann mit Hinweis); beides nur zur Vorschau.
    val written = remember(context) { runCatching { ScheduleWidgetModel.eventsFrom(RuleLedgerPreferences(context).load().mappings) }.getOrDefault(emptyList()) }
    val clock = LocalWidgetPreviewClock.current
    val now = remember(clock) { clock.now() }
    val events = written.ifEmpty { sampleEvents(now) }
    // Countdown: eigene Prüfungen/Fristen, sonst Beispiele (mit demselben Hinweis).
    val manual = state.ruleLedger.manualEvents
    val hasCountdownData = manual.any { it.kind != ManualEventKind.NORMAL }
    val countdownItems = if (hasCountdownData) manual else sampleCountdown(now)
    // Kurznamen für „Woche“ und „Stundenplan“ (je Quell-UID, nur lokal); Vorschau folgt sofort.
    val shortNameStore = remember(context) { CourseShortNames(context) }
    var shortNames by remember { mutableStateOf(shortNameStore.load()) }
    fun change(next: WidgetSettings) {
        settings = next
        if (current != null) preferences.save(current.id, next) else preferences.save(next)
        ScheduleWidgets.refreshAll(context)
    }
    // Für platzierte Widgets nur passende Optionen; beim Standard alle (gilt für jede Art).
    val showCourse = current == null || size in setOf(WidgetSize.SMALL, WidgetSize.MEDIUM, WidgetSize.LARGE, WidgetSize.GRID, WidgetSize.TOMORROW)
    // Kurznamen gelten für „Woche“ und „Stundenplan“.
    val optionalShortNames = current == null || size in setOf(WidgetSize.SMALL, WidgetSize.MEDIUM, WidgetSize.TOMORROW)
    val showShortNames = current == null || size == WidgetSize.LARGE || size == WidgetSize.GRID || (optionalShortNames && settings.useShortNames)
    val showWeek = current == null || size == WidgetSize.LARGE
    val showCountdown = current == null || size == WidgetSize.COUNTDOWN
    // Neues Ziel beginnt oben (eigener Bildlaufzustand je Ziel).
    key(target) { DesignPage(contentTag = "widgets-content") {
        DesignText(tr(message(812)), 13, TextInset)
        DesignText(tr(message(813)), 12, TextInset, muted = true)

        DesignSection(tr(message(878)), TextInset)
        if (placed.isEmpty()) DesignText(tr(message(887)), 12, TextInset, muted = true)
        Column {
            placed.forEach { widget ->
                val number = placed.filter { it.size == widget.size }.indexOf(widget) + 1
                DesignChoice(
                    tr(message(147, widget.size.label(), number)), target == widget.id, Modifier.testTag("widgets-target-${widget.id}"),
                    detail = tr(message(881, widget.widthDp, widget.heightDp)),
                ) { target = widget.id }
            }
            DesignChoice(tr(message(879)), target == DefaultTarget, Modifier.testTag("widgets-target-default")) { target = DefaultTarget }
        }

        DesignSection(tr(message(831)), TextInset)
        if (current == null) {
            // Mehr als fünf Arten: umbrechende Chips statt Segmenten; Prüf-Widget mit Kurzname.
            DesignChips(PreviewTypes.map { tr(if (it == WidgetSize.CHECK) message(888) else it.label()) }, PreviewTypes.indexOf(size), Modifier.testTag("widgets-preview-size")) { defaultPreview = PreviewTypes[it].name }
        }
        val width = current?.widthDp ?: previewWidth(size)
        val height = current?.heightDp ?: previewHeight(size)
        // Kurznamen auch über Wochen gleichen Titels (TUM vergibt je Woche eigene UIDs).
        val resolvedShortNames = CourseShortNames.resolve(shortNames, written.ifEmpty { events })
        WidgetPreview(size, WidgetFrame(events, true, settings, now, clock.zone(), width, height, countdownItems, resolvedShortNames))
        if (if (size == WidgetSize.COUNTDOWN) !hasCountdownData else written.isEmpty()) DesignText(tr(message(836)), 11, TextInset, muted = true)

        if (showCourse) {
            DesignSection(tr(message(814)), TextInset)
            Column {
                DesignCheck(tr(message(815)), settings.hideStopAttending, TextInset.testTag("widgets-hide-gray")) { change(settings.copy(hideStopAttending = it)) }
                DesignCheck(tr(message(816)), settings.hideChooseToAttend, TextInset.testTag("widgets-hide-blue")) { change(settings.copy(hideChooseToAttend = it)) }
                DesignCheck(tr(message(817)), settings.showLocation, TextInset.testTag("widgets-location")) { change(settings.copy(showLocation = it)) }
                if (optionalShortNames) DesignCheck(tr(message(942)), settings.useShortNames, TextInset.testTag("widgets-use-short-names")) { change(settings.copy(useShortNames = it)) }
            }
        }
        if (showWeek) {
            DesignText(tr(message(828)), 13, TextInset)
            DesignSegmented(listOf(tr(message(789)), tr(message(830))), if (settings.calendarWeek) 0 else 1, Modifier.testTag("widgets-week")) {
                change(settings.copy(calendarWeek = it == 0))
            }
        }
        if (showShortNames) {
            // Stundenplan zeigt immer die Kalenderwoche.
            CourseShortNameOptions(ScheduleWidgetModel.visible(written, settings), settings.calendarWeek || size == WidgetSize.GRID, size == WidgetSize.GRID, now, clock.zone(), resolvedShortNames) { uid, name ->
                shortNameStore.set(uid, name)
                shortNames = shortNameStore.load()
            }
        }
        if (showCountdown) CountdownOptions(settings, manual, now, clock.zone(), ::change)

        DesignSection(tr(message(818)), TextInset)
        // Systemaussehen: Ecke vom System, Block wie Systemkarten; manuelle Werte bleiben darunter gespeichert.
        val systemRadius = remember(context) { SystemWidgetLook.radiusDp(context) }
        val follow = settings.followSystem
        DesignCheck(tr(message(900)), follow, TextInset.testTag("widgets-follow-system"), detail = tr(message(901))) { change(settings.copy(followSystem = it)) }
        if (follow) DesignText(tr(systemRadius?.let { message(902, it) } ?: message(903)), 12, TextInset.testTag("widgets-system-radius"), muted = true)
        DesignText(tr(message(819)), 13, TextInset)
        DesignSegmented(listOf(tr(message(820)), tr(message(821))), if (settings.translucent) 1 else 0, Modifier.testTag("widgets-background")) {
            change(settings.copy(translucent = it == 1))
        }
        DesignText(tr(message(822)), 13, TextInset)
        // Manuelle Ecke gilt beim Folgen nur, wenn das System keinen Radius liefert.
        val cornerBySystem = follow && systemRadius != null
        DesignChips(CornerLabels.map { tr(message(it)) }, settings.corner.ordinal, Modifier.testTag("widgets-corner"), enabled = !cornerBySystem) { change(settings.copy(corner = WidgetCorner.entries[it])) }
        if (cornerBySystem) DesignText(tr(message(904)), 12, TextInset, muted = true)

        // Block innerhalb der Zellen: Quadrat oder Breite/Höhe in Prozent (Wunsch: auch im festen 2×2 anpassbar).
        DesignSection(tr(message(882)), TextInset)
        DesignCheck(tr(message(883)), settings.square, TextInset.testTag("widgets-square"), enabled = !follow) { change(settings.copy(square = it)) }
        // Gerätefund: Quadrat in einem länglichen Feld (z. B. 1×2) macht das Aufziehen wirkungslos – darauf hinweisen.
        if (!follow && settings.square && elongated(width, height)) DesignText(tr(message(905)), 12, TextInset.testTag("widgets-square-hint"), color = LocalDesignColors.current.accent)
        DesignSlider({ tr(message(884, it)) }, settings.blockWidthPercent, BlockPercentRange, 5, Modifier.testTag("widgets-block-width"), enabled = !follow) { change(settings.copy(blockWidthPercent = it)) }
        DesignSlider({ tr(message(885, it)) }, settings.blockHeightPercent, BlockPercentRange, 5, Modifier.testTag("widgets-block-height"), enabled = !follow) { change(settings.copy(blockHeightPercent = it)) }
        DesignText(tr(message(if (follow) 904 else 886)), 12, TextInset, muted = true)

        // Hinzufügen nur beim Standard; die Schaltfläche nennt die Art (Startbildschirm-Dialog zeigt nur das App-Symbol).
        if (current == null) {
            DesignPillButton(tr(message(877, size.label())), Modifier.testTag("widgets-pin"), primary = false) {
                pinFeedback = if (ScheduleWidgets.canRequestPin(context) && ScheduleWidgets.requestPin(context, size)) null else message(834)
            }
            pinFeedback?.let { DesignText(tr(it), 12, TextInset.testTag("widgets-pin-feedback"), muted = true) }
        }
    } }
}

/**
 * Kurznamen der Kurse, die das Widget „Woche“ gerade zeigt (nur geschriebene Termine); Eingabe wird sofort gespeichert,
 * die Widgets folgen beim Verlassen der App. Andere Kurse über die Bearbeitungsseite des Kurses.
 */
@Composable
private fun CourseShortNameOptions(
    written: List<WidgetEvent>, calendarWeek: Boolean, grid: Boolean, now: Instant, zone: ZoneId, shortNames: Map<String, String>, onChange: (String, String) -> Unit,
) {
    DesignSection(tr(message(909)), TextInset)
    // Gleiche Woche wie das Widget (Stundenplan am Wochenende ggf. die nächste); je Titel ein Feld.
    val week = if (grid) GridWidgetModel.displayWeek(written, now, zone) else ScheduleWidgetModel.week(written, now, zone, calendarWeek)
    val courses = week.days.flatMap { it.events }.distinctBy { it.title.trim().lowercase() }
    if (courses.isEmpty()) {
        DesignText(tr(message(911)), 12, TextInset.testTag("widgets-short-empty"), muted = true)
        return
    }
    DesignText(tr(message(910)), 12, TextInset, muted = true)
    courses.forEach { event ->
        // Eigener Eingabetext je Feld: gespeichert wird bereinigt, Leerzeichen beim Tippen bleiben sichtbar.
        var text by remember(event.courseId) { mutableStateOf(shortNames[event.courseId].orEmpty()) }
        DesignField(tr(event.title), text, { text = it.take(CourseShortNames.MAX_LENGTH); onChange(event.courseId, text) }, Modifier.testTag("widgets-short-${event.courseId}"))
        ShortNameSuggestions(event.title, text, Modifier.testTag("widgets-suggest-${event.courseId}")) { text = it; onChange(event.courseId, it) }
    }
}

/** Countdown: Arten und angeheftetes Hauptziel (Rückfall auf das nächste, wenn es abläuft oder gelöscht wird). */
@Composable
private fun CountdownOptions(settings: WidgetSettings, manual: List<ManualScheduleItem>, now: Instant, zone: ZoneId, change: (WidgetSettings) -> Unit) {
    DesignSection(tr(message(857)), TextInset)
    Column {
        DesignCheck(tr(message(860)), settings.countdownExams, TextInset.testTag("widgets-countdown-exams")) { change(settings.copy(countdownExams = it)) }
        DesignCheck(tr(message(861)), settings.countdownDeadlines, TextInset.testTag("widgets-countdown-deadlines")) { change(settings.copy(countdownDeadlines = it)) }
    }
    DesignText(tr(message(862)), 13, TextInset)
    val pinnable = CountdownWidgetRenderer.entries(WidgetFrame(emptyList(), true, settings, now, zone, 0, 0, manual))
    Column {
        DesignChoice(tr(message(863)), pinnable.none { it.item.id == settings.countdownPinnedId }, Modifier.testTag("widgets-pin-auto")) {
            change(settings.copy(countdownPinnedId = null))
        }
        pinnable.forEach { entry ->
            DesignChoice(
                tr(entry.item.title), entry.item.id == settings.countdownPinnedId, Modifier.testTag("widgets-pin-${entry.item.id}"),
                detail = tr(message(147, entry.item.kind.label(), entry.remaining.text())),
            ) { change(settings.copy(countdownPinnedId = entry.item.id)) }
        }
    }
    DesignText(tr(message(864)), 12, TextInset, muted = true)
}

/**
 * Vorschau mit denselben RemoteViews wie auf dem Startbildschirm, auf einem zarten Verlauf (macht „durchscheinend“
 * sichtbar). Klickziele der Vorschau sind wirkungslos gestellt, damit sie nicht aus der Einstellungsseite springen.
 */
@Composable
private fun WidgetPreview(size: WidgetSize, frame: WidgetFrame) {
    val colors = LocalDesignColors.current
    val wallpaper = Brush.linearGradient(listOf(colors.accent.copy(alpha = .35f), colors.success.copy(alpha = .25f), colors.wash))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(wallpaper).padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { FrameLayout(it) },
            modifier = Modifier.size(frame.widthDp.dp, frame.heightDp.dp).testTag("widgets-preview"),
            update = { host ->
                host.removeAllViews()
                val view = ScheduleWidgetRenderer.render(host.context, size, frame).apply(host.context, host)
                disableClicks(view)
                host.addView(view)
            },
        )
    }
}

private fun disableClicks(view: android.view.View) {
    view.setOnClickListener(null)
    view.isClickable = false
    if (view is android.view.ViewGroup) (0 until view.childCount).forEach { disableClicks(view.getChildAt(it)) }
}

/** Längliches Feld: lange Seite mindestens 1,6-mal die kurze (dann wirkt „Quadrat“ beim Aufziehen nicht). */
internal fun elongated(widthDp: Int, heightDp: Int): Boolean = minOf(widthDp, heightDp) > 0 && maxOf(widthDp, heightDp) >= 1.6 * minOf(widthDp, heightDp)

private fun previewWidth(size: WidgetSize) = when (size) { WidgetSize.SMALL -> 170; WidgetSize.CHECK -> 85; else -> 330 }
/** Countdown in der Vorschau breit (Hauptziel und die nächsten zwei daneben). */
private fun previewHeight(size: WidgetSize) = when (size) { WidgetSize.SMALL, WidgetSize.COUNTDOWN -> 170; WidgetSize.CHECK -> 105; WidgetSize.MEDIUM, WidgetSize.TOMORROW -> 180; WidgetSize.LARGE -> 340; WidgetSize.GRID -> 290 }

/** Synthetische Prüfungen/Fristen relativ zu jetzt; keine echten Daten. */
private fun sampleCountdown(now: Instant): List<ManualScheduleItem> {
    fun item(n: Int, days: Long, title: String, kind: ManualEventKind) = now.plus(Duration.ofDays(days)).let {
        ManualScheduleItem("sample-countdown-$n", title, it, it.plus(Duration.ofHours(2)), AttendanceState.MUST_ATTEND, kind)
    }
    return listOf(item(1, 12, "Analysis 1", ManualEventKind.EXAM), item(2, 3, "Übungsblatt 5", ManualEventKind.DEADLINE), item(3, 20, "Messtechnik", ManualEventKind.EXAM))
}

/** Synthetische Beispieltermine relativ zu jetzt (laufend, später heute, morgen, Woche); keine echten Daten. */
private fun sampleEvents(now: Instant): List<WidgetEvent> {
    val hour = Duration.ofHours(1)
    fun event(n: Int, from: Instant, minutes: Long, title: String, state: AttendanceState, room: String) =
        WidgetEvent("sample-$n", "sample-$n", title, from, from.plus(Duration.ofMinutes(minutes)), state, room)
    val start = now.minus(Duration.ofMinutes(30))
    return listOf(
        event(1, start, 90, "Analysis 1", AttendanceState.MUST_ATTEND, "Hörsaal 1 (0000.00.001)"),
        event(2, now.plus(hour.multipliedBy(2)), 90, "Messtechnik", AttendanceState.CHOOSE_TO_ATTEND, "Seminarraum (0000.01.110)"),
        event(3, now.plus(hour.multipliedBy(4)), 60, "Physik Übung", AttendanceState.STOP_ATTENDING, "Raum 0000.02.120"),
        event(4, now.plus(hour.multipliedBy(22)), 90, "Regelungstechnik", AttendanceState.MUST_ATTEND, "Hörsaal 2 (0000.00.002)"),
        event(5, now.plus(hour.multipliedBy(47)), 90, "Signale und Systeme", AttendanceState.MUST_ATTEND, "Hörsaal 3 (0000.00.003)"),
        event(6, now.plus(hour.multipliedBy(70)), 120, "Praktikum", AttendanceState.CHOOSE_TO_ATTEND, "Labor (0000.U1.010)"),
    ).sortedBy(WidgetEvent::startsAt)
}
