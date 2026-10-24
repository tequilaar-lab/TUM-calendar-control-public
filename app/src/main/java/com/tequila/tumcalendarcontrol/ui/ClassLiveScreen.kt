/*
 * Aufgabe: Einstellungsseite der Live-Kursbenachrichtigung: Erklärung, Schalter (fragt bei Bedarf die
 *   Benachrichtigungsberechtigung an), Stand der Live-Updates im System, Vorlauf und „auch blaue Kurse“.
 * Ein-/Ausgabe: ClassLiveSettingsStore -> Bedienelemente; Änderungen -> speichern und ClassLiveScheduler.refresh.
 * Nebenwirkungen: Nur private Einstellungen, Systemdialog der Benachrichtigungsberechtigung, Neuplanung von
 *   Benachrichtigung und Wecker; kein Netzwerk, keine Kalender- oder Regeländerung.
 * Nicht zuständig: Planung, Inhalt der Benachrichtigung oder Wecker (notify-Paket).
 */
package com.tequila.tumcalendarcontrol.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.notify.ClassLiveLeadOptions
import com.tequila.tumcalendarcontrol.notify.ClassLiveNotifier
import com.tequila.tumcalendarcontrol.notify.ClassLiveScheduler
import com.tequila.tumcalendarcontrol.notify.ClassLiveSettings
import com.tequila.tumcalendarcontrol.notify.ClassLiveSettingsStore
import com.tequila.tumcalendarcontrol.ui.design.DesignCheck
import com.tequila.tumcalendarcontrol.ui.design.DesignChips
import com.tequila.tumcalendarcontrol.ui.design.DesignPage
import com.tequila.tumcalendarcontrol.ui.design.DesignSection
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors

private val LiveInset = Modifier.padding(horizontal = 4.dp)

@Composable
fun ClassLiveScreen() {
    val context = LocalContext.current
    val store = remember(context) { ClassLiveSettingsStore(context) }
    var settings by remember { mutableStateOf(store.load()) }
    var permissionMissing by remember { mutableStateOf(!ClassLiveNotifier.canPost(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionMissing = !granted
        ClassLiveScheduler.refresh(context)
    }
    fun change(next: ClassLiveSettings) {
        settings = next
        store.save(next)
        ClassLiveScheduler.refresh(context)
    }
    DesignPage(contentTag = "class-live-content") {
        DesignText(tr(message(927)), 12, LiveInset, muted = true)
        DesignCheck(tr(message(926)), settings.enabled, LiveInset.testTag("class-live-enabled")) { enabled ->
            change(settings.copy(enabled = enabled))
            // Einschalten ohne Berechtigung: Systemdialog (ab Android 13), sonst bleibt nur der Hinweis.
            if (enabled && permissionMissing && Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (settings.enabled && permissionMissing) {
            DesignText(tr(message(939)), 12, LiveInset.testTag("class-live-permission"), color = LocalDesignColors.current.danger)
        }
        if (settings.enabled) {
            DesignText(tr(message(if (ClassLiveNotifier.canPromote(context)) 937 else 938)), 12, LiveInset.testTag("class-live-promotion"), muted = true)
        }
        DesignSection(tr(message(928)), LiveInset)
        DesignChips(ClassLiveLeadOptions.map { tr(message(929, it)) }, ClassLiveLeadOptions.indexOf(settings.leadMinutes), Modifier.testTag("class-live-lead")) {
            change(settings.copy(leadMinutes = ClassLiveLeadOptions[it]))
        }
        DesignCheck(tr(message(930)), settings.includeChooseToAttend, LiveInset.testTag("class-live-choose")) { change(settings.copy(includeChooseToAttend = it)) }
    }
}
