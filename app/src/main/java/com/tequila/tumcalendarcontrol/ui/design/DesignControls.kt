/*
 * Aufgabe: Eingabe- und Auswahlsteuerelemente des neuen Erscheinungsbilds als Hüllen um Miuix-Komponenten.
 * Ein-/Ausgabe: Lokalisierte Beschriftungen, Werte und Rückrufe -> Miuix RadioButton, Checkbox, TabRowWithContour,
 *   umbrechende Auswahlchips,
 *   TextField, Button und BasicComponent mit den Farbrollen und Radien des Designs.
 * Nebenwirkungen: Kurze haptische Rückmeldung über LocalDesignHaptics; Rückrufe entscheiden über Zustandsänderungen.
 * Nicht zuständig: Fachlogik, Übersetzung, Speicherung oder Navigation.
 */
package com.tequila.tumcalendarcontrol.ui.design

import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.ui.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CheckboxDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.RadioButtonDefaults
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Feine Trennlinie mit 10 % Tinte; trennt Zeilen ohne harte graue Kästen. */
@Composable
fun DesignLine(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = LocalDesignColors.current.ink.copy(alpha = .10f))
}

// —— Auswahl: Tinte statt Akzent, damit Blau eindeutig „Teilnahme auswählen“ bleibt ——

@Composable
fun DesignChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    detail: String = "",
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = LocalDesignColors.current
    val haptics = LocalDesignHaptics.current
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(selected, role = Role.RadioButton) { haptics.perform(HapticEvent.SELECTION); onClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Miuix-Auswahl zeigt nur beim gewählten Eintrag ein Häkchen; fester Platz hält die Beschriftungen bündig.
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            RadioButton(selected, onClick = null, colors = RadioButtonDefaults.radioButtonColors(selectedColor = colors.ink))
        }
        Box(Modifier.weight(1f)) { DesignLabel(label, detail) }
        // Optionaler Abschluss rechts, z. B. Farbpunkt des Teilnahmestatus.
        trailing?.invoke()
    }
}

@Composable
fun DesignCheck(label: String, checked: Boolean, modifier: Modifier = Modifier, detail: String = "", onChange: (Boolean) -> Unit) {
    val colors = LocalDesignColors.current
    val haptics = LocalDesignHaptics.current
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(checked, role = Role.Checkbox) { haptics.perform(HapticEvent.SELECTION); onChange(it) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Miuix fixiert das Kästchen auf 26 dp (requiredSize); auf Wunsch etwas kleiner: 22-dp-Rahmen, 0,85 skaliert.
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            Checkbox(ToggleableState(checked), onClick = null, modifier = Modifier.scale(.85f),
                // Ungewählt: zarte Tintenfläche statt Miuix-Standard (der auf dem Papiergrund als schwerer Punkt wirkt).
                colors = CheckboxDefaults.checkboxColors(
                    checkedBackgroundColor = colors.ink, checkedForegroundColor = colors.paper,
                    uncheckedBackgroundColor = colors.ink.copy(alpha = .10f), uncheckedForegroundColor = colors.paper,
                ))
        }
        DesignLabel(label, detail)
    }
}

@Composable
private fun DesignLabel(label: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DesignText(label, 14)
        if (detail.isNotEmpty()) DesignText(detail, 11, muted = true)
    }
}

/**
 * Segmentsteuerung als Schwebeblock: zarte Rinne mit leichtem Schatten, gewähltes Segment als hervortretender
 * abgerundeter Block, der federnd gleitet und sich seitlich ziehen lässt (DesignSlidingSelector). Höhe 58 dp (Tippfläche ≥ 48 dp).
 */
@Composable
fun DesignSegmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val colors = LocalDesignColors.current
    val shape = RoundedCornerShape(DesignRadii.Field)
    DesignSlidingSelector(
        count = options.size, selected = selected, onSelect = onSelect,
        modifier = modifier.fillMaxWidth().height(58.dp).designGlow(DesignRadii.Field, colors, strength = .12f).clip(shape).background(colors.wash),
        corner = DesignRadii.Field - 4.dp,
    ) { index, active ->
        val ink by animatedSelectionColor(active, colors.ink, colors.muted, "segment-$index")
        DesignText(options[index], 14, color = ink, weight = if (active) FontWeight.Medium else FontWeight.Normal, maxLines = 1)
    }
}

/**
 * Auswahlchips für viele oder lange Optionen (z. B. sieben Wochentage), die in einer Segmentsteuerung abgeschnitten
 * würden: umbrechende Reihe, gleiche Farbrollen wie DesignSegmented, Tippfläche je Chip ≥ 48 dp hoch.
 */
@Composable
fun DesignChips(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val colors = LocalDesignColors.current
    val haptics = LocalDesignHaptics.current
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, option ->
            val active = index == selected
            val progress = animatedSelectionProgress(active, "chip-$index")
            Box(
                Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(DesignRadii.Field)).background(colors.wash)
                    .designSelectionBlock(colors, DesignRadii.Field, progress)
                    .selectable(active, role = Role.RadioButton) { haptics.perform(HapticEvent.SELECTION); onSelect(index) }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                DesignText(option, 14, color = if (active) colors.ink else colors.muted, weight = if (active) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}

/**
 * Eingabefeld mit Beschriftung darüber; in Ruhe feine Kante (Miuix zeichnet nur beim Fokus eine),
 * beim Fokus Akzentkante von Miuix. search=true ergänzt die Lupe und eine einzeilige Eingabe;
 * visualTransformation z. B. zum Verdecken privater Adressen.
 */
@Composable
fun DesignField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    search: Boolean = false,
    singleLine: Boolean = search,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalDesignColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Beschriftung als freier Text auf der Titellinie, 4 dp innerhalb der Feldkante.
        if (label.isNotEmpty()) DesignText(label, 12, muted = true, modifier = Modifier.padding(start = 4.dp))
        // squircleBorder zeichnet hinter dem Inhalt; die Feldfläche würde sie überdecken, daher als eigene Ebene darüber.
        Box {
            TextField(
                value = value, onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = DesignRadii.Field,
                colors = TextFieldDefaults.textFieldColors(backgroundColor = colors.wash, labelColor = colors.muted, borderColor = colors.accent.copy(alpha = .6f)),
                label = placeholder, useLabelAsPlaceholder = true, singleLine = singleLine, visualTransformation = visualTransformation,
                // Eingabetext in Tinte; Miuix-Standard wäre im Dunkelschema zu dunkel.
                textStyle = MiuixTheme.textStyles.main.copy(color = colors.ink, fontSize = 15.sp),
                leadingIcon = if (search) { { Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.muted, modifier = Modifier.padding(start = 14.dp).size(20.dp)) } } else null,
                // Suche: Leeren-Knopf, sobald etwas eingegeben ist; sonst optionaler eigener Abschluss (z. B. Datumswahl).
                trailingIcon = when {
                    search && value.isNotEmpty() -> { {
                        Icon(
                            Icons.Rounded.Close, contentDescription = tr(message(754)), tint = colors.muted,
                            modifier = Modifier.padding(end = 8.dp).size(48.dp).designPressable(corner = 24.dp) { onValueChange("") }.padding(14.dp).testTag("field-clear"),
                        )
                    } }
                    trailing != null -> trailing
                    else -> null
                },
            )
            Box(Modifier.matchParentSize().squircleBorder(1.dp, colors.ink.copy(alpha = .14f), DesignRadii.Field))
        }
    }
}

/** Pillen-Schaltfläche im Inhalt: primär Akzent, sekundär helle Fläche mit Glanzkante, Gefahr gedämpftes Rot. */
@Composable
fun DesignPillButton(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    danger: Boolean = false,
    enabled: Boolean = true,
    haptic: HapticEvent? = HapticEvent.ACTION,
    onClick: () -> Unit,
) {
    val haptics = LocalDesignHaptics.current
    val colors = LocalDesignColors.current
    val container = when { !primary -> colors.wash; danger -> colors.danger; else -> colors.accent }
    val content = if (primary) colors.paper else colors.ink
    val sheen = !primary && enabled && LocalGlassLevel.current != GlassLevel.OFF
    Button(
        onClick = { haptic?.let(haptics::perform); onClick() }, enabled = enabled, cornerRadius = DesignRadii.Pill, minHeight = 48.dp,
        modifier = modifier.fillMaxWidth().designSheen(RoundedCornerShape(DesignRadii.Pill), colors, sheen),
        colors = ButtonDefaults.buttonColors(color = container, disabledColor = colors.wash, contentColor = content, disabledContentColor = colors.muted),
    ) {
        DesignText(label, 13, color = if (enabled) content else colors.muted, weight = FontWeight.Medium)
    }
}

/** Listenzeile auf Basis von Miuix BasicComponent: Titel, Erläuterung, optionaler Wert und Pfeil, darunter feine Linie (inset: Textversatz). */
@Composable
fun DesignRow(
    title: String,
    modifier: Modifier = Modifier,
    detail: String = "",
    value: String = "",
    danger: Boolean = false,
    tag: String = "",
    inset: Dp = 0.dp,
    onClick: () -> Unit = {},
) {
    val colors = LocalDesignColors.current
    Column(modifier.fillMaxWidth().then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier)) {
        BasicComponent(
            title = title, summary = detail.ifEmpty { null },
            titleColor = BasicComponentDefaults.titleColor(color = if (danger) colors.danger else colors.ink),
            summaryColor = BasicComponentDefaults.summaryColor(color = colors.muted),
            // inset: Text auf der Titellinie, Trennlinie bleibt an der Blockkante (frei stehende Listen auf Seiten).
            insideMargin = PaddingValues(start = inset, top = 12.dp, bottom = 12.dp),
            // Klick über designPressable: Druck als abgerundeter Schwebeblock statt Rechteckblitz.
            modifier = Modifier.designPressable(onClick = onClick),
            endActions = {
                if (value.isNotEmpty()) DesignText(value, 12, muted = true)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.muted, modifier = Modifier.size(18.dp))
            },
        )
        DesignLine()
    }
}
