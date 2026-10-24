/*
 * Aufgabe: „Systemaussehen folgen“ der Widgets: Eckenradius des Systems lesen und den Block „passend zu Systemkarten“
 *   innerhalb der belegten Zellen berechnen (feste Ränder nach Messung auf dem HONOR-Startbildschirm).
 * Ein-/Ausgabe: Systemressourcen, Hersteller, Dichten bzw. Zellgröße in dp -> Radius, Startbildschirm-Maßstab, Blockgeometrie.
 * Nebenwirkungen: Liest den öffentlichen Systemradius, Herstellername und Dichten; startet einmal je Prozess getprop
 *   für ro.sf.lcd_density (keine Berechtigung, keine fremden App-Daten).
 * Nicht zuständig: Einstellungen speichern, Hintergründe wählen oder RemoteViews zeichnen.
 */
package com.tequila.tumcalendarcontrol.widget

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import kotlin.math.roundToInt

object SystemWidgetLook {
    /**
     * Ränder der Systemkarten gegenüber dem Feld, das der Startbildschirm Drittanbieter-Widgets gibt (HONOR-Messung:
     * Karte im Zellfeld links/rechts 19,5 px, oben 62 px, unten 67 px; Drittanbieterfeld je Seite 12 px; 1 dp = 2,55 px).
     * Fest in dp statt Prozent: Systemkarten sind bei zwei wie drei Zeilen oben und unten zusammen etwa 41 dp kleiner.
     */
    const val SIDE_DP = 3f
    const val TOP_DP = 19.5f
    const val BOTTOM_DP = 21.5f

    /** Untergrenze des Blocks je Achse (Anteil des Feldes), damit kleine Felder wie 1×1 lesbar bleiben. */
    const val MIN_FRACTION = .6f

    /**
     * Vergrößerung, mit der der Startbildschirm unsere Widgets zeichnet. Gerätefund HONOR (Außenbildschirm, Systemeinstellung
     * „Anzeigegröße“ 408 dpi, Gerätedichte 480 dpi): Drittanbieter-Widgets werden in 408-dp gezeichnet und dann als Ganzes
     * um 480/408 ≈ 1,18 vergrößert (14 dp Innenabstand = 42 px statt 36 px, 50 px Rand = 58 px). Die gemeldeten Größen
     * gelten in diesem vergrößerten Maß, Prozentränder stimmen daher von selbst; nur in Bildschirm-dp gemessene Werte
     * (Ränder und Radius der Systemkarten) müssen geteilt werden. Nur für HONOR/HUAWEI angenommen, sonst 1.
     */
    fun launcherScale(context: Context): Float =
        launcherScale(Build.MANUFACTURER.orEmpty(), context.resources.displayMetrics.densityDpi, deviceDensityDpi)

    /** Reine Berechnung (testbar): Verhältnis Gerätedichte / aktuelle Dichte, auf 0,5–2 begrenzt. */
    internal fun launcherScale(manufacturer: String, currentDpi: Int, deviceDpi: Int): Float {
        if (manufacturer.lowercase() !in ScalingLaunchers || currentDpi <= 0 || deviceDpi <= 0) return 1f
        return (deviceDpi.toFloat() / currentDpi).coerceIn(.5f, 2f)
    }

    private val ScalingLaunchers = setOf("honor", "huawei")

    /**
     * Gerätedichte ohne die Einstellung „Anzeigegröße“: öffentliche Systemeigenschaft ro.sf.lcd_density über das
     * Werkzeug getprop (HONOR liefert der App als DENSITY_DEVICE_STABLE bereits den eingestellten Wert). Einmal je Prozess;
     * bei Fehler der stabile Wert (Maßstab dann 1).
     */
    private val deviceDensityDpi: Int by lazy {
        runCatching {
            ProcessBuilder("getprop", "ro.sf.lcd_density").start().inputStream.bufferedReader().use { it.readText().trim().toInt() }
        }.getOrNull()?.takeIf { it > 0 } ?: DisplayMetrics.DENSITY_DEVICE_STABLE
    }

    /** Systemradius in dp (Android 12+, Startbildschirm kann ihn überschreiben); null = nicht verfügbar → manuelle Ecke. */
    fun radiusDp(context: Context): Int? = radiusDp(Build.VERSION.SDK_INT, context.resources.displayMetrics.density) {
        context.resources.getDimension(android.R.dimen.system_app_widget_background_radius)
    }

    /**
     * Reine Umrechnung (testbar): unter API 31, bei Lesefehler oder 0 kein Wert; sonst auf ganze dp gerundet und auf die
     * vorhandenen Hintergründe (0–32 dp) begrenzt.
     */
    internal fun radiusDp(sdk: Int, density: Float, readPx: () -> Float): Int? {
        if (sdk < Build.VERSION_CODES.S || density <= 0f) return null
        val dp = runCatching(readPx).getOrNull()?.div(density)?.roundToInt() ?: return null
        return dp.takeIf { it > 0 }?.coerceAtMost(WidgetBackgroundTable.MAX_RADIUS_DP)
    }

    /**
     * Block „passend zu Systemkarten“: feste Ränder je Seite; wird ein Rand zu groß (unter 60 % übrig), wird der Block
     * auf 60 % begrenzt und der Rest im selben Verhältnis (oben:unten) verteilt.
     */
    fun preset(widthDp: Int, heightDp: Int): ScheduleWidgetRenderer.BlockGeometry {
        val (left, right) = axis(widthDp, SIDE_DP, SIDE_DP)
        val (top, bottom) = axis(heightDp, TOP_DP, BOTTOM_DP)
        return ScheduleWidgetRenderer.BlockGeometry((widthDp - left - right).roundToInt(), (heightDp - top - bottom).roundToInt(), left, top, right, bottom, screenUnits = true)
    }

    private fun axis(length: Int, start: Float, end: Float): Pair<Float, Float> {
        val maxInset = length * (1 - MIN_FRACTION)
        if (start + end <= maxInset) return start to end
        val scale = maxInset / (start + end)
        return start * scale to end * scale
    }
}
