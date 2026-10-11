/*
 * Aufgabe: Module und Plugin-Repositories definieren.
 * Ein-/Ausgabe: Gradle-Einstellungen -> Buildgraph mit einem App-Modul.
 * Nebenwirkungen: Öffentliche Build-Abhängigkeiten auflösen.
 * Nicht zuständig: Kalenderdaten, Synchronisierungsregeln oder Android-Oberfläche.
 */
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TUM Calendar Control"
include(":app")
