/*
 * Aufgabe: Eine feste Eckenradius-Skala für das neue Erscheinungsbild festlegen.
 * Ein-/Ausgabe: Keine Eingaben -> Radien für Blöcke, Felder, Kacheln und Pillen.
 * Nebenwirkungen: Keine.
 * Nicht zuständig: Superellipsen-Darstellung (übernimmt Miuix über squircleSurface ab Android 13).
 */
package com.tequila.tumcalendarcontrol.ui.design

import androidx.compose.ui.unit.dp

/**
 * Weiche Radiusfamilie, verschachtelt abnehmend; Zwischenwerte um 11–12 dp nur für kleine Kacheln.
 * Miuix-Komponenten erhalten diese Radien und zeichnen sie selbst als Superellipse, sofern unterstützt.
 */
object DesignRadii {
    val Block = 18.dp
    val Field = 14.dp
    val Tile = 12.dp
    val Thumb = 11.dp
    val Pill = 28.dp
    val Navigation = 24.dp
    /** Bestätigungsdialog (wie im bestätigten Entwurf). */
    val Dialog = 28.dp
}
