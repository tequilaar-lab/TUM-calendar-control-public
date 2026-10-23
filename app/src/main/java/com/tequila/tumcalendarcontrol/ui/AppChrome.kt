/*
 * Aufgabe: Seitenrahmen rendern: Kopf (Marke, gläserne Zurück-Schaltfläche oder Profil, englische Kleinbeschriftung, Titel),
 *   Hauptseitenanimation, Inhaltsebene als Glasquelle und schwebende Glasnavigation.
 * Ein-/Ausgabe: Navigation und bestehender Zustand -> fester Compose-Rahmen mit Animationen.
 * Nebenwirkungen: Keine externen Aktionen; reine Darstellung und bestehende Aufrufrückmeldung.
 * Nicht zuständig: Seiteninhalte, Android-Berechtigungs-/Datei-/Netzwerkkoordination, iCal-Lesen, Google-Autorisierung oder Kalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import com.tequila.tumcalendarcontrol.ui.design.DesignMotion
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.tequila.tumcalendarcontrol.ui.design.DesignGlassNavigation
import com.tequila.tumcalendarcontrol.ui.design.DesignNavigationItem
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors
import com.tequila.tumcalendarcontrol.ui.design.DesignConfirmHost
import com.tequila.tumcalendarcontrol.ui.design.LocalGlassBackdrop
import com.tequila.tumcalendarcontrol.ui.design.designGlow
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold

/** Nur am Ende scrollbarer Inhalte; kein äußeres Viewport-Padding. Systemsicherheitsbereich getrennt. */
internal val FloatingNavigationContentClearance = 100.dp

/** Keine funktionslose weitere Aktion im Hauptseitenrahmen. */
@Composable
internal fun CalendarControlChrome(state: CalendarControlState) {
    // Bestätigungsdialoge der Seiten legen sich als Glas über den ganzen Rahmen (Kopf, Inhalt, Navigation).
    DesignConfirmHost { CalendarControlScaffold(state) }
}

@Composable
private fun CalendarControlScaffold(state: CalendarControlState) {
    val savedPages = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val contentScreen = state.contentScreen
    val colors = LocalDesignColors.current
    // Inhaltsebene: schwebende Navigation tastet sie für Unschärfe und Brechung ab.
    val contentLayer = rememberLayerBackdrop()
    Scaffold(
        topBar = {
            AppPageHeader(
                kicker = if (contentScreen == AppScreen.OVERVIEW) homeSemesterKicker(state.displayedCourses) else contentScreen.kicker(),
                // Untertitel entfallen gemäß bestätigtem Entwurf; die Übersicht zeigt ihre Listenüberschrift als Titel.
                title = tr(contentScreen.pageTitle(state)),
                navigation = {
                    // Zurück erscheint/verschwindet mit Skalierung; die Breite gleitet, damit „TUM“ nicht springt.
                    // 48-dp-Tippfläche, um 4 dp nach links versetzt, damit der sichtbare 40-dp-Kreis bündig bleibt.
                    AnimatedContent(
                        targetState = contentScreen.usesBackArrow(),
                        transitionSpec = {
                            // Ohne Beschnitt, sonst wird der weiche Schatten des Knopfes während der Größenänderung abgeschnitten.
                            ((fadeIn(DesignMotion.enter()) + scaleIn(DesignMotion.enter(), initialScale = .8f)) togetherWith
                                (fadeOut(DesignMotion.exit()) + scaleOut(DesignMotion.exit(), targetScale = .8f))) using SizeTransform(clip = false)
                        },
                        label = "header-back",
                    ) { back ->
                        if (back) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlassBackButton(onClick = state::handleBack)
                                Spacer(Modifier.width(4.dp))
                            }
                        } else {
                            // Hauptseiten: Marke bündig mit dem Titel (wie im bestätigten Bild), kein Platzhalter.
                            Spacer(Modifier.width(0.dp))
                        }
                    }
                },
                trailing = {
                    AnimatedVisibility(
                        visible = !contentScreen.usesBackArrow(),
                        enter = fadeIn(DesignMotion.enter()) + scaleIn(DesignMotion.enter(), initialScale = .8f),
                        exit = fadeOut(DesignMotion.exit()) + scaleOut(DesignMotion.exit(), targetScale = .8f),
                    ) { TactileProfileButton(profile = state.profile, onClick = state::openProfile) }
                },
            )
        },
        // Navigation über Inhalt legen; keine breite dunkle Scaffold-Fläche oder Höhenänderung bei Profilrückkehr.
        bottomBar = {},
    ) { innerPadding ->
        CompositionLocalProvider(LocalGlassBackdrop provides contentLayer) {
            Box(Modifier.fillMaxSize().background(colors.paper)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(contentLayer)
                        .background(colors.paper)
                        .padding(
                            PaddingValues(
                                start = 16.dp,
                                top = innerPadding.calculateTopPadding() + 8.dp,
                                end = 16.dp,
                                // Scaffold berücksichtigt Systemnavigation; Navigationsabstand nur am Inhaltsende.
                                bottom = innerPadding.calculateBottomPadding(),
                            ),
                        ),
                ) {
                    AnimatedContent(
                        targetState = contentScreen,
                        transitionSpec = {
                            // Hereinkommen mit Abbremskurve, alte Seite schneller ausblenden (DesignMotion).
                            val enter = DesignMotion.enter<androidx.compose.ui.unit.IntOffset>()
                            val leave = DesignMotion.exit<androidx.compose.ui.unit.IntOffset>()
                            when (state.navigationDirection) {
                                NavigationDirection.FORWARD -> (slideInHorizontally(enter) { it } + fadeIn(DesignMotion.enter())) togetherWith (slideOutHorizontally(leave) { -it / 3 } + fadeOut(DesignMotion.exit()))
                                NavigationDirection.BACKWARD -> (slideInHorizontally(enter) { -it / 3 } + fadeIn(DesignMotion.enter())) togetherWith (slideOutHorizontally(leave) { it } + fadeOut(DesignMotion.exit()))
                                NavigationDirection.PROFILE_ENTER -> (slideInHorizontally(enter) { -it } + fadeIn(DesignMotion.enter())) togetherWith (slideOutHorizontally(leave) { it / 3 } + fadeOut(DesignMotion.exit()))
                                // Profil nach links verlassen, vorige Hauptseite vollständig von rechts einblenden; keine vertikale Neuordnung.
                                NavigationDirection.PROFILE_EXIT -> (slideInHorizontally(enter) { it } + fadeIn(DesignMotion.enter())) togetherWith (slideOutHorizontally(leave) { -it / 3 } + fadeOut(DesignMotion.exit()))
                            }
                        },
                        label = message(139),
                    ) { screen ->
                        savedPages.SaveableStateProvider(screen.name) { AppScreenContent(state, screen) }
                    }
                }
                // Scrollkante: Inhalt läuft unter dem Kopf weich aus statt hart abgeschnitten.
                Box(
                    Modifier.align(Alignment.TopCenter).padding(top = innerPadding.calculateTopPadding()).fillMaxWidth().height(14.dp)
                        .background(Brush.verticalGradient(listOf(colors.paper, colors.paper.copy(alpha = 0f)))),
                )
                // Navigation gleitet nach unten weg bzw. herein; beim Ausblenden bleibt das zuletzt gewählte Ziel markiert.
                val navigationScreen = remember { mutableStateOf(contentScreen) }
                if (contentScreen.usesBottomNavigation()) navigationScreen.value = contentScreen
                AnimatedVisibility(
                    visible = contentScreen.usesBottomNavigation(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(DesignMotion.enter()) { it / 2 } + fadeIn(DesignMotion.enter()),
                    exit = slideOutVertically(DesignMotion.exit()) { it / 2 } + fadeOut(DesignMotion.exit()),
                ) { AppBottomNavigation(state, navigationScreen.value) }
            }
        }
    }
}

/** Titel je Seite; die Übersicht nennt ihre Liste, weil die Marke bereits in der Kopfzeile steht. */
private fun AppScreen.pageTitle(state: CalendarControlState): String = when (this) {
    AppScreen.OVERVIEW -> if (state.sourceCatalogLoaded) message(333) else message(334)
    else -> title()
}

/**
 * Kopf wie im bestätigten Entwurf: Markenzeile (TUM fett im Akzent, Produktname gedämpft) mit Zurück links oder Profil rechts,
 * darunter englische Kleinbeschriftung und großer Titel. Gleiche Zeilenhöhe mit/ohne Zurück, damit Titel nicht springen.
 */
@Composable
private fun AppPageHeader(
    kicker: String,
    title: String,
    navigation: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val colors = LocalDesignColors.current
    Column(Modifier.fillMaxWidth().background(colors.paper).statusBarsPadding().padding(start = 16.dp, end = 20.dp, top = 6.dp, bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            navigation()
            Spacer(Modifier.width(12.dp))
            DesignText("TUM", 15, weight = FontWeight.Bold, color = colors.accent, tracking = 1f)
            DesignText("  /  " + tr(message(701)), 12, muted = true, modifier = Modifier.weight(1f), maxLines = 1)
            trailing()
        }
        // Kennzeichnung und Titel überblenden beim Seitenwechsel (leicht verzögert, mit dem Inhalt statt vor ihm).
        AnimatedContent(
            targetState = kicker to title,
            transitionSpec = { fadeIn(DesignMotion.headerIn()) togetherWith fadeOut(DesignMotion.headerOut()) },
            label = "header-title",
        ) { (shownKicker, shownTitle) ->
            Column(Modifier.padding(start = 12.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DesignText(shownKicker, 10, muted = true, mono = true, tracking = .8f, modifier = Modifier.testTag("page-kicker"))
                DesignText(shownTitle, 30, maxLines = 2, modifier = Modifier.testTag("page-title"))
            }
        }
    }
}

/**
 * Runde Glas-Zurück-Schaltfläche (Miuix IconButton): Tippfläche 48 dp, sichtbarer heller Kreis 40 dp mit weichem Schatten.
 * Der Kreis sitzt mittig; der Aufrufer gleicht den Versatz aus, damit der Kreis am Seitenrand bündig bleibt.
 */
@Composable
private fun GlassBackButton(onClick: () -> Unit) {
    val colors = LocalDesignColors.current
    val haptics = com.tequila.tumcalendarcontrol.ui.design.LocalDesignHaptics.current
    IconButton(
        onClick = { haptics.perform(com.tequila.tumcalendarcontrol.ui.design.HapticEvent.SELECTION); onClick() },
        modifier = Modifier.offset(x = (-4).dp).testTag("back-button"),
        backgroundColor = Color.Transparent,
        minHeight = 48.dp,
        minWidth = 48.dp,
    ) {
        Box(Modifier.size(40.dp).designGlow(20.dp, colors, strength = .7f).background(colors.floating, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr(message(138)), tint = colors.ink, modifier = Modifier.size(20.dp))
        }
    }
}

/** Vier Hauptziele als Glasnavigation (Miuix FloatingNavigationBar); Beschriftungen lokalisiert. */
@Composable
private fun AppBottomNavigation(state: CalendarControlState, selectedScreen: AppScreen, modifier: Modifier = Modifier) {
    val targets = listOf(AppScreen.OVERVIEW, AppScreen.SEARCH, AppScreen.FAVORITES, AppScreen.SETTINGS)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        DesignGlassNavigation(
            items = listOf(
                DesignNavigationItem(Icons.Rounded.Home, tr(message(141))),
                DesignNavigationItem(Icons.Rounded.Search, tr(message(142))),
                DesignNavigationItem(Icons.Rounded.FavoriteBorder, tr(message(143))),
                DesignNavigationItem(Icons.Rounded.Settings, tr(message(144))),
            ),
            selected = selectedScreen.bottomNavigationIndex(),
            modifier = Modifier.testTag("floating-navigation"),
            onSelect = { index -> state.open(targets[index]) },
        )
    }
}
