/*
 * Aufgabe: Lehrtyp-Anzeigenamen speichern und Umbenennungslink mit Dialog im neuen Erscheinungsbild bereitstellen.
 * Ein-/Ausgabe: Visuelle Gruppenschlüssel und Namen -> gemeinsame Anzeigenamen.
 * Nebenwirkungen: Nur private Namenspräferenzen; Oberfläche meldet Auswahl über Rückrufe.
 * Nicht zuständig: Quellentitel, Kurszugehörigkeit, Teilnahmestatus oder Kalenderänderungen.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import android.content.Context
import com.tequila.tumcalendarcontrol.ui.design.hapticClickable
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.tequila.tumcalendarcontrol.ui.design.DesignField
import com.tequila.tumcalendarcontrol.ui.design.DesignPillButton
import com.tequila.tumcalendarcontrol.ui.design.DesignRadii
import com.tequila.tumcalendarcontrol.ui.design.DesignText
import com.tequila.tumcalendarcontrol.ui.design.LocalDesignColors
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults

internal class CourseTypeNamesPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("course_type_names", Context.MODE_PRIVATE)
    fun load(): Map<String, String> = preferences.all.mapNotNull { (key, value) ->
        (value as? String)?.takeIf(::validCourseTypeName)?.let { key to it }
    }.toMap()
    fun save(names: Map<String, String>) {
        val editor = preferences.edit().clear()
        names.filterValues(::validCourseTypeName).forEach { (key, value) -> editor.putString(key, value.trim()) }
        editor.apply()
    }
}

internal fun validCourseTypeName(value: String): Boolean = value.trim().isNotEmpty() && value.trim().length <= 30 && value.none { it.isISOControl() }
internal data class CourseTypeNames(val names: Map<String, String> = emptyMap(), val change: (String, String?) -> Unit = { _, _ -> })
internal val LocalCourseTypeNames = staticCompositionLocalOf { CourseTypeNames() }

/**
 * Umbenennen als kleiner Akzent-Textlink (Tippfläche mindestens 48 × 48 dp); der Dialog nutzt die Bausteine des Designsystems.
 * Ändert nur den Anzeigenamen des Lehrtyps, nie Kursidentität oder Quelle.
 */
@Composable
internal fun CourseTypeRenameButton(type: CourseTeachingTypeGroup) {
    val names = LocalCourseTypeNames.current
    val colors = LocalDesignColors.current
    var editing by remember { mutableStateOf(false) }
    var value by remember(type.label) { mutableStateOf(type.label) }
    val configuration = LocalConfiguration.current
    DesignText(
        tr(message(383)), 12, color = colors.accent, weight = FontWeight.Medium,
        modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).hapticClickable { value = type.label; editing = true }
            .wrapContentSize().padding(horizontal = 4.dp).testTag("course-type-rename"),
    )
    if (editing) Dialog(onDismissRequest = { editing = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Fester Papiergrund statt Glas: über dem Abdunkelungsschleier gibt es nichts abzutasten.
        Card(
            Modifier.width((configuration.screenWidthDp - 48).coerceIn(200, 480).dp).heightIn(max = (configuration.screenHeightDp * 0.72f).coerceAtMost(480f).dp),
            cornerRadius = DesignRadii.Block, insideMargin = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            colors = CardDefaults.defaultColors(color = colors.paper, contentColor = colors.ink),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DesignText(tr(message(384)), 18, serif = true)
                DesignText(tr(message(385)), 12, muted = true)
                DesignField(tr(message(386)), value, { value = it })
                DesignPillButton(tr(message(387)), enabled = validCourseTypeName(value)) { names.change(type.key, value.trim()); editing = false }
                DesignPillButton(tr(message(388)), primary = false) { names.change(type.key, null); editing = false }
                DesignPillButton(tr(message(197)), primary = false) { editing = false }
            }
        }
    }
}
