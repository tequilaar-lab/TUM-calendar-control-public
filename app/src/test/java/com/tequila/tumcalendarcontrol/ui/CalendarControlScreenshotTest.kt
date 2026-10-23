/*
 * Aufgabe: feste synthetische Compose-Ansichten aufnehmen und auf Layoutregression prüfen.
 * Ein-/Ausgabe: feste Robolectric-Größen und Speicherzustände -> PNG-Baselines/Differenzberichte.
 * Nebenwirkungen: nur ausdrücklich gestartete Bildaufnahme schreibt src/test/screenshots.
 * Nicht zuständig: echte HONOR-Systemleisten, Google-Autorisierung oder Calendar-Provider-Ausgabe.
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
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
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.*
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
    fun `深蓝配色与展开屏右侧课程详情`() {
        // Neue Übersicht: breit links Verzeichnis, rechts aktueller Kurs (früher rechter Klappbaum „展开课程目录“).
        val state = CalendarControlState().also { it.themeMode = ThemeMode.DARK; it.darkPaletteStyle = DarkPaletteStyle.DEEP_BLUE }
        render(state)
        composeRule.onNodeWithText("课程 B").performClick()
        composeRule.onNodeWithTag("overview-detail-title").assertTextEquals("课程 B")
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
        // Farben liegen seit dem neuen Erscheinungsbild auf der eigenen Darstellungsseite.
        val state = CalendarControlState().also { it.open(AppScreen.APPEARANCE) }
        render(state)
        composeRule.onNodeWithTag("appearance-content").performScrollToNode(hasText("深蓝"))
        composeRule.onNodeWithTag("appearance-content").performTouchInput { swipeUp() }
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

    @Test
    @Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
    fun `已有课程的时间页可直接继续而不重新读取`() {
        val state = loadedSourceState()
        state.openSourceRange()
        render(state)
        // Seit dem neuen Erscheinungsbild schwebt „weiter“ als Hauptaktion und ist ohne Scrollen sichtbar.
        composeRule.onNodeWithText("使用已保存课程 · 下一步").assertIsDisplayed()
        capture("source_range_cached_next_412x892")
        composeRule.onNodeWithText("使用已保存课程 · 下一步").performClick()
        assertEquals(AppScreen.SOURCE_PREVIEW, state.screen)
        assertFalse(state.sourceReadRequested)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
    fun `分别设置可切换且取消不保存`() {
        val state = loadedSourceState()
        state.openSourceAttendanceEditor(state.currentSourceOccurrences.map { it.identity }.toSet())
        render(state)
        composeRule.onNodeWithText("按课程分别设置").performClick()
        composeRule.onAllNodes(hasText("单独设置"))[0].performClick()
        capture("attendance_individual_412x892")
        composeRule.onNodeWithText("统一设置").performClick()
        assertFalse(state.individualAttendanceMode)
        assertTrue(state.ruleLedger.rules.isEmpty())
    }

    @Test
    @Config(sdk = [36], qualifiers = "w412dp-h892dp-420dpi")
    fun `子类型可改名且取消不保存`() {
        val state = CalendarControlState()
        render(state)
        composeRule.onNodeWithText("课程 A").performClick()
        composeRule.onNodeWithText("改名").performClick()
        composeRule.onNode(androidx.compose.ui.test.isDialog()).captureRoboImage("src/test/screenshots/course_type_rename_412x892.png")
        composeRule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextReplacement("自定义类型")
        composeRule.onNodeWithText("取消").performClick()
        assertTrue(state.courseTypeNames.isEmpty())
        composeRule.onNodeWithText("改名").performClick()
        composeRule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextReplacement("自定义类型")
        composeRule.onNodeWithText("保存名称").performClick()
        assertEquals(listOf("自定义类型"), state.courseTypeNames.values.toList())
    }

    /** Datumsbrowser isoliert rendern, damit Aktionsleistenhöhe die Monatsrasterprüfung nicht beeinflusst. */
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
        // Compose-Bildaufnahme unterstützt Miuix-Shader; UI-Baumexport ist in Gradle ausgeschaltet.
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
