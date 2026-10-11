# Lokalisierung

## Sprachwahl

Die App bietet Deutsch, Englisch und Chinesisch unter **Einstellungen → Sprache**. Die Wahl wirkt sofort und bleibt im privaten App-Speicher. Ein Wechsel startet die Activity nicht neu und führt keine Quellenlesung, Autorisierung oder Kalenderaktion aus. Nicht unterstützte Systemsprachen verwenden Englisch.

Die deutsche README ist die zentrale Projektbeschreibung. Quellkommentare, Buildkommentare und die ergänzende Leseübersetzung der Ausführungsgrenzen sind deutsch. Die verbindliche chinesische AGENTS.md sowie die ursprünglichen Lizenztexte bleiben unverändert.

## Ressourcen und bestehende Modelle

`values/strings.xml` enthält Englisch als Rückfall, `values-de/strings.xml` Deutsch und `values-zh/strings.xml` Chinesisch. Nummerierte Nachrichtenschlüssel sind über alle Sprachen stabil; Platzhalter dürfen beim Übersetzen umgestellt, aber nicht entfernt werden.

Bestehende Modelle und gespeicherte Werte verwenden weiterhin ihre kanonischen String-Verträge. `i18n/AppMessage.kt` verfolgt ausschließlich von der App erzeugte Nachrichten über schwache Objektidentitäten bis zur Anzeige. Compose löst dann Schlüssel und Argumente in der gewählten Sprache auf. Gleichlautende eigene Texte bleiben unverändert; es findet keine globale Textersetzung statt. Nachrichtenteile müssen mit `joinMessages` beziehungsweise `concatenateMessages` verbunden werden, damit ihre Herkunft erhalten bleibt.

`i18n/MessageSources.kt` ist der generierte kanonische Katalog. Er wird mit `python3 scripts/generate_message_catalog.py` aus den chinesischen Ressourcen erzeugt. `restoreOwnedMessage` wird ausschließlich für zuvor gespeicherte app-eigene Ergebniszusammenfassungen verwendet, niemals für Kursnamen oder andere Nutzereingaben. Der kanonische Katalog bleibt unverändert, damit bestehende Regeln, Tests und persistierte Werte durch die Anzeigesprache nicht verändert werden. Synchronisierungsidentitäten, Preference-Schlüssel und Nutzereingaben werden nicht übersetzt. Bereits extern geschriebene Kalendernamen und Beschreibungen bleiben aus Kompatibilitätsgründen ebenfalls unverändert.

## Bewusst erhaltene chinesische Inhalte

- Chinesische Sprachressourcen und die Sprachauswahl „中文“.
- Verbindliche AGENTS.md und der kanonische Nachrichtenkatalog.
- Bestehende Kalender-Konstanten für die Kompatibilität bereits angelegter Ziele.
- Synthetische Testdaten und vorhandene chinesische Testnamen. Sie prüfen unter anderem, dass Kursnamen und andere Eingaben unverändert bleiben.
- Historische chinesische Bildschirmaufnahmen und Git-Committexte. Die Historie wird für diese Übersetzung nicht umgeschrieben.

Diese Inhalte sind keine fehlenden deutschen Quellkommentare. Eine vollständige Liste der ursprünglich betroffenen Dateien steht im [Textinventar](TEXTINVENTAR.md).

## Prüfung

Die lokalen Tests prüfen Sprachwechsel, private Sprachspeicherung, Ressourcenabdeckung, verschachtelte Hinweise, unveränderte Eingaben und lokalisierte Daten. Synthetische Bildschirmaufnahmen prüfen schmale und breite Ansichten. Ein Debug-Build prüft Ressourcen und Kotlin-Code. Echte Google-/HONOR-Kalenderaktionen sind keine Voraussetzung einer rein sprachlichen Änderung und wurden dafür nicht ausgelöst.
