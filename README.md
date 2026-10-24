# TUM Kalendersteuerung

Eine Android-App, die TUM-Kurstermine aus einem iCal-Abonnement entsprechend der eigenen Teilnahmeauswahl in einen app-eigenen lokalen Kalender und/oder einen Google-Zweitkalender überträgt. Systemkalender wie HONOR Kalender dienen als Anzeige.

Version: **1.4.0**, Build **61**. Die Oberfläche unterstützt **Deutsch, Englisch und Chinesisch**. Unter **Einstellungen → Sprache** lässt sich die Sprache sofort wechseln; die Auswahl bleibt auf dem Gerät gespeichert. Beim ersten Start gilt die Systemsprache, für andere Sprachen Englisch.

## Bedienung

1. In **Einstellungen & Synchronisierung** die private iCal-Adresse eingeben. Den Lesezeitraum wählen und **Kurse lesen** antippen.
2. Konkrete Termine auswählen. Bereits gespeicherte Kurse lassen sich ohne erneutes Lesen weiterverwenden; ein neuer Leseentwurf kürzt die bestehende Auswahl nicht.
3. Teilnahme festlegen: **Grün** bedeutet verbindliche Teilnahme, **Blau** frei gewählte Teilnahme, **Grau** keine Teilnahme ab dem festgelegten Termin. Favoriten dienen nur der Anzeige.
4. In der Ausgabevorschau lokale und/oder Google-Ziele wählen, die betroffenen Mengen prüfen und die Ausgabe ausdrücklich bestätigen.

## Oberfläche

Seit 1.2.0 hat die App ein ruhiges, akademisches Erscheinungsbild: große Seitentitel, Milchglaskarten und schwebende Hauptaktionen sowie eine untere Navigation aus Glas. Alle Bedienelemente basieren auf Miuix; den Glaseffekt liefert backdrop. Unter **Einstellungen → Darstellung** lassen sich Hell/Dunkel, je zwei Farbpaletten und die Glasstufe (**Aus, Weich, Standard, Klar**) wählen. Android 12 zeigt nur Unschärfe, erst ab Android 13 kommt die Linsenbrechung hinzu; ältere Geräte verwenden deckende Flächen. Bestätigungen wie Neulesen, Bereinigen, Zieländerung und Wiederherstellen erscheinen als Glasdialog über der ganzen Seite. In der Kursauswahl wählen **Alle Kurse auswählen** und **Ganzen Kurs auswählen** schnell aus. Ist bereits alles gewählt, heben dieselben Schaltflächen die Auswahl wieder auf. Beide hängen weder von der Suche noch vom Anzeigefilter ab.

Seit 1.3.0 gibt es drei **Startbildschirm-Widgets** mit eigenem Schwerpunkt: klein „Nächster Termin“ (laufender oder nächster Termin mit Raum), mittel „Heute“ (Zeitleiste) und groß „Diese Woche“. Sie zeigen nur Termine, die bereits in den Kalender geschrieben wurden, lassen sich in Höhe und Breite frei ziehen und unter **Einstellungen → Startbildschirm-Widgets** anpassen: Grau oder Blau ausblenden, Raum anzeigen, deckend oder durchscheinend, fünf Eckenstufen. Nach einer neuen Lesung zeigt die Kursauswahl, was sich gegenüber der vorherigen Lesung geändert hat (neu, entfallen, abgesagt, Zeit, Ort); die Seite **Änderungsdetails** listet alles auf. Der Hinweis der Hintergrundprüfung nennt nur die Zahlen und öffnet „Kurse lesen“. Orte in Kursdetails und Übersicht öffnen auf Antippen den TUM-Raumfinder (nav.tum.de). Beim Schreiben zeigt die Hauptaktion den Fortschritt je Kalender; **Anhalten** wirkt zwischen zwei Terminen, und erneutes Schreiben setzt dort fort.

Seit 1.4.0 haben eigene Campustermine eine **Art**: Termin, Prüfung oder Frist. Eine Frist braucht nur die Fälligkeit und erscheint im Kalender als die 30 Minuten davor; sonst bleibt der geschriebene Kalendertermin unverändert. Die Startseite zeigt unter **Demnächst** bis zu drei Prüfungen oder Fristen der nächsten 30 Tage mit Restzeit. Das vierte Widget **Countdown** zählt groß bis zur nächsten oder einer angehefteten Prüfung/Frist und zeigt breit oder hoch weitere Einträge. Langes Drücken auf das App-Symbol bietet Verknüpfungen (Neu lesen, Prüfung hinzufügen, Ausgabe prüfen, Widget-Einstellungen); die Schnelleinstellung **Auf Änderungen prüfen** startet bei aktivierter Hintergrundprüfung sofort eine lesende Prüfung.

Der Browser ordnet Kurse nach **Kursname → Lehrtyp → Einzeltermin**. Namen und Typen strukturieren ausschließlich die Anzeige. Synchronisierung und Bereinigung verwenden immer **Quell-UID und ursprünglichen Instanzschlüssel**; gleichzeitige unterschiedliche Kurse bleiben getrennt.

Eigene Kursnamen, Orte, Spitznamen und Eingaben werden beim Sprachwechsel nicht übersetzt. Die Darstellung enthält auch übersetzte Fehler-, Berechtigungs-, Sicherungs- und Bereinigungshinweise.

## Schutz der Kalender und Daten

- Die App verwaltet ausschließlich eigene lokale Kalender und selbst erstellte Google-Zweitkalender. Hauptkalender, Abonnements und fremde Termine bleiben unberührt.
- Lesen, Autorisieren, Schreiben und Bereinigen erfordern ausdrückliche Aktionen. Normale Ausgabe ergänzt oder aktualisiert nur die Auswahl; sie löscht keine abgewählten Kurse.
- Werden lokaler und Google-Kalender gleichzeitig in HONOR angezeigt, können Termine doppelt erscheinen. In diesem Fall nur einen Kalender anzeigen.
- Gespeicherte Kurse und Regeln liegen im privaten App-Speicher. Private iCal-Adressen, OAuth-Token und echte Kalenderdaten gehören niemals ins Repository.
- Widgets lesen nur die app-eigenen Zuordnungen auf dem Gerät, ohne Netzwerk; sie zeigen Kursnamen und Räume auf dem Startbildschirm.
- Hintergrundprüfungen sind standardmäßig aus. Nach Aktivierung prüfen sie nur auf mögliche Änderungen; sie schreiben nicht automatisch und können durch Energiesparmaßnahmen verzögert werden.

## Entwicklung

Die App verwendet Kotlin, Jetpack Compose mit Miuix, backdrop (Glaseffekte), iCal4j, WorkManager und Google Play services. Erforderlich sind Android SDK 37 und ein mit den Gradle-Plugins kompatibles JDK; die Ausgabe bleibt Java-17-kompatibel. Die App benötigt mindestens Android 8 / API 26.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Seit Version 1.2.0 hat **Claude** (Anthropic) die Weiterentwicklung übernommen und die Oberfläche neu gestaltet. Anforderungen, Abnahme und Freigaben liegen weiterhin beim Entwickler.

Lokale Tests und ein erfolgreicher Build ersetzen keine Prüfung auf einem echten Gerät oder der tatsächlichen Kalenderausgabe. Die Google-Verbindung erfordert eine passende Google-Cloud-Konfiguration für die verwendete App-/Signaturkombination.

Ausführungsgrenzen stehen im verbindlichen [AGENTS.md](AGENTS.md). Eine [deutsche Leseübersetzung](AGENTS.de.md) erläutert dieselben Regeln. Hinweise zu den Sprachressourcen und bewusst beibehaltenen chinesischen Inhalten stehen in [Lokalisierung](docs/LOKALISIERUNG.md); die ursprüngliche vollständige Dateiliste steht im [Textinventar](docs/TEXTINVENTAR.md).

## Lizenz und Beiträge

Der eigene Quellcode steht unter **GPL-3.0-only** mit einer zusätzlichen Erlaubnis für die benötigten Google-Play-services-Bibliotheken. Siehe [LICENSE](LICENSE) und [LICENSE-NOTICE.md](LICENSE-NOTICE.md). Wer geänderte Versionen weitergibt, muss den zugehörigen Quellcode nach GPLv3 bereitstellen. Fremdbibliotheken behalten ihre eigenen Lizenzbedingungen; die verbindlichen Lizenztexte bleiben im Original.

Beiträge können als Pull Request eingereicht werden. Über die Aufnahme in `main` entscheidet der Maintainer. Ein Fork oder Klon verleiht keine Schreibberechtigung für dieses Repository.
