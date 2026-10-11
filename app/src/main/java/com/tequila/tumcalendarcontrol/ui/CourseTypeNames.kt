/*
 * Aufgabe: Lehrtyp-Anzeigenamen speichern und Umbenennungsdialog bereitstellen.
 * Ein-/Ausgabe: Visuelle Gruppenschlüssel und Namen -> gemeinsame Anzeigenamen.
 * Nebenwirkungen: Nur private Namenspräferenzen; Oberfläche meldet Auswahl über Rückrufe.
 * Nicht zuständig: Quellentitel, Kurszugehörigkeit, Teilnahmestatus oder Kalenderänderungen.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalConfiguration
import top.yukonga.miuix.kmp.basic.*

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

@Composable
internal fun CourseTypeRenameButton(type: CourseTeachingTypeGroup) {
    val names = LocalCourseTypeNames.current
    var editing by remember { mutableStateOf(false) }
    var value by remember(type.label) { mutableStateOf(type.label) }
    val configuration = LocalConfiguration.current
    Button(onClick = { value = type.label; editing = true }) { Text(tr(message(383))) }
    if (editing) Dialog(onDismissRequest = { editing = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.width((configuration.screenWidthDp - 48).coerceIn(200, 480).dp).height((configuration.screenHeightDp * 0.72f).coerceAtMost(480f).dp), insideMargin = PaddingValues(16.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr(message(384)))
                Text(tr(message(385)))
                TextField(value, { value = it }, Modifier.fillMaxWidth(), label = tr(message(386)))
                Button(onClick = { names.change(type.key, value.trim()); editing = false }, enabled = validCourseTypeName(value), modifier = Modifier.fillMaxWidth()) { Text(tr(message(387))) }
                Button(onClick = { names.change(type.key, null); editing = false }, modifier = Modifier.fillMaxWidth()) { Text(tr(message(388))) }
                Button(onClick = { editing = false }, modifier = Modifier.fillMaxWidth()) { Text(tr(message(197))) }
            }
        }
    }
}
