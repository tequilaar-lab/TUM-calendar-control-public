/*
 * Aufgabe: Private iCal-Adresse als verdeckte Eingabe mit kurzer letzter Zeichenanzeige erfassen.
 * Ein-/Ausgabe: Neuer ungespeicherter Adressentwurf -> gleichlange Maskierung und Bearbeitungsrückruf.
 * Nebenwirkungen: Nur Compose-Speicher und kurzer Maskierungs-Timer.
 * Nicht zuständig: Adressprüfung, Android-Keystore-Verschlüsselung, Konfigurationspersistenz oder Kalendersynchronisierung.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField

private const val LAST_CHARACTER_REVEAL_MILLIS = 1500L

@Composable
fun PrivateAddressInput(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var showAddress by remember { mutableStateOf(false) }
    var lastVisibleIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(lastVisibleIndex, value, showAddress) {
        if (!showAddress && lastVisibleIndex >= 0) {
            delay(LAST_CHARACTER_REVEAL_MILLIS)
            lastVisibleIndex = -1
        }
    }
    TextField(
        value = value,
        onValueChange = { updated ->
            lastVisibleIndex = if (!showAddress && updated.length > value.length) updated.length - 1 else -1
            onValueChange(updated)
        },
        modifier = modifier.fillMaxWidth(),
        label = tr(message(484)),
        singleLine = true,
        visualTransformation = PrivateAddressVisualTransformation(showAddress, lastVisibleIndex),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Switch(checked = showAddress, onCheckedChange = { visible ->
            showAddress = visible
            if (!visible) lastVisibleIndex = -1
        })
        Text(tr(if (showAddress) message(485) else message(486)))
    }
}

private class PrivateAddressVisualTransformation(
    private val showAll: Boolean,
    private val visibleIndex: Int,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val rendered = text.text.mapIndexed { index, character ->
            if (showAll || index == visibleIndex) character else '•'
        }.joinToString(separator = "")
        return TransformedText(AnnotatedString(rendered), OffsetMapping.Identity)
    }
}
