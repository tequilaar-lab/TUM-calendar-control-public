/*
 * Aufgabe: Android-App-Modul, Compose und Testabhängigkeiten konfigurieren.
 * Ein-/Ausgabe: Öffentliche Plugin-/Bibliothekskoordinaten -> Debug-APK und JVM-Testaufgaben.
 * Nebenwirkungen: Beim ersten Build öffentliche Abhängigkeiten herunterladen.
 * Nicht zuständig: Netzwerkzugriffe, Kontoautorisierung, iCal-Parsing oder echte Kalenderausgaben.
 */
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import dev.detekt.gradle.Detekt

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
    // Nur Diagnoseberichte erzeugen; keine Formatierung und keine Vorbedingung für check.
    id("org.jlleitschuh.gradle.ktlint")
    id("dev.detekt")
}

android {
    namespace = "com.tequila.tumcalendarcontrol"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tequila.tumcalendarcontrol"
        minSdk = 26
        targetSdk = 37
        // Buildnummer nach jedem abgenommenen Release erhöhen; Nachweise bleiben im lokalen Audit.
        versionCode = 62
        versionName = "1.4.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // Optimierter Testbau für das eigene Gerät: nicht debugbar (ART kompiliert, Compose-Profile wirken),
        // mit dem lokalen Debug-Schlüssel signiert, damit er Debug-Installationen samt Daten überschreiben kann.
        create("staging") {
            initWith(getByName("release"))
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += "META-INF/groovy/**"
        resources.excludes += "META-INF/groovy-release-info.properties"
        resources.excludes += "META-INF/INDEX.LIST"
    }
}

kotlin {
    // Mit vorhandenem neuerem JDK bauen; Artefakte bleiben Java-17-kompatibel.
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Aufgabe: manuell ausführbare Analyseberichte, ohne Codeänderung oder verpflichtende Qualitätsprüfung.
// Ein-/Ausgabe: Kotlin/Gradle-Quellen -> Ktlint-/Detekt-Berichte unter app/build/reports.
// Nebenwirkungen: öffentliche Analyseabhängigkeiten beim ersten Lauf laden; keine Kalender-, Konto- oder Gerätezugriffe.
// Nicht zuständig: Formatierung, Baselines, Unterdrückungsregeln oder Blockieren von check/assemble.
ktlint {
    ignoreFailures.set(true)
    outputToConsole.set(false)
}

detekt {
    toolVersion = "2.0.0-alpha.6"
    ignoreFailures = true
    autoCorrect = false
}

tasks.withType<Detekt>().configureEach {
    reports {
        html.required.set(true)
        checkstyle.required.set(true)
        sarif.required.set(false)
        markdown.required.set(false)
    }
}

val lintCompatibilityReport = tasks.register("lintCompatibilityReport") {
    group = "verification"
    description = "Erzeugt versuchsweise Ktlint- und Detekt-Berichte; formatiert nicht und blockiert den Build nicht."
    dependsOn("ktlintCheck", "detekt")
}

// Beide Plugins hängen standardmäßig an check; die ursprünglichen direkten lint-/test-Abhängigkeiten wiederherstellen.
// Neue Analysen nur über den manuellen Einstieg ausführen; Android-Prüfumfang unverändert.
tasks.named("check") {
    setDependsOn(listOf(tasks.named("lint"), tasks.named("test")))
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    // Nur Rounded-Symbolvektoren; Oberflächenkomponenten weiterhin aus Compose-Miuix.
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.3")
    // Glasflächen (Hintergrundunschärfe, Linsenbrechung) für schwebende Navigation und Schaltflächen; Miuix bietet keine.
    // 1.0.6 setzt Compose 1.10.3 und Kotlin-Stdlib 2.3.10 voraus und hebt keine vorhandenen Versionen an.
    implementation("io.github.kyant0:backdrop:1.0.6")
    // Bewährter RFC-5545-Parser; API 26 und Java 17 erfüllen seine Android-Voraussetzungen.
    implementation("org.mnode.ical4j:ical4j:3.1.0")
    // Offizieller Autorisierungsclient; minimaler app.created-Umfang erst nach ausdrücklichem Verbinden.
    implementation("com.google.android.gms:play-services-auth:21.6.0")
    // Offizieller WorkManager; Hintergrundprüfung liest nur die Quelle, keine Kalendersynchronisierung.
    implementation("androidx.work:work-runtime:2.11.2")

    testImplementation("junit:junit:4.13.2")
    // Nur lokale Compose-Bildregression; keine Geräte-, Konto- oder Kalenderverbindung.
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.74.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.74.0")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.7.8")
    testImplementation("org.robolectric:robolectric:4.16")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.8")
}
