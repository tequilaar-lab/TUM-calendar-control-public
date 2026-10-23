/*
 * Aufgabe: Haptische Rückmeldung des Designsystems: Ereignisarten, drei Stärken, Ein/Aus und Zuordnung zu Systemeffekten.
 * Ein-/Ausgabe: Ereignis und Einstellung -> Konstante für View.performHapticFeedback (oder nichts, wenn aus).
 * Nebenwirkungen: Nur kurze Vibration über die Ansicht; keine Berechtigung, folgt der System-Haptikeinstellung.
 * Nicht zuständig: Speicherung (HapticPreferences), Auslösen fachlicher Aktionen.
 */
package com.tequila.tumcalendarcontrol.ui.design

import com.tequila.tumcalendarcontrol.i18n.message

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role

/** Stärke der Rückmeldung; Reihenfolge = Anzeige. */
enum class HapticLevel(val label: String) { LIGHT(message(743)), STANDARD(message(689)), STRONG(message(745)) }

/** Anlass: Auswahl (leicht), Aktion, Bestätigung, gefährliche Bestätigung. */
enum class HapticEvent { SELECTION, ACTION, CONFIRM, REJECT }

data class HapticSettings(val enabled: Boolean = true, val level: HapticLevel = HapticLevel.STANDARD)

/**
 * Zuordnung zu Systemeffekten: je Stufe ein spürbar stärkerer Effekt; Bestätigen/Ablehnen ab Android 11 mit eigenen
 * Mustern (außer bei „leicht“), davor wie eine Aktion.
 */
fun hapticConstant(event: HapticEvent, level: HapticLevel, sdk: Int = Build.VERSION.SDK_INT): Int {
    val selection = when (level) {
        HapticLevel.LIGHT -> HapticFeedbackConstants.CLOCK_TICK
        HapticLevel.STANDARD -> HapticFeedbackConstants.KEYBOARD_TAP
        HapticLevel.STRONG -> HapticFeedbackConstants.CONTEXT_CLICK
    }
    val action = when (level) {
        HapticLevel.LIGHT -> HapticFeedbackConstants.KEYBOARD_TAP
        HapticLevel.STANDARD -> HapticFeedbackConstants.CONTEXT_CLICK
        HapticLevel.STRONG -> HapticFeedbackConstants.LONG_PRESS
    }
    val patterned = sdk >= Build.VERSION_CODES.R && level != HapticLevel.LIGHT
    return when (event) {
        HapticEvent.SELECTION -> selection
        HapticEvent.ACTION -> action
        HapticEvent.CONFIRM -> if (patterned) HapticFeedbackConstants.CONFIRM else action
        HapticEvent.REJECT -> if (patterned) HapticFeedbackConstants.REJECT else action
    }
}

/** Ausführender Teil: nur wenn eingeschaltet; performer erhält die Systemkonstante. */
class DesignHaptics(val settings: HapticSettings, private val performer: (Int) -> Unit) {
    fun perform(event: HapticEvent) {
        if (settings.enabled) performer(hapticConstant(event, settings.level))
    }

    /** Vorschau einer Stufe beim Umstellen, unabhängig vom gespeicherten Zustand. */
    fun preview(level: HapticLevel) = performer(hapticConstant(HapticEvent.ACTION, level))
}

/** Standard ohne Wirkung (Bildtests, Vorschauen); die App stellt die echte Rückmeldung bereit. */
val LocalDesignHaptics = staticCompositionLocalOf { DesignHaptics(HapticSettings(enabled = false)) {} }

/** Einstellung und Änderungsrückruf für die Darstellungsseite (analog zur Glasauswahl). */
data class HapticSelection(val settings: HapticSettings, val change: (HapticSettings) -> Unit)

val LocalHapticSelection = staticCompositionLocalOf { HapticSelection(HapticSettings()) {} }

/** Echte Rückmeldung über die aktuelle Ansicht. */
@Composable
fun rememberViewHaptics(settings: HapticSettings): DesignHaptics {
    val view = LocalView.current
    return remember(view, settings) { DesignHaptics(settings) { view.performHapticFeedback(it) } }
}

/** Eigene klickbare Fläche mit Rückmeldung (Standard: Auswahl); Druck als abgerundeter Schwebeblock (designPressable). */
fun Modifier.hapticClickable(event: HapticEvent = HapticEvent.SELECTION, onClick: () -> Unit): Modifier = designPressable(event, onClick = onClick)

/** Auswählbare Fläche mit Auswahlrückmeldung; erneutes Antippen des gewählten Elements bleibt still. */
fun Modifier.hapticSelectable(selected: Boolean, role: Role, onClick: () -> Unit): Modifier = composed {
    val haptics = LocalDesignHaptics.current
    selectable(selected, role = role) { if (!selected) haptics.perform(HapticEvent.SELECTION); onClick() }
}
