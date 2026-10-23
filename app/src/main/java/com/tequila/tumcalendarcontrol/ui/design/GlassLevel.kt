/*
 * Aufgabe: Vier Glasstufen und ihre geräteabhängige Abschwächung definieren.
 * Ein-/Ausgabe: Gewählte Stufe und Android-API-Stufe -> wirksame Glasparameter (Tönung, Unschärfe, Linsenbrechung).
 * Nebenwirkungen: Keine; reine Berechnung.
 * Nicht zuständig: Speicherung (GlassPreferences) oder Zeichnen (GlassSurfaces).
 */
package com.tequila.tumcalendarcontrol.ui.design

import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf
import com.tequila.tumcalendarcontrol.i18n.message

/**
 * Tönung: Anteil der Flächenfarbe über dem unscharfen Hintergrund; niedriger = durchsichtiger.
 * „Aus“ entspricht „Transparenz reduzieren“: deckende Flächen, Hauptschaltfläche wieder vollfarbig.
 */
enum class GlassLevel(val label: String, val tint: Float, val blurDp: Float, val lensHeightDp: Float, val lensAmountDp: Float) {
    OFF(message(687), 1f, 0f, 0f, 0f),
    SOFT(message(688), .72f, 14f, 0f, 0f),
    STANDARD(message(689), .46f, 12f, 12f, 24f),
    CLEAR(message(690), .24f, 8f, 16f, 32f),
}

/** Was das Gerät darstellen kann: Unschärfe ab Android 12 (API 31), Linsenbrechung ab Android 13 (API 33). */
enum class GlassSupport {
    NONE, BLUR_ONLY, FULL;

    companion object {
        fun forSdk(sdk: Int): GlassSupport = when {
            sdk >= 33 -> FULL
            sdk >= 31 -> BLUR_ONLY
            else -> NONE
        }
    }
}

/** Tatsächlich gezeichnete Parameter nach Abschwächung; enabled=false bedeutet deckende Fläche. */
data class GlassSpec(val enabled: Boolean, val tint: Float, val blurDp: Float, val lensHeightDp: Float, val lensAmountDp: Float)

/**
 * Abschwächung: ohne Unterstützung immer deckend; nur Unschärfe -> Linse entfällt, Tönung wie gewählt.
 * Die Wahl selbst bleibt gespeichert, damit sie auf einem neueren Gerät wieder vollständig wirkt.
 */
fun glassSpec(level: GlassLevel, support: GlassSupport): GlassSpec = when {
    level == GlassLevel.OFF || support == GlassSupport.NONE -> GlassSpec(false, 1f, 0f, 0f, 0f)
    support == GlassSupport.BLUR_ONLY -> GlassSpec(true, level.tint, level.blurDp, 0f, 0f)
    else -> GlassSpec(true, level.tint, level.blurDp, level.lensHeightDp, level.lensAmountDp)
}

val LocalGlassLevel = staticCompositionLocalOf { GlassLevel.STANDARD }

/** Aktuelle Glasstufe und Auswahlrückruf (App speichert; Bildtests ohne Wirkung), analog zur Sprachauswahl. */
data class GlassSelection(val level: GlassLevel, val select: (GlassLevel) -> Unit)

val LocalGlassSelection = staticCompositionLocalOf { GlassSelection(GlassLevel.STANDARD) {} }
val LocalGlassSupport = staticCompositionLocalOf { GlassSupport.forSdk(Build.VERSION.SDK_INT) }
