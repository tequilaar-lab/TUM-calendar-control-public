/*
 * Aufgabe: Bestätigungsdialog als Glasfläche über der ganzen Seite (Kopf, Inhalt, Navigation) und dessen Wirt.
 * Ein-/Ausgabe: Lokalisierte Texte und Rückrufe einer Seite -> Überlagerung mit Abbrechen/Bestätigen.
 * Nebenwirkungen: Keine; Rückrufe entscheiden über Aktionen. Zurück-Taste gilt als Abbrechen.
 * Nicht zuständig: Inhalt oder Auslösung der bestätigten Aktion, Übersetzung, Navigation.
 */
package com.tequila.tumcalendarcontrol.ui.design

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** Inhalt eines Bestätigungsdialogs; alle Texte bereits lokalisiert. danger färbt die Bestätigung gedämpft rot. */
class DesignConfirmRequest(
    val kicker: String,
    val title: String,
    val body: String,
    val note: String,
    val cancelLabel: String,
    val confirmLabel: String,
    val danger: Boolean,
    val onCancel: () -> Unit,
    val onConfirm: () -> Unit,
)

/** Vom Wirt gehaltene aktuelle Anfrage; Seiten setzen sie über DesignConfirmDialog. */
class DesignConfirmController {
    var request by mutableStateOf<DesignConfirmRequest?>(null)
}

val LocalDesignConfirmController = staticCompositionLocalOf<DesignConfirmController?> { null }

/**
 * Wirt um den ganzen Seitenrahmen: Rahmen ist die Abtastebene, der Dialog liegt außerhalb davon
 * (sonst zeichnete er sich beim Abtasten rekursiv selbst).
 */
@Composable
fun DesignConfirmHost(content: @Composable () -> Unit) {
    val controller = remember { DesignConfirmController() }
    val host = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalDesignConfirmController provides controller, LocalGlassHostBackdrop provides host) {
            Box(Modifier.fillMaxSize().layerBackdrop(host)) { content() }
        }
        controller.request?.let { DesignConfirmOverlay(it, host) }
    }
}

/**
 * Seitenseitiger Aufruf: request != null zeigt den Dialog, null blendet ihn aus.
 * Ohne Wirt (z. B. isolierte Komponententests) erscheint die Überlagerung an Ort und Stelle.
 */
@Composable
fun DesignConfirmDialog(request: DesignConfirmRequest?) {
    val controller = LocalDesignConfirmController.current
    if (controller == null) {
        request?.let { DesignConfirmOverlay(it, null) }
        return
    }
    SideEffect { controller.request = request }
    DisposableEffect(controller) { onDispose { controller.request = null } }
}

/** Leichter Schleier; Fläche mindestens 94 % deckend, damit der Text unabhängig von der Glasstufe lesbar bleibt. */
@Composable
private fun DesignConfirmOverlay(request: DesignConfirmRequest, host: LayerBackdrop?) {
    val colors = LocalDesignColors.current
    val spec = glassSpec(LocalGlassLevel.current, LocalGlassSupport.current)
    val glass = host != null && spec.enabled
    BackHandler(onBack = request.onCancel)
    Box(
        Modifier.fillMaxSize().background(colors.ink.copy(alpha = if (glass) .16f else .36f))
            // Schleier fängt Berührungen ab (ohne Semantik, sonst verschmölze der Dialoginhalt zu einem Knopf).
            .pointerInput(Unit) { detectTapGestures {} }
            .testTag("confirm-dialog"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(28.dp).widthIn(max = 400.dp).fillMaxWidth()
                .designGlass(colors, DesignRadii.Dialog, host, spec, tintScale = 1.6f, minAlpha = .94f).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DesignText(request.kicker, 11, muted = true)
            DesignText(request.title, 22, modifier = Modifier.testTag("confirm-title"))
            DesignText(request.body, 14)
            if (request.note.isNotEmpty()) {
                DesignLine()
                DesignText(request.note, 12, muted = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Abbrechen leicht, Bestätigen mit eigenem Muster (gefährlich: Ablehnungsmuster).
                DesignPillButton(request.cancelLabel, Modifier.weight(1f).testTag("confirm-cancel"), primary = false, haptic = HapticEvent.SELECTION, onClick = request.onCancel)
                DesignPillButton(
                    request.confirmLabel, Modifier.weight(1f).testTag("confirm-accept"), danger = request.danger,
                    haptic = if (request.danger) HapticEvent.REJECT else HapticEvent.CONFIRM, onClick = request.onConfirm,
                )
            }
        }
    }
}
