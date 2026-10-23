/*
 * Aufgabe: Gemeinsame Bewegungsregeln des Designsystems: Dauern, Kurven und Übergänge für Seiten, Kopf, Dialoge und Auswahl.
 * Ein-/Ausgabe: Keine Eingaben -> Animationsspezifikationen und Hilfen (z. B. animierte Auswahlfarbe).
 * Nebenwirkungen: Keine.
 * Nicht zuständig: Haptik (DesignHaptics), Inhalte oder Navigation.
 *
 * Regeln: Hereinkommen mit Abbremskurve (240–320 ms), Hinausgehen schneller (150–200 ms); Richtung folgt der
 * Hierarchie (vorwärts nach links, zurück nach rechts); Auswahlfarben gleiten in ~180 ms statt zu springen.
 */
package com.tequila.tumcalendarcontrol.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color

object DesignMotion {
    const val ENTER_MS = 300
    const val EXIT_MS = 180
    const val SELECT_MS = 180

    fun <T> enter() = tween<T>(ENTER_MS, easing = LinearOutSlowInEasing)
    fun <T> exit() = tween<T>(EXIT_MS, easing = FastOutSlowInEasing)
    fun <T> select() = tween<T>(SELECT_MS, easing = FastOutSlowInEasing)

    /** Kopftexte erscheinen leicht verzögert, damit sie mit dem Seiteninhalt statt vor ihm wechseln. */
    fun <T> headerIn() = tween<T>(240, delayMillis = 60, easing = LinearOutSlowInEasing)
    fun <T> headerOut() = tween<T>(120, easing = FastOutSlowInEasing)

    /** Dialogfläche: kritisch gedämpfte Feder ohne sichtbares Nachschwingen. */
    fun <T> dialog() = spring<T>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
}

/** Auswahlfarbe gleitet statt zu springen. */
@Composable
fun animatedSelectionColor(selected: Boolean, on: Color, off: Color, label: String): State<Color> =
    animateColorAsState(if (selected) on else off, DesignMotion.select(), label = label)
