/*
 * Aufgabe: Inhaltsbausteine des neuen Erscheinungsbilds: Abschnittstitel, Hinweise, Milchglaskarten, Kennzahlen und einfache Seite.
 * Ein-/Ausgabe: Lokalisierte Texte und Inhalte -> Miuix Card mit Milchglasmaterial und typografische Bausteine.
 * Nebenwirkungen: Keine.
 * Nicht zuständig: Schwebende Glasflächen (DesignFloating), Fachdaten oder Übersetzung.
 */
package com.tequila.tumcalendarcontrol.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults

/** Kleiner Abschnittstitel mit optionalem rechtsbündigem Index (z. B. Anzahl). */
@Composable
fun DesignSection(title: String, modifier: Modifier = Modifier, index: String = "") {
    Row(modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        DesignText(title, 12, muted = true, tracking = .6f)
        if (index.isNotEmpty()) DesignText(index, 10, muted = true, mono = true)
    }
}

/**
 * Milchglaskarte (Miuix Card): etwas heller als das Papier, halbtransparent, mit Glanzkante und abgeschwächtem Schatten.
 * Liegt in der Inhaltsebene und tastet daher nichts ab; „Aus“ fällt auf eine schlichte Fläche zurück.
 */
@Composable
fun DesignFrostCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalDesignColors.current
    val glass = LocalGlassLevel.current != GlassLevel.OFF
    val fill = when { !glass -> colors.wash; colors.isDark -> Color.White.copy(alpha = .06f); else -> colors.floating.copy(alpha = .78f) }
    Card(
        modifier = modifier.fillMaxWidth()
            .then(if (glass) Modifier.designGlow(DesignRadii.Block, colors, strength = .45f) else Modifier)
            .designSheen(RoundedCornerShape(DesignRadii.Block), colors, glass),
        cornerRadius = DesignRadii.Block,
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        colors = CardDefaults.defaultColors(color = fill, contentColor = colors.ink),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** Hinweis: Milchglaskarte mit Akzentleiste (Fehler: gedämpftes Rot), Titel und kurzer Erläuterung. */
@Composable
fun DesignNote(title: String, body: String, modifier: Modifier = Modifier, error: Boolean = false) {
    val colors = LocalDesignColors.current
    DesignFrostCard(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(3.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(if (error) colors.danger else colors.accent))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DesignText(title, 14, color = if (error) colors.danger else colors.ink)
                DesignText(body, 12, muted = true)
            }
        }
    }
}

/** Kennzahlenzeile: große, leichte Ziffern über kleinen Beschriftungen, gleich breit verteilt. */
@Composable
fun DesignMetrics(vararg items: Pair<String, String>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, value) ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                DesignText(value, 26, weight = FontWeight.Light)
                DesignText(label, 11, muted = true)
            }
        }
    }
}

/**
 * Scrollbare Seite ohne schwebende Hauptaktion (z. B. Hinweise, Beispiele): gleiche Ränder und Abstände wie
 * DesignActionPage, unten nur der normale Abstand zum Seitenende.
 */
@Composable
fun DesignPage(modifier: Modifier = Modifier, contentTag: String = "page-content", content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().testTag(contentTag).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}
