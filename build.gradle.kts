/*
 * Aufgabe: Projektweite Android-/Kotlin-Pluginversionen festlegen.
 * Ein-/Ausgabe: Plugin-Koordinaten -> projektweit verfügbare Plugins.
 * Nebenwirkungen: Öffentliche Plugins beim ersten Build auflösen.
 * Nicht zuständig: Modulabhängigkeiten, Anwendungslogik oder Kalenderzugriffe.
 */
plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("io.github.takahirom.roborazzi") version "1.74.0" apply false
    // Nur berichtsorientierter Kompatibilitätsversuch im App-Modul; nicht im Rootprojekt oder check.
    id("org.jlleitschuh.gradle.ktlint") version "14.1.0" apply false
    id("dev.detekt") version "2.0.0-alpha.6" apply false
}
