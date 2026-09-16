/*
 * 职责：以固定合成状态记录并校验关键 Compose 页面截图，防止既有布局回归。
 * 输入／输出：输入内存 CalendarControlState 与固定 Robolectric 尺寸；输出已确认的 PNG 基线或差异报告。
 * 外部副作用：仅在显式 Roborazzi 记录任务写入 src/test/screenshots；不读取网络、账户、iCal 或日历。
 * 不负责：荣耀真机状态栏、Google 授权、本机 Calendar Provider 或真实日历写入验证。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.captureRoboImage
import com.tequila.tumcalendarcontrol.sync.IcalReadOnlyImportResult
import com.tequila.tumcalendarcontrol.sync.IcalReadOnlyPreview
import com.tequila.tumcalendarcontrol.sync.PrivateSyncConfiguration
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceCourse
import com.tequila.tumcalendarcontrol.sync.SourceOutputDateWindow
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarControlScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
    fun `首页浅色 顶栏与底栏保持安全间距`() {
        val state = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT }

        render(state)
        capture("overview_light_412x892")
    }

    @Test
    @Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
    fun `首页深色 顶栏与底栏保持安全间距`() {
        val state = CalendarControlState().also { it.themeMode = ThemeMode.DARK }

        render(state)
        capture("overview_dark_412x892")
    }

    @Test
    @Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
    fun `读取时间页可滚动至下一步`() {
        val state = CalendarControlState().also { it.openSourceRange() }

        render(state)
        composeRule.onNodeWithText("读取后先选择要管理的课程，再设置出席和收藏。写入前会显示本机和 Google 日历将受影响的数量；确认后才会写入。").performScrollTo()
        capture("source_range_bottom_reachable_412x892")
    }

    @Test
    @Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
    fun `课程选择 固定操作区与页面内容分离`() {
        render(loadedSourceState())
        capture("source_courses_selector_412x892")
    }

    @Test
    @Config(sdk = [36], qualifiers = "w840dp-h892dp-420dpi")
    fun `展开态课程选择 左侧控制浮块与右侧课次列表独立`() {
        render(loadedSourceState())
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val list = composeRule.onNodeWithTag("source-list-viewport").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("列表应延伸到窗口底部安全区，不为左侧浮块整页裁短", list.bottom > root.bottom - root.height * 0.04f)
        capture("source_courses_selector_wide_840x892")
    }

    @Test
    @Config(sdk = [36], qualifiers = "w840dp-h892dp-420dpi")
    fun `深蓝配色与右侧展开目录`() {
        val state = CalendarControlState().also { it.themeMode = ThemeMode.DARK; it.darkPaletteStyle = DarkPaletteStyle.DEEP_BLUE }
        render(state)
        composeRule.onAllNodes(androidx.compose.ui.test.hasContentDescription("展开课程目录"))[0].performClick()
        capture("overview_deep_blue_directory_840x892")
    }

    @Test
    @Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
    fun `浅色暖米白配色`() {
        val state = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT; it.lightPaletteStyle = LightPaletteStyle.WARM_WHITE }
        render(state)
        capture("overview_warm_white_412x892")
    }

    @Test
    @Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
    fun `外观颜色选择可滚动到达`() {
        val state = CalendarControlState().also { it.open(AppScreen.SETTINGS) }
        render(state)
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasText("深蓝"))
        composeRule.onNodeWithTag("settings-list").performTouchInput { swipeUp() }
        capture("appearance_choices_412x892")
    }

    @Test
    @Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
    fun `长列表在不透明浮块后延伸而非被整条裁断`() {
        val template = com.tequila.tumcalendarcontrol.domain.SampleCourses.all().first()
        val state = CalendarControlState(initialCourses = (1..30).map { template.copy(id = "synthetic-$it", title = "合成课程 ${it.toString().padStart(2, '0')}") })
        render(state)
        capture("overview_floating_overlay_long_412x892")
        composeRule.onNodeWithTag("course-browser-list").performScrollToNode(hasText("合成课程 30"))
        composeRule.onNodeWithTag("course-browser-list").performTouchInput { swipeUp() }
        val lastCourse = composeRule.onNodeWithText("合成课程 30").fetchSemanticsNode().boundsInRoot
        val navigation = composeRule.onNodeWithTag("floating-navigation").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("末条课程应能滚至浮块上方：$lastCourse / $navigation", lastCourse.bottom < navigation.top)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
    fun `按日期选课 月格显示完整`() {
        val state = loadedSourceState()

        renderDateBrowser(state)
        capture("source_courses_dates_412x892")
    }

    @Test
    @Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
    fun `设置页底栏选中块与四个槽位对齐`() {
        val state = CalendarControlState().also { it.open(AppScreen.SETTINGS) }

        render(state)
        capture("bottom_navigation_settings_412x892")
    }

    @Test
    @Config(sdk = [36], qualifiers = "w840dp-h892dp-420dpi")
    fun `宽屏首页 保持完整窗口布局`() {
        val state = CalendarControlState().also { it.themeMode = ThemeMode.LIGHT }

        render(state)
        capture("overview_wide_840x892")
    }

    private fun render(state: CalendarControlState) {
        composeRule.setContent { CalendarControlSurface(state) }
        composeRule.waitForIdle()
    }

    /** 仅渲染日期选择组件，以稳定审查月格本身，不受来源选择页固定操作栏高度影响。 */
    private fun renderDateBrowser(state: CalendarControlState) {
        composeRule.setContent {
            TumCalendarTheme(mode = state.themeMode) {
                SourceCourseDateBrowser(
                    state = state,
                    outputWindow = state.privateSyncConfiguration.sourceOutputWindow ?: SourceOutputDateWindow(),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun capture(name: String) {
        // Compose 专用捕获支持 Miuix 的圆角 Shader；UI 树导出由项目 Gradle 配置固定关闭。
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun loadedSourceState(): CalendarControlState = CalendarControlState().also { state ->
        state.updatePrivateSyncConfiguration(
            PrivateSyncConfiguration(tumSubscriptionUrl = "https://synthetic.example.test/calendar"),
        )
        state.requestSourcePreview()
        state.resolveSourcePreview(state.sourceReadToken, IcalReadOnlyImportResult.Success(syntheticPreview()))
    }

    private fun syntheticPreview(): IcalReadOnlyPreview {
        val startsAt = Instant.parse("2026-10-12T08:00:00Z")
        val endsAt = Instant.parse("2026-10-12T10:00:00Z")
        return IcalReadOnlyPreview(
            sourceEventCount = 2,
            manageableOccurrenceCount = 2,
            skippedByReason = emptyMap(),
            firstManageableStart = startsAt,
            lastManageableEnd = endsAt,
            courses = listOf(
                SourceCourse("ui-course-a", "合成课程 A", "教室 A", startsAt, endsAt, "ui-a-1", 1, 0),
                SourceCourse("ui-course-b", "合成课程 B", "教室 B", startsAt.plusSeconds(86_400), endsAt.plusSeconds(86_400), "ui-b-1", 1, 0),
            ),
            occurrences = listOf(
                SourceCalendarOccurrence("ui-course-a", "ui-a-1", "合成课程 A", startsAt, endsAt, false, "教室 A"),
                SourceCalendarOccurrence("ui-course-b", "ui-b-1", "合成课程 B", startsAt.plusSeconds(86_400), endsAt.plusSeconds(86_400), false, "教室 B"),
            ),
        )
    }
}
