/*
 * 职责：组合主题、顶栏、底部导航、页面内容和侧边导航菜单。
 * 输入／输出：输入内存中的 CalendarControlState；输出整个离线应用界面。
 * 外部副作用：无；所有操作仅更新当前 Compose 进程内的状态。
 * 不负责：持久化、账户授权、私有 iCal 或 Android Calendar Provider。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CalendarControlApp() {
    val state = remember { CalendarControlState() }
    TumCalendarTheme {
        Box(Modifier.fillMaxSize()) {
            CalendarControlScaffold(state)
            NavigationMenu(state)
        }
    }
}

@Composable
private fun CalendarControlScaffold(state: CalendarControlState) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = state.screen.title(),
                subtitle = state.screen.subtitle() ?: "",
                navigationIcon = {
                    IconButton(onClick = { state.isMenuOpen = true }) {
                        Icon(Icons.Rounded.Menu, "打开导航菜单")
                    }
                },
                actions = {
                    IconButton(onClick = { state.open(AppScreen.MORE) }) {
                        Icon(Icons.Rounded.MoreVert, "更多操作")
                    }
                },
            )
        },
        bottomBar = {
            if (state.screen.usesBottomNavigation()) AppBottomNavigation(state)
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    PaddingValues(
                        start = 16.dp,
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        end = 16.dp,
                        bottom = innerPadding.calculateBottomPadding() + 8.dp,
                    ),
                ),
        ) {
            AppScreenContent(state)
        }
    }
}

@Composable
private fun AppBottomNavigation(state: CalendarControlState) {
    NavigationBar(modifier = Modifier.fillMaxWidth()) {
        NavigationBarItem(
            selected = state.screen == AppScreen.OVERVIEW,
            onClick = { state.open(AppScreen.OVERVIEW) },
            icon = Icons.Rounded.Home,
            label = "首页",
        )
        NavigationBarItem(
            selected = state.screen == AppScreen.SEARCH,
            onClick = { state.open(AppScreen.SEARCH) },
            icon = Icons.Rounded.Search,
            label = "搜索",
        )
        NavigationBarItem(
            selected = state.screen == AppScreen.FAVORITES,
            onClick = { state.open(AppScreen.FAVORITES) },
            icon = Icons.Rounded.Favorite,
            label = "收藏",
        )
        NavigationBarItem(
            selected = state.screen == AppScreen.SETTINGS,
            onClick = { state.open(AppScreen.SETTINGS) },
            icon = Icons.Rounded.Settings,
            label = "设置",
        )
    }
}

@Composable
private fun NavigationMenu(state: CalendarControlState) {
    AnimatedVisibility(
        visible = state.isMenuOpen,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TumColors.onSurface.copy(alpha = 0.32f))
                .clickable { state.isMenuOpen = false },
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(0.72f)
                    .fillMaxSize()
                    .background(TumColors.surface)
                    .padding(horizontal = 16.dp, vertical = 28.dp),
            ) {
                Text("导航菜单", style = MiuixTheme.textStyles.title1)
                Text("仅显示已获 Fixplatz 的课程", color = TumColors.onSurfaceVariant)
                NavigationMenuItem("首页", Icons.Rounded.Home) { state.open(AppScreen.OVERVIEW) }
                NavigationMenuItem("搜索", Icons.Rounded.Search) { state.open(AppScreen.SEARCH) }
                NavigationMenuItem("收藏", Icons.Rounded.Favorite) { state.open(AppScreen.FAVORITES) }
                NavigationMenuItem("设置与同步", Icons.Rounded.Settings) { state.open(AppScreen.SETTINGS) }
            }
        }
    }
}

@Composable
private fun NavigationMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label)
        Text(label, modifier = Modifier.padding(start = 16.dp), style = MiuixTheme.textStyles.body1)
    }
}

private fun AppScreen.usesBottomNavigation(): Boolean = this in setOf(
    AppScreen.OVERVIEW,
    AppScreen.SEARCH,
    AppScreen.FAVORITES,
    AppScreen.SETTINGS,
)

private fun AppScreen.title(): String = when (this) {
    AppScreen.OVERVIEW -> "TUM 日历控制"
    AppScreen.DETAIL -> "课程详情"
    AppScreen.ATTENDANCE -> "状态选择"
    AppScreen.SEARCH -> "搜索课程"
    AppScreen.FAVORITES -> "收藏课程"
    AppScreen.MORE -> "更多操作"
    AppScreen.SETTINGS -> "设置与同步"
}

private fun AppScreen.subtitle(): String? = when (this) {
    AppScreen.OVERVIEW -> "离线原型 · 仅合成数据"
    AppScreen.SETTINGS -> "本轮不连接任何真实日历"
    else -> null
}
