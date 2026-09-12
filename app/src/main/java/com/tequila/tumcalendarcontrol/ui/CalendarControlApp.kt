/*
 * 职责：组合主题、固定顶栏、浮动主导航、全屏更多覆盖层与侧边导航菜单。
 * 输入／输出：输入内存中的 CalendarControlState；输出整个离线 Compose-Miuix 应用界面。
 * 外部副作用：无；所有操作仅更新当前 Compose 进程内的状态。
 * 不负责：持久化、账户授权、私有 iCal、Android Calendar Provider 或真实日历写入。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val FixedTopBarHeight = 96.dp
private val FloatingNavigationShape = RoundedCornerShape(30.dp)

@Composable
fun CalendarControlApp() {
    val state = remember { CalendarControlState() }
    BackHandler(enabled = state.canHandleBack()) { state.handleBack() }
    TumCalendarTheme(mode = state.themeMode) {
        Box(Modifier.fillMaxSize()) {
            CalendarControlScaffold(state)
            NavigationMenu(state)
            MoreOverlay(state)
        }
    }
}

@Composable
private fun CalendarControlScaffold(state: CalendarControlState) {
    val contentScreen = state.contentScreen
    Scaffold(
        topBar = {
            FixedAppTopBar(
                title = contentScreen.title(),
                subtitle = contentScreen.subtitle() ?: "\u00A0",
                navigationIcon = {
                    if (contentScreen.usesBackArrow()) {
                        TactileIconButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回上一页",
                            onClick = state::handleBack,
                        )
                    } else {
                        TactileIconButton(
                            icon = Icons.Rounded.Menu,
                            contentDescription = "打开导航菜单",
                            onClick = { state.isMenuOpen = true },
                        )
                    }
                },
                actions = {
                    TactileIconButton(
                        icon = Icons.Rounded.MoreVert,
                        contentDescription = "更多操作",
                        onClick = state::openMore,
                    )
                },
            )
        },
        bottomBar = {
            if (contentScreen.usesBottomNavigation()) AppBottomNavigation(state, contentScreen)
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
            AnimatedContent(
                targetState = contentScreen,
                transitionSpec = {
                    if (state.navigationDirection == NavigationDirection.FORWARD) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 3 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                    }
                },
                label = "主页面切换",
            ) { screen ->
                AppScreenContent(state, screen)
            }
        }
    }
}

/** 顶栏拥有固定总高度，空副标题保留空间，从而避免主页面横移时内容上下跳动。 */
@Composable
private fun FixedAppTopBar(
    title: String,
    subtitle: String,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(FixedTopBarHeight)) {
        TopAppBar(
            title = title,
            subtitle = subtitle,
            navigationIcon = navigationIcon,
            actions = actions,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun AppBottomNavigation(state: CalendarControlState, selectedScreen: AppScreen) {
    val palette = LocalTumPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(12.dp, FloatingNavigationShape)
            .clip(FloatingNavigationShape)
            .background(palette.surfaceContainer.copy(alpha = 0.88f))
            .border(1.dp, palette.outline.copy(alpha = 0.35f), FloatingNavigationShape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TactileNavigationItem(
            Icons.Rounded.Home, "首页", selectedScreen == AppScreen.OVERVIEW,
            onClick = { state.open(AppScreen.OVERVIEW) },
        )
        TactileNavigationItem(
            Icons.Rounded.Search, "搜索", selectedScreen == AppScreen.SEARCH,
            onClick = { state.open(AppScreen.SEARCH) },
        )
        TactileNavigationItem(
            Icons.Rounded.Favorite, "收藏", selectedScreen == AppScreen.FAVORITES,
            onClick = { state.open(AppScreen.FAVORITES) },
        )
        TactileNavigationItem(
            Icons.Rounded.Settings, "设置", selectedScreen == AppScreen.SETTINGS,
            onClick = { state.open(AppScreen.SETTINGS) },
        )
    }
}

/** 更多操作以独立覆盖层覆盖完整内容区，避免底栏切换动画与它混在一起。 */
@Composable
private fun MoreOverlay(state: CalendarControlState) {
    val palette = LocalTumPalette.current
    AnimatedVisibility(
        visible = state.isMoreOpen,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.surface),
        ) {
            FixedAppTopBar(
                title = AppScreen.MORE.title(),
                subtitle = "\u00A0",
                navigationIcon = {
                    TactileIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "关闭更多操作",
                        onClick = state::handleBack,
                    )
                },
                actions = {},
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                MoreScreen(state)
            }
        }
    }
}

@Composable
private fun NavigationMenu(state: CalendarControlState) {
    val palette = LocalTumPalette.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val drawerWidth = maxWidth / 3
        if (state.isMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.onSurface.copy(alpha = 0.32f))
                    .clickable { state.isMenuOpen = false },
            )
        }
        AnimatedVisibility(
            visible = state.isMenuOpen,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
        ) {
            CompositionLocalProvider(LocalContentColor provides palette.onSurface) {
                Column(
                    modifier = Modifier
                        .width(drawerWidth)
                        .fillMaxSize()
                        .background(palette.surface)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                ) {
                    Text("导航", style = MiuixTheme.textStyles.title3)
                    Text("Fixplatz", color = palette.onSurfaceVariant, style = MiuixTheme.textStyles.body2)
                    NavigationMenuItem("首页", Icons.Rounded.Home) { state.open(AppScreen.OVERVIEW) }
                    NavigationMenuItem("搜索", Icons.Rounded.Search) { state.open(AppScreen.SEARCH) }
                    NavigationMenuItem("收藏", Icons.Rounded.Favorite) { state.open(AppScreen.FAVORITES) }
                    NavigationMenuItem("设置", Icons.Rounded.Settings) { state.open(AppScreen.SETTINGS) }
                }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label)
        Text(label, modifier = Modifier.padding(start = 12.dp), style = MiuixTheme.textStyles.body2)
    }
}

private fun AppScreen.usesBottomNavigation(): Boolean = this in setOf(
    AppScreen.OVERVIEW,
    AppScreen.SEARCH,
    AppScreen.FAVORITES,
    AppScreen.SETTINGS,
)

private fun AppScreen.usesBackArrow(): Boolean = this in setOf(
    AppScreen.DETAIL,
    AppScreen.ATTENDANCE,
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
