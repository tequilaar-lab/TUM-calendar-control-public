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
                    // Zurück: 48-dp-Tippfläche, um 4 dp nach links versetzt, damit der sichtbare 40-dp-Kreis bündig bleibt.
                    if (contentScreen.usesBackArrow()) {
                        GlassBackButton(onClick = state::handleBack)
                        Spacer(Modifier.width(4.dp))
                    } else {
                                }
                },
                trailing = {
                    if (!contentScreen.usesBackArrow()) TactileProfileButton(profile = state.profile, onClick = state::openProfile)
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
                            when (state.navigationDirection) {
                                NavigationDirection.FORWARD -> (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                                NavigationDirection.BACKWARD -> (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                                NavigationDirection.PROFILE_ENTER -> (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                                // Profil nach links verlassen, vorige Hauptseite vollständig von rechts einblenden; keine vertikale Neuordnung.
                                NavigationDirection.PROFILE_EXIT -> (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
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
                if (contentScreen.usesBottomNavigation()) {
                    AppBottomNavigation(state, contentScreen, Modifier.align(Alignment.BottomCenter))
                }
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
        Column(Modifier.padding(start = 12.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DesignText(kicker, 10, muted = true, mono = true, tracking = .8f, modifier = Modifier.testTag("page-kicker"))
            DesignText(title, 30, maxLines = 2, modifier = Modifier.testTag("page-title"))
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
    IconButton(
        onClick = onClick,
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
