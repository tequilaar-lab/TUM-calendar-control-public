#!/usr/bin/env python3
"""Aufgabe: kanonischen Kotlin-Katalog aus chinesischen Sprachressourcen erzeugen.
Ein-/Ausgabe: values-zh/strings.xml -> i18n/MessageSources.kt.
Nebenwirkungen: ausschließlich diese generierte lokale Quelldatei überschreiben.
Nicht zuständig: Übersetzungen, IDs, Anwendungslogik oder Veröffentlichung.
"""
from pathlib import Path
import json
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/res/values-zh/strings.xml"
TARGET = ROOT / "app/src/main/java/com/tequila/tumcalendarcontrol/i18n/MessageSources.kt"
HEADER = """/* Automatisch aus dem Nachrichtenkatalog erzeugt; nicht von Hand bearbeiten.
 * Aufgabe: kanonische Nachrichten für bestehende String-Verträge bereitstellen.
 * Ein-/Ausgabe: Nachrichtenschlüssel -> chinesische Vorlage; keine Nebenwirkungen.
 * Nicht zuständig: Anzeigeübersetzung, Benutzerdaten oder Synchronisierung.
 */
package com.tequila.tumcalendarcontrol.i18n

internal val messageSources = mapOf(
"""


def canonical_text(element):
    value = "".join(element.itertext())
    if value.startswith('"') and value.endswith('"'):
        value = value[1:-1]
    return re.sub(r"\\([\\\"'nt])", lambda match: {"n": "\n", "t": "\t"}.get(match[1], match[1]), value)


def main():
    entries = []
    for element in ET.parse(SOURCE).getroot().findall("string"):
        name = element.get("name", "")
        if not re.fullmatch(r"msg_\d{4}", name):
            continue
        text = json.dumps(canonical_text(element), ensure_ascii=False).replace("$", "\\$")
        entries.append((int(name[4:]), text))
    if len({key for key, _ in entries}) != len(entries):
        raise ValueError("Doppelte Nachrichtenschlüssel")
    TARGET.write_text(HEADER + "".join(f"    {key} to {text},\n" for key, text in sorted(entries)) + ")\n")


if __name__ == "__main__":
    main()
