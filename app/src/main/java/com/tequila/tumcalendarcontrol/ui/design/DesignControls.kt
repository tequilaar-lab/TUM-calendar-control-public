/*
 * Aufgabe: Eingabe- und Auswahlsteuerelemente des neuen Erscheinungsbilds als Hüllen um Miuix-Komponenten.
 * Ein-/Ausgabe: Lokalisierte Beschriftungen, Werte und Rückrufe -> Miuix RadioButton, Checkbox, TabRowWithContour,
 *   TextField, Button und BasicComponent mit den Farbrollen und Radien des Designs.
 * Nebenwirkungen: Keine; Rückrufe entscheiden über Zustandsänderungen.
 * Nicht zuständig: Fachlogik, Übersetzung, Speicherung oder Navigation.
 */
package com.tequila.tumcalendarcontrol.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
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
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
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
fun DesignChoice(label: String, selected: Boolean, modifier: Modifier = Modifier, detail: String = "", onClick: () -> Unit) {
    val colors = LocalDesignColors.current
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RadioButton(selected, onClick = null, colors = RadioButtonDefaults.radioButtonColors(selectedColor = colors.ink))
        DesignLabel(label, detail)
    }
}

@Composable
fun DesignCheck(label: String, checked: Boolean, modifier: Modifier = Modifier, detail: String = "", onChange: (Boolean) -> Unit) {
    val colors = LocalDesignColors.current
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onChange).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Checkbox(ToggleableState(checked), onClick = null,
            colors = CheckboxDefaults.checkboxColors(checkedBackgroundColor = colors.ink, checkedForegroundColor = colors.paper))
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

/** Segmentsteuerung: helle Rinne, gewähltes Segment hebt sich als Fläche ab (dunkel: Tinte 14 %). */
@Composable
fun DesignSegmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val colors = LocalDesignColors.current
    TabRowWithContour(
        tabs = options, selectedTabIndex = selected, onTabSelected = onSelect,
        modifier = modifier.fillMaxWidth(), cornerRadius = DesignRadii.Field, height = 52.dp,
        colors = TabRowDefaults.tabRowColors(
            backgroundColor = colors.wash, contentColor = colors.muted,
            selectedBackgroundColor = if (colors.isDark) colors.ink.copy(alpha = .14f) else colors.floating, selectedContentColor = colors.ink,
        ),
    )
}

/**
 * Eingabefeld mit Beschriftung darüber; in Ruhe feine Kante (Miuix zeichnet nur beim Fokus eine),
 * beim Fokus Akzentkante von Miuix. search=true ergänzt die Lupe und eine einzeilige Eingabe.
 */
@Composable
fun DesignField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    search: Boolean = false,
) {
    val colors = LocalDesignColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (label.isNotEmpty()) DesignText(label, 12, muted = true)
        // squircleBorder zeichnet hinter dem Inhalt; die Feldfläche würde sie überdecken, daher als eigene Ebene darüber.
        Box {
            TextField(
                value = value, onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = DesignRadii.Field,
                colors = TextFieldDefaults.textFieldColors(backgroundColor = colors.wash, labelColor = colors.muted, borderColor = colors.accent.copy(alpha = .6f)),
                label = placeholder, useLabelAsPlaceholder = true, singleLine = search,
                // Eingabetext in Tinte; Miuix-Standard wäre im Dunkelschema zu dunkel.
                textStyle = MiuixTheme.textStyles.main.copy(color = colors.ink, fontSize = 15.sp),
                leadingIcon = if (search) { { Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.muted, modifier = Modifier.padding(start = 14.dp).size(20.dp)) } } else null,
            )
            Box(Modifier.matchParentSize().squircleBorder(1.dp, colors.ink.copy(alpha = .14f), DesignRadii.Field))
        }
    }
}

/** Pillen-Schaltfläche im Inhalt: primär Akzent, sekundär helle Fläche mit Glanzkante, Gefahr gedämpftes Rot. */
@Composable
fun DesignPillButton(label: String, modifier: Modifier = Modifier, primary: Boolean = true, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalDesignColors.current
    val container = when { !primary -> colors.wash; danger -> colors.danger; else -> colors.accent }
    val content = if (primary) colors.paper else colors.ink
    val sheen = !primary && enabled && LocalGlassLevel.current != GlassLevel.OFF
    Button(
        onClick = onClick, enabled = enabled, cornerRadius = DesignRadii.Pill, minHeight = 48.dp,
        modifier = modifier.fillMaxWidth().designSheen(RoundedCornerShape(DesignRadii.Pill), colors, sheen),
        colors = ButtonDefaults.buttonColors(color = container, disabledColor = colors.wash, contentColor = content, disabledContentColor = colors.muted),
    ) {
        DesignText(label, 13, color = if (enabled) content else colors.muted, weight = FontWeight.Medium)
    }
}

/** Listenzeile auf Basis von Miuix BasicComponent: Titel, Erläuterung, optionaler Wert und Pfeil, darunter feine Linie. */
@Composable
fun DesignRow(title: String, modifier: Modifier = Modifier, detail: String = "", value: String = "", danger: Boolean = false, tag: String = "", onClick: () -> Unit = {}) {
    val colors = LocalDesignColors.current
    Column(modifier.fillMaxWidth().then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier)) {
        BasicComponent(
            title = title, summary = detail.ifEmpty { null },
            titleColor = BasicComponentDefaults.titleColor(color = if (danger) colors.danger else colors.ink),
            summaryColor = BasicComponentDefaults.summaryColor(color = colors.muted),
            insideMargin = PaddingValues(vertical = 12.dp),
            onClick = onClick,
            endActions = {
                if (value.isNotEmpty()) DesignText(value, 12, muted = true)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.muted, modifier = Modifier.size(18.dp))
            },
        )
        DesignLine()
    }
}
