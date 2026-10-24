/*
 * Aufgabe: Ortsangaben antippbar machen: Zeile „Raum auf der Karte zeigen“ (Kursdetails) und kompakte Kartenschaltfläche
 *   (Terminzeilen der Übersicht), beide öffnen den TUM-Raumfinder im Browser.
 * Ein-/Ausgabe: Ortstext -> Bedienelement; Antippen -> Browser mit RoomLink-Adresse.
 * Nebenwirkungen: Nur nach ausdrücklichem Antippen wird genau dieser Ortstext als Adresse an nav.tum.de übergeben.
 * Nicht zuständig: Linkbildung (RoomLink), Kalendertermine oder Hintergrundabrufe.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Place
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tequila.tumcalendarcontrol.domain.RoomLink
import com.tequila.tumcalendarcontrol.ui.design.DesignRow
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors
import com.tequila.tumcalendarcontrol.ui.design.hapticClickable
import top.yukonga.miuix.kmp.basic.Icon

/** Öffnet die Adresse; fehlt ein Browser, passiert nichts (kein Absturz, keine Ersatzaktion). */
@Composable
private fun rememberRoomOpener(): (String) -> Unit {
    val handler = LocalUriHandler.current
    return { url -> runCatching { handler.openUri(url) } }
}

/** Zeile in den Kursdetails; ohne Ort nicht sichtbar. Detail nennt Raumnummer bzw. Ortstext und das Ziel nav.tum.de. */
@Composable
internal fun RoomLinkRow(location: String, modifier: Modifier = Modifier) {
    val url = RoomLink.url(location) ?: return
    val open = rememberRoomOpener()
    DesignRow(tr(message(777)), modifier, detail = tr(message(778, RoomLink.roomCode(location) ?: location.trim())), tag = "room-link") { open(url) }
}

/** Kompakte Kartenschaltfläche (48 dp Tippfläche) neben einer Terminzeile; ohne Ort nicht sichtbar. */
@Composable
internal fun RoomMapButton(location: String, modifier: Modifier = Modifier) {
    val url = RoomLink.url(location) ?: return
    val open = rememberRoomOpener()
    val description = tr(message(779))
    Box(
        modifier.size(48.dp).hapticClickable { open(url) }.semantics { contentDescription = description }.testTag("room-map"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Place, contentDescription = null, tint = LocalDesignColors.current.muted, modifier = Modifier.size(20.dp))
    }
}
