/*
 * 职责：渲染应用固定外壳：顶栏、主页面动效、浮动底栏与“更多操作”覆盖层。
 * 输入／输出：输入 CalendarControlState 的纯 UI 状态与事件回调；输出 Compose-Miuix 布局。
 * 外部副作用：无；所有状态变化经 CalendarControlState 的已有用户操作入口表达。
 * 不负责：装配 Android 权限／文件／网络依赖、读取 iCal、授权 Google 或写入日历。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.ui.platform.testTag

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar

private val FixedTopBarHeight = 136.dp
private val FloatingNavigationShape = RoundedCornerShape(30.dp)
/** 仅用于滚动内容末尾，不能作为外层视口 padding；系统安全区另行处理。 */
internal val FloatingNavigationContentClearance = 100.dp

/** 主页面外壳不保留无实际功能的更多操作入口。 */
@Composable
internal fun CalendarControlChrome(state: CalendarControlState) {
    CalendarControlScaffold(state)
}

@Composable
private fun CalendarControlScaffold(state: CalendarControlState) {
    val savedPages = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val contentScreen = state.contentScreen
    val showsFloatingNavigation = contentScreen.usesBottomNavigation()
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
                        TactileProfileButton(
                            profile = state.profile,
                            onClick = state::openProfile,
                        )
                    }
                },
                actions = {},
            )
        },
        // 底栏叠放在内容之上，避免 Scaffold 为它铺设整条深色背景，也避免资料页返回时改变内容高度。
        bottomBar = {},
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    PaddingValues(
                        start = 16.dp,
                        top = innerPadding.calculateTopPadding() + 16.dp,
                        end = 16.dp,
                        // Scaffold 已提供系统导航区；浮块不再重复叠加 navigationBarsPadding。
                        bottom = innerPadding.calculateBottomPadding() + 8.dp,
                    ),
                ),
        ) {
            Box(
                modifier = Modifier
                    // 页面视口始终完整；浮块避让仅放在滚动内容末尾，不裁短整页。
                    .fillMaxSize(),
            ) {
                AnimatedContent(
                    targetState = contentScreen,
                    transitionSpec = {
                        when (state.navigationDirection) {
                            NavigationDirection.FORWARD -> (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                            NavigationDirection.BACKWARD -> (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                            NavigationDirection.PROFILE_ENTER -> (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                            // 资料页向左退出，进入前的主页面完整地从右侧进入；不再因底栏出现而纵向重排。
                            NavigationDirection.PROFILE_EXIT -> (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                        }
                    },
                    label = "主页面切换",
                ) { screen ->
                    savedPages.SaveableStateProvider(screen.name) { AppScreenContent(state, screen) }
                }
            }
            if (showsFloatingNavigation) {
                AppBottomNavigation(
                    state = state,
                    selectedScreen = contentScreen,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/** 顶栏预留状态栏安全区和固定内容高度，避免图标侵入通知栏或正文侵入副标题。 */
@Composable
private fun FixedAppTopBar(
    title: String,
    subtitle: String,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(FixedTopBarHeight)
            .statusBarsPadding(),
    ) {
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
private fun AppBottomNavigation(
    state: CalendarControlState,
    selectedScreen: AppScreen,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTumPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("floating-navigation")
            .shadow(12.dp, FloatingNavigationShape)
            .clip(FloatingNavigationShape)
            .background(palette.surfaceContainer)
            .border(1.dp, palette.outline.copy(alpha = 0.35f), FloatingNavigationShape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val itemWidth = maxWidth / 4
            val selectedOffset = animateDpAsState(
                targetValue = itemWidth * selectedScreen.bottomNavigationIndex(),
                label = "底栏选中块滑动",
            ).value
            Box(
                modifier = Modifier
                    .offset(x = selectedOffset)
                    .width(itemWidth)
                    .height(60.dp)
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(palette.primaryContainer.copy(alpha = 0.82f)),
            )
            Box(Modifier.fillMaxWidth()) {
                // 四个等宽槽位与指示器使用同一 itemWidth，避免依赖冲突造成对齐漂移。
                TactileNavigationItem(
                    Icons.Rounded.Home,
                    "首页",
                    selectedScreen == AppScreen.OVERVIEW,
                    onClick = { state.open(AppScreen.OVERVIEW) },
                    modifier = Modifier.width(itemWidth).offset(x = itemWidth * 0),
                )
                TactileNavigationItem(
                    Icons.Rounded.Search,
                    "搜索",
                    selectedScreen == AppScreen.SEARCH,
                    onClick = { state.open(AppScreen.SEARCH) },
                    modifier = Modifier.width(itemWidth).offset(x = itemWidth * 1),
                )
                TactileNavigationItem(
                    Icons.Rounded.Favorite,
                    "收藏",
                    selectedScreen == AppScreen.FAVORITES,
                    onClick = { state.open(AppScreen.FAVORITES) },
                    modifier = Modifier.width(itemWidth).offset(x = itemWidth * 2),
                )
                TactileNavigationItem(
                    Icons.Rounded.Settings,
                    "设置",
                    selectedScreen == AppScreen.SETTINGS,
                    onClick = { state.open(AppScreen.SETTINGS) },
                    modifier = Modifier.width(itemWidth).offset(x = itemWidth * 3),
                )
            }
        }
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
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                MoreScreen(state)
            }
        }
    }
}
