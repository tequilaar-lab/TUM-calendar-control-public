/*
 * Aufgabe: Kopfleiste, Hauptseitenanimation, schwebende Navigation und Aktionsüberlagerung rendern.
 * Ein-/Ausgabe: Navigation und bestehender Zustand -> fester Compose-Rahmen mit Animationen.
 * Nebenwirkungen: Keine externen Aktionen; reine Berechnung/Darstellung oder bestehende Aufrufrückmeldung.
 * Nicht zuständig: Android-Berechtigungs-/Datei-/Netzwerkkoordination, iCal-Lesen, Google-Autorisierung oder Kalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.ui.platform.testTag

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar

private val FixedTopBarHeight = 136.dp
private val FloatingNavigationShape = RoundedCornerShape(30.dp)
/** Nur am Ende scrollbarer Inhalte; kein äußeres Viewport-Padding. Systemsicherheitsbereich getrennt. */
internal val FloatingNavigationContentClearance = 100.dp

/** Keine funktionslose weitere Aktion im Hauptseitenrahmen. */
@Composable
internal fun CalendarControlChrome(state: CalendarControlState) {
    CalendarControlScaffold(state)
}

@Composable
private fun CalendarControlScaffold(state: CalendarControlState) {
    val savedPages = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val contentScreen = state.contentScreen
    val showsFloatingNavigation = contentScreen.usesBottomNavigation()
    Scaffold(
        topBar = {
            FixedAppTopBar(
                title = contentScreen.title(),
                subtitle = contentScreen.subtitle() ?: "\u00A0",
                navigationIcon = {
                    if (contentScreen.usesBackArrow()) {
                        TactileIconButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = tr(message(138)),
                            onClick = state::handleBack,
                        )
                    } else {
                        TactileProfileButton(
                            profile = state.profile,
                            onClick = state::openProfile,
                        )
                    }
                },
                actions = {},
            )
        },
        // Navigation über Inhalt legen; keine breite dunkle Scaffold-Fläche oder Höhenänderung bei Profilrückkehr.
        bottomBar = {},
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    PaddingValues(
                        start = 16.dp,
                        top = innerPadding.calculateTopPadding() + 16.dp,
                        end = 16.dp,
                        // Scaffold berücksichtigt Systemnavigation; kein zusätzliches navigationBarsPadding für schwebende Navigation.
                        bottom = innerPadding.calculateBottomPadding() + 8.dp,
                    ),
                ),
        ) {
            Box(
                modifier = Modifier
                    // Vollständiger Viewport; Navigationsabstand nur am Inhaltende, niemals gesamte Seite kürzen.
                    .fillMaxSize(),
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
            if (showsFloatingNavigation) {
                AppBottomNavigation(
                    state = state,
                    selectedScreen = contentScreen,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/** Statusleistenabstand und feste Kopfhöhe reservieren; Symbole und Untertitel überlappen nicht. */
@Composable
private fun FixedAppTopBar(
    title: String,
    subtitle: String,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(FixedTopBarHeight)
            .statusBarsPadding(),
    ) {
        TopAppBar(
            title = tr(title),
            subtitle = tr(subtitle),
            navigationIcon = navigationIcon,
            actions = actions,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun AppBottomNavigation(
    state: CalendarControlState,
    selectedScreen: AppScreen,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTumPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("floating-navigation")
            .shadow(12.dp, FloatingNavigationShape)
            .clip(FloatingNavigationShape)
            .background(palette.surfaceContainer)
            .border(1.dp, palette.outline.copy(alpha = 0.35f), FloatingNavigationShape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val itemWidth = maxWidth / 4
            val selectedOffset = animateDpAsState(
                targetValue = itemWidth * selectedScreen.bottomNavigationIndex(),
                label = message(140),
            ).value
            Box(
                modifier = Modifier
                    .offset(x = selectedOffset)
                    .width(itemWidth)
                    .height(60.dp)
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(palette.primaryContainer.copy(alpha = 0.82f)),
            )
            Box(Modifier.fillMaxWidth()) {
                // Vier gleiche Slots und Indikator teilen itemWidth; keine Ausrichtungsverschiebung.
                TactileNavigationItem(
                    Icons.Rounded.Home,
                    message(141),
                    selectedScreen == AppScreen.OVERVIEW,
                    onClick = { state.open(AppScreen.OVERVIEW) },
                    modifier = Modifier.width(itemWidth).offset(x = itemWidth * 0),
                )
                TactileNavigationItem(
                    Icons.Rounded.Search,
                    message(142),
                    selectedScreen == AppScreen.SEARCH,
                    onClick = { state.open(AppScreen.SEARCH) },
                    modifier = Modifier.width(itemWidth).offset(x = itemWidth * 1),
                )
                TactileNavigationItem(
                    Icons.Rounded.Favorite,
                    message(143),
                    selectedScreen == AppScreen.FAVORITES,
                    onClick = { state.open(AppScreen.FAVORITES) },
                    modifier = Modifier.width(itemWidth).offset(x = itemWidth * 2),
                )
                TactileNavigationItem(
                    Icons.Rounded.Settings,
                    message(144),
                    selectedScreen == AppScreen.SETTINGS,
                    onClick = { state.open(AppScreen.SETTINGS) },
                    modifier = Modifier.width(itemWidth).offset(x = itemWidth * 3),
                )
            }
        }
    }
}

/** Weitere Aktionen als eigene vollständige Überlagerung; getrennt von Navigationsanimation. */
@Composable
private fun MoreOverlay(state: CalendarControlState) {
    val palette = LocalTumPalette.current
    AnimatedVisibility(
        visible = state.isMoreOpen,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.surface),
        ) {
            FixedAppTopBar(
                title = AppScreen.MORE.title(),
                subtitle = "\u00A0",
                navigationIcon = {
                    TactileIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = tr(message(145)),
                        onClick = state::handleBack,
                    )
                },
                actions = {},
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                MoreScreen(state)
            }
        }
    }
}
