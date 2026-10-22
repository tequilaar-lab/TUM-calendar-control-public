/*
 * Aufgabe: Testgalerie aller Bausteine des neuen Erscheinungsbilds mit synthetischen Inhalten.
 * Ein-/Ausgabe: Keine Eingaben -> Compose-Seite mit Inhaltsebene, schwebender Schaltfläche und Glasnavigation.
 * Nebenwirkungen: Keine; nur für Bildtests, nicht Teil der App.
 * Nicht zuständig: Fachzustand, echte Kurse, Navigation oder Übersetzungsprüfung.
 */
package com.tequila.tumcalendarcontrol.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** Synthetische Galerie; Reihenfolge entspricht dem bestätigten Entwurf, damit Bildvergleiche nebeneinander lesbar sind. */
@Composable
internal fun DesignGallery(showNavigation: Boolean = true) {
    val colors = LocalDesignColors.current
    val content = rememberLayerBackdrop()
    var choice by remember { mutableIntStateOf(1) }
    var checked by remember { mutableStateOf(true) }
    var segment by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    CompositionLocalProvider(LocalGlassBackdrop provides content) {
        Box(Modifier.fillMaxSize().background(colors.paper)) {
            Column(Modifier.fillMaxSize().layerBackdrop(content).background(colors.paper).verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp).padding(top = 24.dp, bottom = 160.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DesignText("COMPONENTS / DESIGN", 10, muted = true, tracking = .8f, mono = true)
                DesignText("设计组件", 30)
                DesignLine()
                DesignSection("出席状态", index = "03")
                listOf("必去", "选择出席", "不去").forEachIndexed { i, label -> DesignChoice(label, choice == i, detail = "合成说明") { choice = i } }
                DesignCheck("本机日历", checked, detail = "本设备上的应用专用日历") { checked = it }
                DesignSegmented(listOf("跟随系统", "浅色", "深色"), segment) { segment = it }
                DesignField("搜索课程", query, { query = it }, placeholder = "德语课名、中文别名或地点", search = true)
                DesignField("事项名称", "项目小组讨论", {})
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DesignPillButton("从今天起", Modifier.weight(1f), primary = false) {}
                    DesignPillButton("导出备份", Modifier.weight(1f)) {}
                }
                DesignRow("读取与选择课程", detail = "继续使用已保存的 133 个课次", value = "133")
                DesignRow("清理已写入日程", detail = "操作前再次确认", danger = true)
                DesignNote("主日历和其他应用日程不会受影响", "只处理本应用创建的日程。")
                DesignNote("本次读取未完成", "连接超时。已保存课程仍可继续使用。", error = true)
                DesignFrostCard {
                    DesignSection("本机日历", index = "已完成")
                    DesignMetrics("新建" to "121", "更新" to "10", "核对" to "2", "失败" to "0")
                }
                DesignText("Messsystem- und Sensortechnik", 28, serif = true)
                DesignText("同一批课次写入两本日历；不是 266 个不同课次。", 12, muted = true)
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                // Echte Seiten zeigen entweder Hauptaktion oder Navigation; hier beide mit Abstand übereinander.
                DesignFloatingButton("下一步 · 出席与收藏", enabled = true, modifier = Modifier.padding(horizontal = 28.dp).padding(bottom = 12.dp).fillMaxWidth().testTag("design-floating")) {}
                if (showNavigation) DesignGlassNavigation(
                    listOf(DesignNavigationItem(Icons.Rounded.Home, "首页"), DesignNavigationItem(Icons.Rounded.Search, "搜索"),
                        DesignNavigationItem(Icons.Rounded.FavoriteBorder, "收藏"), DesignNavigationItem(Icons.Rounded.Settings, "设置")),
                    selected = 3, modifier = Modifier.testTag("design-navigation"),
                ) {}
            }
        }
    }
}
