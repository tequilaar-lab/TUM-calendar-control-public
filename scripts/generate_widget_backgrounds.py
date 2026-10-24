#!/usr/bin/env python3
"""Aufgabe: Widget-Hintergründe je Eckenradius (0–32 dp, deckend und durchscheinend) und die Kotlin-Zuordnungstabelle erzeugen.
Ein-/Ausgabe: keine Eingabe -> res/drawable/widget_bg_{solid,glass}_r{0..32}.xml und widget/WidgetBackgroundTable.kt.
Nebenwirkungen: ausschließlich diese generierten Dateien überschreiben.
Nicht zuständig: Farben (res/values*/colors.xml), Auswahl des Radius oder Widget-Darstellung.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DRAWABLES = ROOT / "app/src/main/res/drawable"
TABLE = ROOT / "app/src/main/java/com/tequila/tumcalendarcontrol/widget/WidgetBackgroundTable.kt"
MAX_RADIUS = 32
# Art -> (Farbressource, Beschreibung im Dateikopf)
KINDS = {"solid": ("widget_surface", "deckend"), "glass": ("widget_surface_glass", "durchscheinend")}

SHAPE = """<!-- Automatisch erzeugt (scripts/generate_widget_backgrounds.py); nicht von Hand bearbeiten.
     Aufgabe: Widget-Hintergrund ({label}, Ecken {radius} dp); reine Darstellungsressource ohne Daten oder Aktionen. -->
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/{color}" />
    <corners android:radius="{radius}dp" />
    <stroke android:width="1dp" android:color="@color/widget_rim" />
</shape>
"""

TABLE_HEADER = """/* Automatisch erzeugt (scripts/generate_widget_backgrounds.py); nicht von Hand bearbeiten.
 * Aufgabe: Hintergrundressourcen der Widgets nach Eckenradius in dp (Index = Radius 0–{max}).
 * Ein-/Ausgabe: keine Eingabe -> Ressourcen-IDs; keine Nebenwirkungen.
 * Nicht zuständig: Wahl des Radius (ScheduleWidgetRenderer.background) oder Darstellung.
 */
package com.tequila.tumcalendarcontrol.widget

import com.tequila.tumcalendarcontrol.R

internal object WidgetBackgroundTable {
    const val MAX_RADIUS_DP = {max}
"""


def main():
    # Ressourcen: je Art und Radius eine Form mit derselben Randlinie wie bisher.
    for kind, (color, label) in KINDS.items():
        for radius in range(MAX_RADIUS + 1):
            (DRAWABLES / f"widget_bg_{kind}_r{radius}.xml").write_text(SHAPE.format(label=label, radius=radius, color=color), encoding="utf-8")
    # Tabelle: Index = Radius, damit die Auswahl ohne Namenssuche zur Laufzeit auskommt.
    lines = [TABLE_HEADER.replace("{max}", str(MAX_RADIUS))]
    for kind in KINDS:
        lines.append(f"    val {kind}: IntArray = intArrayOf(\n")
        for radius in range(MAX_RADIUS + 1):
            lines.append(f"        R.drawable.widget_bg_{kind}_r{radius},\n")
        lines.append("    )\n")
    lines.append("}\n")
    TABLE.write_text("".join(lines), encoding="utf-8")


if __name__ == "__main__":
    main()
