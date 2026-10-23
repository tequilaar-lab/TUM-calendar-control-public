/*
 * Aufgabe: Textdarstellung des neuen Erscheinungsbilds auf Basis von Miuix-Text bereitstellen.
 * Ein-/Ausgabe: Bereits lokalisierter Text und Stilangaben -> Miuix Text mit Tabellenziffern und Zeilenumbruch-Strategie.
 * Nebenwirkungen: Keine.
 * Nicht zuständig: Übersetzung (Aufrufer übergeben tr(message(...))) oder Kursdaten-Formatierung.
 */
package com.tequila.tumcalendarcontrol.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text

/**
 * size in sp; Titel ab 22 sp nutzen ausgewogenen Überschriftenumbruch, Fließtext Absatzumbruch.
 * Automatische Silbentrennung hilft bei langen deutschen Kursnamen; Ziffern sind tabellarisch,
 * damit wechselnde Zählerstände Schaltflächen und Grundlinien nicht verschieben.
 */
@Composable
fun DesignText(
    text: String,
    size: Int = 15,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    weight: FontWeight = FontWeight.Normal,
    serif: Boolean = false,
    mono: Boolean = false,
    color: Color? = null,
    tracking: Float = 0f,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign = TextAlign.Start,
) {
    val colors = LocalDesignColors.current
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(
            color = color ?: if (muted) colors.muted else colors.ink,
            fontSize = size.sp,
            lineHeight = (size * 1.35).sp,
            fontWeight = weight,
            letterSpacing = tracking.sp,
            fontFamily = when { serif -> FontFamily.Serif; mono -> FontFamily.Monospace; else -> FontFamily.SansSerif },
            fontFeatureSettings = "tnum",
            lineBreak = if (size >= 22) LineBreak.Heading else LineBreak.Paragraph,
            hyphens = Hyphens.Auto,
            textAlign = align,
        ),
    )
}
