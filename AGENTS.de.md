# Verbindliche Grenzen: deutsche Leseübersetzung

Diese Datei ist eine Leseübersetzung des unveränderten chinesischen [AGENTS.md](AGENTS.md). Sie ersetzt den ursprünglichen Vertrag nicht und führt keine neuen Regeln ein. Bei Abweichungen gilt das Original.

## Geltung und Änderungen

AGENTS.md ist der einzige verbindliche Vertrag dieses Repositorys. Vor und nach jeder Ausführung, die Code, Konfiguration, Dokumentation, Abhängigkeiten, Tests, Telefoneinstellungen, Google Kalender oder echte Daten ändern kann, muss er vollständig gelesen werden. Plan und Ergebnis dokumentieren die Lektüre, den Git-Zustand und die Einhaltung.

Der Vertrag darf nicht eigenmächtig geändert, gelöscht, abgeschwächt oder umgangen werden. Jede Änderung erfordert zuerst einen chinesischen Plan mit alter/neuer Regel, Auswirkungen, Risiken und Begründung sowie die ausdrückliche Zustimmung des Benutzers. Ohne Zustimmung keine Vertragsänderung.

## Nachweise jeder Ausführung

1. Vorher Vertrag lesen, Git-Zustand prüfen und einen chinesischen Plan ausschließlich lokal unter `.local-audit/` speichern. Er enthält Ziel, Umfang, Ausschlüsse, Eingabegrenzen, Risiken, exakte Schreibziele, Rücknahme, einzelne Abnahmekriterien und erwartete Nachweise. Das Verzeichnis niemals committen oder pushen.
2. Nur geplante und autorisierte Aktionen ausführen. Ungeplante externe Schreibzugriffe, Rechteerweiterungen, Löschungen, Sammeländerungen oder Kalenderübernahme erfordern eine Unterbrechung und Benutzerbestätigung.
3. Danach Vertrag erneut vollständig lesen und angemessene Prüfungen ausführen. Im selben Plan Ergebnisse, Fehlernachweise, Git-Zustand, offene Punkte und Abnahme ergänzen. Vor Erfüllung aller Kriterien keinen Abschluss behaupten.
4. Jeden unabhängig abnehmbaren Schritt zuerst prüfen, dann nur zugehörige freigegebene Dateien stagen, committen und den aktuellen Branch pushen. Ungeprüfte Arbeit nur ausdrücklich als Zwischenstand kennzeichnen. Pushfehler melden; keine eigenmächtige Umschreibung der Remote-Historie. Audit, lokale Konfiguration, Buildartefakte, Caches, Token und echte Daten bleiben ausgeschlossen.
5. Wiederverwendbare Entwurfs-, Fehleranalyse- und Prüfmethoden zusätzlich als chinesische Anleitung in `.local-audit/` speichern, nicht nur im Chat und niemals auf GitHub.

## Module und Wartbarkeit

1. Jede handgeschriebene Codedatei hat genau eine klar abgegrenzte Aufgabe. Keine universellen Hilfsdateien, gemischten Schichten oder sachfremden Ergänzungen zum Umgehen von Grenzen.
2. Handgeschriebener Code, Tests und Buildskripte: höchstens 1.000 nichtleere Zeilen einschließlich Kommentaren, Ziel höchstens 500. Generierte, gesperrte und fremde Dateien nicht von Hand ändern; sie zählen nicht zu dieser Grenze.
3. Dateianfang nennt Verantwortung, Ein-/Ausgabe, externe Nebenwirkungen und Ausschlüsse. Hauptblöcke erhalten zweckbezogene Kommentare; öffentliche und nicht offensichtliche Synchronisierungs-/Regellogik klare Erläuterungen.
4. Zu lange oder vermischte Dateien nach Fachbereich zerlegen; keine Verdichtung, Erklärungsentfernung oder versteckte Logik als Umgehung.

## Daten, Datenschutz und Synchronisierung

1. Private iCal-Adressen, OAuth-Token, vollständige E-Mail-Adressen, echte Kalenderinhalte, Signaturschlüssel und unbereinigte Protokolle niemals committen oder in Plänen, Bildern oder Testbeispielen speichern. Tests ausschließlich mit synthetischen Daten.
2. Originale TUM-Daten nur lokal als Eingabe. Ohne bestätigten Fixplatz nur „unbestätigt/nicht importieren“, niemals automatisch ins Ziel schreiben.
3. Sichtbare Regeln: Grün verbindliche Teilnahme, Blau gewählte Teilnahme, Grau keine Teilnahme ab festgelegtem Datum/Termin. Grau verändert niemals rückwirkend historische Instanzen.
4. Nur selbst erstellte, beschreibbare Google-Zweitkalender verwalten; niemals Hauptkalender, Abonnements, manuelle Benutzerkalender oder Kalender anderer Apps. HONOR dient der Anzeige; lokale Änderungen dort gelten nicht als verlässlich an Google zurückgeschrieben.
5. Echtes privates Lesen, Kalendererstellung/-löschung, Sammeländerungen, Google-Datenübertragung, Telefoneinstellungen oder neue OAuth-Rechte erfordern genaue Ziele, Vorschau/Rücknahme und ausdrückliche Benutzerfreigabe im aktuellen Plan.

## Technik und Prüfung

1. Keine improvisierten eigenen ICS-Parser. Parser und Wiederholungen zunächst synthetisch für UID, Zeitzone, RRULE, EXDATE, RECURRENCE-ID und Absagen testen.
2. Deduplizierung über Quell-UID und ursprüngliche Instanz, niemals Titel, Farbe, Ort oder Beschreibung.
3. Hintergrundaufträge versprechen keine sekundengenaue Ausführung; sofortige Synchronisierung, verständlicher Status und Fehleraufzeichnungen müssen verfügbar sein.
4. Bei Regel-/Synchronisierungsänderungen getrennt prüfen: Unit-Logik, Vorschau, echten Google-Zielkalender und Anzeige auf echtem HONOR-Gerät. Keine Prüfung ersetzt eine andere.
