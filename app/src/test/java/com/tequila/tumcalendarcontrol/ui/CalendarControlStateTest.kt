/*
 * Aufgabe: synthetische Regressionstests für CalendarControlState.
 * Ein-/Ausgabe: künstliche Zustände und Fixtures -> JUnit-Prüfergebnisse.
 * Nebenwirkungen: nur lokaler Testprozess und gegebenenfalls Robolectric-Präferenzen.
 * Nicht zuständig: echte Konten, private Quellen, Geräte oder reale Kalenderausgaben.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.domain.AttendanceState
import com.tequila.tumcalendarcontrol.domain.CourseLocalOverride
import com.tequila.tumcalendarcontrol.domain.TumEventStatus
import com.tequila.tumcalendarcontrol.sync.PrivateSyncConfiguration
import com.tequila.tumcalendarcontrol.sync.IcalReadOnlyImportResult
import com.tequila.tumcalendarcontrol.sync.IcalReadOnlyPreview
import com.tequila.tumcalendarcontrol.sync.ManualScheduleItem
import com.tequila.tumcalendarcontrol.sync.RuleLedger
import com.tequila.tumcalendarcontrol.sync.SourceCourse
import com.tequila.tumcalendarcontrol.sync.SourceCalendarOccurrence
import com.tequila.tumcalendarcontrol.sync.SourceOccurrenceIdentity
import com.tequila.tumcalendarcontrol.sync.SourceCheckCadence
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarControlStateTest {
    @Test
    fun `确认状态会更新所选课程且回到详情`() {
        val state = CalendarControlState()

        state.openCourse("course-b")
        state.openAttendance()
        state.pendingAttendance = AttendanceState.MUST_ATTEND
        state.confirmAttendance()

        assertEquals(AppScreen.DETAIL, state.screen)
        assertEquals(AttendanceState.MUST_ATTEND, state.selectedCourse.attendance)
    }

    @Test
    fun `收藏切换只影响对应课程`() {
        val state = CalendarControlState()

        state.toggleFavorite("course-a")

        assertTrue(state.courses.first { it.id == "course-a" }.isFavorite)
        assertFalse(state.courses.first { it.id == "course-c" }.isFavorite)
    }

    @Test
    fun `主标签页按相对位置使用相反方向动效`() {
        val state = CalendarControlState()

        state.open(AppScreen.SETTINGS)
        assertEquals(NavigationDirection.FORWARD, state.navigationDirection)
        state.open(AppScreen.FAVORITES)

        assertEquals(NavigationDirection.BACKWARD, state.navigationDirection)
    }

    @Test
    fun `范围页从设置向前进入且返回使用后退动效`() {
        val state = CalendarControlState()

        state.open(AppScreen.SETTINGS)
        state.openSourceRange()

        assertEquals(AppScreen.SOURCE_RANGE, state.screen)
        assertEquals(NavigationDirection.FORWARD, state.navigationDirection)
        assertTrue(state.handleBack())
        assertEquals(AppScreen.SETTINGS, state.screen)
        assertEquals(NavigationDirection.BACKWARD, state.navigationDirection)
    }

    @Test
    fun `后台检查需要私有来源且关闭会请求取消排程`() {
        val state = CalendarControlState()

        state.updateSourceCheck(true)
        assertFalse(state.sourceCheckSettings.enabled)
        assertTrue(state.feedback!!.contains("先保存课程订阅地址"))

        state.updatePrivateSyncConfiguration(PrivateSyncConfiguration(tumSubscriptionUrl = "https://synthetic.example.test/calendar"))
        state.updateSourceCheck(true, SourceCheckCadence.WEEKLY)
        assertTrue(state.sourceCheckSettings.enabled)
        assertEquals(SourceCheckCadence.WEEKLY, state.sourceCheckSettings.cadence)
        assertTrue(state.sourceCheckSchedulingRequested)
        state.consumeSourceCheckSchedulingRequest()
        state.updateSourceCheck(false)
        assertFalse(state.sourceCheckSettings.enabled)
        assertTrue(state.sourceCheckSchedulingRequested)
    }

    @Test
    fun `备份导入预览须确认且保留现有映射`() {
        val currentMapping = com.tequila.tumcalendarcontrol.sync.StoredEventMapping(
            com.tequila.tumcalendarcontrol.sync.OutputTarget.LOCAL_CALENDAR,
            com.tequila.tumcalendarcontrol.sync.ManagedEventIdentity("current", "instance"),
            "event-id",
            "fingerprint",
        )
        val state = CalendarControlState(initialRuleLedger = RuleLedger(mappings = listOf(currentMapping)))
        val backupText = com.tequila.tumcalendarcontrol.sync.RuleLedgerBackupCodec.encode(
            RuleLedger(rules = listOf(com.tequila.tumcalendarcontrol.sync.SyncRule("imported", false, AttendanceState.MUST_ATTEND))),
        )

        state.resolveRuleBackupImport(backupText)
        assertEquals(0, state.ruleLedger.rules.size)
        assertTrue(state.pendingRuleBackup != null)
        state.confirmRuleBackupImport()

        assertEquals(listOf("imported"), state.ruleLedger.rules.map { it.sourceUid })
        assertEquals(listOf(currentMapping), state.ruleLedger.mappings)
        assertFalse(state.managedSyncRequested)
    }

    @Test
    fun `重启后仅凭已持久化映射也可进入独立清理页`() {
        val mapping = com.tequila.tumcalendarcontrol.sync.StoredEventMapping(
            com.tequila.tumcalendarcontrol.sync.OutputTarget.LOCAL_CALENDAR,
            com.tequila.tumcalendarcontrol.sync.ManagedEventIdentity("synthetic-uid", "synthetic-instance"),
            "event-id",
            "fingerprint",
        )
        val state = CalendarControlState(initialRuleLedger = RuleLedger(mappings = listOf(mapping)))

        state.open(AppScreen.SETTINGS)
        state.openOutputCleanup()

        assertEquals(AppScreen.OUTPUT_CLEANUP, state.screen)
        assertEquals(null, state.managedSyncPreview)
        assertFalse(state.managedSyncRequested)
        assertTrue(state.handleBack())
        assertEquals(AppScreen.SETTINGS, state.screen)
    }

    @Test
    fun `状态页和详情页会按层级返回而非直接退出`() {
        val state = CalendarControlState()

        state.open(AppScreen.SEARCH)
        state.openCourse("course-a")
        state.openAttendance()

        assertTrue(state.handleBack())
        assertEquals(AppScreen.DETAIL, state.screen)
        assertTrue(state.handleBack())
        assertEquals(AppScreen.SEARCH, state.screen)
        assertFalse(state.canHandleBack())
    }

    @Test
    fun `主题模式可在本次运行内切换`() {
        val state = CalendarControlState()

        state.themeMode = ThemeMode.DARK

        assertEquals(ThemeMode.DARK, state.themeMode)
    }

    @Test
    fun `个人资料保存后返回原来的主页面`() {
        val state = CalendarControlState(initialProfile = UserProfile("初始", AvatarTone.SEA_GREEN))

        state.open(AppScreen.FAVORITES)
        state.openProfile()
        state.updateProfile(UserProfile("新昵称", AvatarTone.LAVENDER))

        assertTrue(state.handleBack())
        assertEquals(AppScreen.FAVORITES, state.screen)
        assertEquals("新昵称", state.profile.displayName)
        assertEquals(AvatarTone.LAVENDER, state.profile.avatarTone)
    }

    @Test
    fun `同步目标开关彼此独立且默认关闭`() {
        val state = CalendarControlState()

        assertFalse(state.syncDestinations.localCalendarEnabled)
        assertFalse(state.syncDestinations.googleCalendarEnabled)

        state.setSyncDestinationEnabled(SyncDestination.LOCAL_CALENDAR, true)

        assertTrue(state.syncDestinations.localCalendarEnabled)
        assertFalse(state.syncDestinations.googleCalendarEnabled)

        state.setSyncDestinationEnabled(SyncDestination.GOOGLE_CALENDAR, true)

        assertTrue(state.syncDestinations.localCalendarEnabled)
        assertTrue(state.syncDestinations.googleCalendarEnabled)
    }

    @Test
    fun `输出开关在确认前不会改变 并在确认后才生效`() {
        val state = CalendarControlState()

        state.requestSyncDestinationChange(SyncDestination.GOOGLE_CALENDAR, true)

        assertFalse(state.syncDestinations.googleCalendarEnabled)
        assertEquals(SyncDestination.GOOGLE_CALENDAR, state.pendingSyncDestinationChange?.destination)
        state.confirmSyncDestinationChange()
        assertTrue(state.syncDestinations.googleCalendarEnabled)
        assertEquals(null, state.pendingSyncDestinationChange)
    }

    @Test
    fun `同时启用两种输出会在确认前提示荣耀日历可能显示两份`() {
        val state = CalendarControlState()
        state.setSyncDestinationEnabled(SyncDestination.LOCAL_CALENDAR, true)

        state.requestSyncDestinationChange(SyncDestination.GOOGLE_CALENDAR, true)

        val change = requireNotNull(state.pendingSyncDestinationChange)
        assertTrue(change.consequence(state.syncDestinations).contains("两份"))
        assertFalse(state.syncDestinations.googleCalendarEnabled)
    }

    @Test
    fun `Google 授权状态会立即显示成功失败并在关闭输出时清除令牌`() {
        val state = CalendarControlState()
        state.setSyncDestinationEnabled(SyncDestination.GOOGLE_CALENDAR, true)

        state.requestGoogleAuthorization()
        assertEquals(GoogleAuthorizationStatus.AUTHORIZING, state.googleAuthorizationStatus)
        assertFalse(state.hasGoogleAuthorization)

        state.resolveGoogleAuthorization(GoogleAuthorizationOutcome.Failure("Google 授权返回失败（服务状态码：10）。"))
        assertEquals(GoogleAuthorizationStatus.FAILED, state.googleAuthorizationStatus)
        assertTrue(state.googleAuthorizationDetail.contains("服务状态码：10"))
        assertFalse(state.hasGoogleAuthorization)

        state.requestGoogleAuthorization()
        state.resolveGoogleAuthorization(GoogleAuthorizationOutcome.AccessToken("synthetic-access-token"))
        assertEquals(GoogleAuthorizationStatus.AUTHORIZED, state.googleAuthorizationStatus)
        assertTrue(state.hasGoogleAuthorization)

        state.setSyncDestinationEnabled(SyncDestination.GOOGLE_CALENDAR, false)
        assertEquals(GoogleAuthorizationStatus.NOT_CONNECTED, state.googleAuthorizationStatus)
        assertFalse(state.hasGoogleAuthorization)
    }

    @Test
    fun `Google 401 会清除运行内授权并要求用户重新连接`() {
        val state = CalendarControlState()
        state.setSyncDestinationEnabled(SyncDestination.GOOGLE_CALENDAR, true)
        state.requestGoogleAuthorization()
        state.resolveGoogleAuthorization(GoogleAuthorizationOutcome.AccessToken("synthetic-access-token"))

        val summary = state.invalidateGoogleAuthorizationAfterUnauthorized()

        assertEquals(GoogleAuthorizationStatus.FAILED, state.googleAuthorizationStatus)
        assertFalse(state.hasGoogleAuthorization)
        assertTrue(summary.contains("HTTP 401"))
        assertTrue(summary.contains("重新连接"))
    }

    @Test
    fun `失效令牌缓存清理结果仅更新仍失效的授权状态`() {
        val state = CalendarControlState()
        state.setSyncDestinationEnabled(SyncDestination.GOOGLE_CALENDAR, true)
        state.requestGoogleAuthorization()
        state.resolveGoogleAuthorization(GoogleAuthorizationOutcome.AccessToken("synthetic-access-token"))
        state.invalidateGoogleAuthorizationAfterUnauthorized()

        state.resolveRejectedGoogleTokenCacheClear(cleared = true)

        assertTrue(state.googleAuthorizationDetail.contains("缓存已清除"))
        assertFalse(state.hasGoogleAuthorization)
    }

    @Test
    fun `保存同步配置后返回设置且不隐式打开输出`() {
        val state = CalendarControlState()

        state.open(AppScreen.SETTINGS)
        state.openSyncConfiguration()
        state.updatePrivateSyncConfiguration(
            PrivateSyncConfiguration(tumSubscriptionUrl = "https://calendar.example.test/subscription"),
        )

        assertTrue(state.handleBack())
        assertEquals(AppScreen.SETTINGS, state.screen)
        assertFalse(state.syncDestinations.localCalendarEnabled)
        assertFalse(state.syncDestinations.googleCalendarEnabled)
    }

    @Test
    fun `未配置来源时不发起只读读取`() {
        val state = CalendarControlState()

        state.requestSourcePreview()

        assertFalse(state.sourceReadRequested)
        assertFalse(state.sourceReadInProgress)
        assertTrue(state.feedback!!.contains("尚未读取课程"))
    }

    @Test
    fun `只读请求忽略旧令牌结果并在当前失败后复位`() {
        val state = CalendarControlState()
        state.updatePrivateSyncConfiguration(PrivateSyncConfiguration(tumSubscriptionUrl = "https://synthetic.example.test/calendar"))
        state.requestSourcePreview()
        val token = state.sourceReadToken

        state.resolveSourcePreview(token + 1, IcalReadOnlyImportResult.Failure("旧结果"))
        assertTrue(state.sourceReadInProgress)

        state.resolveSourcePreview(token, IcalReadOnlyImportResult.Failure("超时"))
        assertFalse(state.sourceReadInProgress)
        assertTrue(state.feedback!!.contains("超时"))
    }

    @Test
    fun `本机课程覆盖不改写来源字段 且来源筛选不改变Fixplatz资格`() {
        val state = CalendarControlState()
        state.openCourse("course-b")

        state.updateSelectedCourseOverride(CourseLocalOverride(title = "本机名称"))
        state.toggleSourceStatusVisible(TumEventStatus.PLANNED)

        assertEquals("课程 B", state.selectedCourse.title)
        assertEquals("本机名称", state.selectedCourse.localOverride.title)
        assertFalse(state.searchedCourses.any { it.id == "course-b" })
        assertTrue(state.selectedCourse.hasFixedPlus)
    }

    @Test
    fun `本机日历权限拒绝保持关闭 同意只允许未来输出`() {
        val state = CalendarControlState()

        state.requestLocalCalendarOutput()
        assertTrue(state.localCalendarPermissionRequestPending)
        state.resolveLocalCalendarPermission(false)
        assertFalse(state.syncDestinations.localCalendarEnabled)

        state.requestLocalCalendarOutput()
        state.resolveLocalCalendarPermission(true)
        assertTrue(state.syncDestinations.localCalendarEnabled)
        assertTrue(state.feedback!!.contains("仍需查看数量并确认"))
    }

    @Test
    fun `四种范围都可写入规则资料库 且指定日期格式无效不会确认`() {
        val state = CalendarControlState()
        AttendanceEditScope.entries.forEach { scope ->
            state.openCourse("course-a")
            state.openAttendance()
            state.pendingAttendance = if (state.selectedCourse.attendance == AttendanceState.MUST_ATTEND) AttendanceState.CHOOSE_TO_ATTEND else AttendanceState.MUST_ATTEND
            state.pendingAttendanceScope = scope
            state.pendingEffectiveDate = if (scope == AttendanceEditScope.SPECIFIED_AND_FUTURE) "2026-10-10" else ""
            state.confirmAttendance()
            assertTrue(state.ruleLedger.rules.any { it.sourceUid == "course-a" })
        }
        state.openAttendance()
        state.pendingAttendanceScope = AttendanceEditScope.SPECIFIED_AND_FUTURE
        state.pendingEffectiveDate = "无效日期"
        state.confirmAttendance()
        assertEquals(AppScreen.ATTENDANCE, state.screen)
        assertEquals("请输入 YYYY-MM-DD 格式的指定日期。", state.feedback)
    }

    @Test
    fun `来源课程默认蓝色并支持批量三态与范围规则`() {
        val state = CalendarControlState()
        state.updatePrivateSyncConfiguration(PrivateSyncConfiguration(tumSubscriptionUrl = "https://synthetic.example.test/calendar"))
        state.requestSourcePreview()
        state.resolveSourcePreview(state.sourceReadToken, IcalReadOnlyImportResult.Success(sourcePreview()))

        assertEquals(AppScreen.SOURCE_PREVIEW, state.screen)
        assertEquals(AttendanceState.CHOOSE_TO_ATTEND, state.sourceAttendanceFor("uid-a"))
        state.selectAllSourceOccurrences()
        state.openSourceAttendanceEditor()
        state.pendingSourceAttendance = AttendanceState.MUST_ATTEND
        state.pendingSourceAttendanceScope = AttendanceEditScope.ALL_OCCURRENCES
        state.confirmSourceAttendance()

        assertEquals(AppScreen.SOURCE_PREVIEW, state.screen)
        assertEquals(AttendanceState.MUST_ATTEND, state.sourceAttendanceFor("uid-a"))
        assertEquals(AttendanceState.MUST_ATTEND, state.sourceAttendanceFor("uid-b"))
        assertTrue(state.displayedCourses.all { it.attendance == AttendanceState.MUST_ATTEND })
    }

    @Test
    fun `批量设置可同时收藏且写入准备不会直接发出写入请求`() {
        val state = CalendarControlState()
        state.updatePrivateSyncConfiguration(PrivateSyncConfiguration(tumSubscriptionUrl = "https://synthetic.example.test/calendar"))
        state.requestSourcePreview()
        state.resolveSourcePreview(state.sourceReadToken, IcalReadOnlyImportResult.Success(sourcePreview()))
        state.selectAllSourceOccurrences()
        state.openSourceAttendanceEditor()
        state.pendingSourceFavoriteAction = SourceFavoriteAction.ADD
        state.confirmSourceAttendance()

        assertEquals(listOf("uid-a", "uid-b"), state.favoriteCourses.map { it.id })
        state.setSyncDestinationEnabled(SyncDestination.LOCAL_CALENDAR, true)
        state.prepareCalendarWrite()

        assertEquals(AppScreen.MANAGED_SYNC_PREVIEW, state.screen)
        assertTrue(state.managedSyncPreview != null)
        assertFalse(state.managedSyncRequested)
    }

    @Test
    fun `成功读取来源会一次回流首页 搜索和收藏数据源`() {
        val state = CalendarControlState()
        state.updatePrivateSyncConfiguration(PrivateSyncConfiguration(tumSubscriptionUrl = "https://synthetic.example.test/calendar"))
        state.requestSourcePreview()
        state.resolveSourcePreview(state.sourceReadToken, IcalReadOnlyImportResult.Success(sourcePreview()))

        assertTrue(state.sourceCatalogLoaded)
        assertEquals(listOf("uid-a", "uid-b"), state.displayedCourses.map { it.id })
        state.searchQuery = "合成 B"
        assertEquals(listOf("uid-b"), state.searchedCourses.map { it.id })
        state.toggleFavorite("uid-b")
        assertEquals(listOf("uid-b"), state.favoriteCourses.map { it.id })
    }

    @Test
    fun `重新读取来源会按 UID 保留本机收藏与覆盖`() {
        val state = CalendarControlState()
        state.updatePrivateSyncConfiguration(PrivateSyncConfiguration(tumSubscriptionUrl = "https://synthetic.example.test/calendar"))
        state.requestSourcePreview()
        state.resolveSourcePreview(state.sourceReadToken, IcalReadOnlyImportResult.Success(sourcePreview()))
        state.toggleFavorite("uid-a")
        state.openCourse("uid-a")
        state.updateSelectedCourseOverride(CourseLocalOverride(title = "本机名称"))
        state.requestSourcePreview()
        state.resolveSourcePreview(state.sourceReadToken, IcalReadOnlyImportResult.Success(sourcePreview()))

        val restored = state.displayedCourses.first { it.id == "uid-a" }
        assertTrue(restored.isFavorite)
        assertEquals("本机名称", restored.localOverride.title)
    }

    @Test
    fun `来源课程仅本次按 UID 保存各自实例键`() {
        val state = CalendarControlState()
        state.updatePrivateSyncConfiguration(PrivateSyncConfiguration(tumSubscriptionUrl = "https://synthetic.example.test/calendar"))
        state.requestSourcePreview()
        state.resolveSourcePreview(state.sourceReadToken, IcalReadOnlyImportResult.Success(sourcePreview()))
        state.selectAllSourceOccurrences()
        state.openSourceAttendanceEditor()
        state.pendingSourceAttendance = AttendanceState.STOP_ATTENDING
        state.pendingSourceAttendanceScope = AttendanceEditScope.ONLY_THIS_OCCURRENCE
        state.confirmSourceAttendance()

        val rules = state.ruleLedger.rules.associateBy { it.sourceUid }
        assertEquals(AttendanceState.STOP_ATTENDING, rules.getValue("uid-a").instanceOverrides["a-instance"])
        assertFalse(rules.getValue("uid-a").instanceOverrides.containsKey("b-instance"))
        assertEquals(AttendanceState.STOP_ATTENDING, rules.getValue("uid-b").instanceOverrides["b-instance"])
    }

    @Test
    fun `来源实例须明确选择后才生成受控双目标预览`() {
        val state = CalendarControlState()
        state.updatePrivateSyncConfiguration(PrivateSyncConfiguration(tumSubscriptionUrl = "https://synthetic.example.test/calendar"))
        state.requestSourcePreview()
        state.resolveSourcePreview(state.sourceReadToken, IcalReadOnlyImportResult.Success(sourcePreview()))
        state.selectAllSourceOccurrences()
        state.setSyncDestinationEnabled(SyncDestination.LOCAL_CALENDAR, true)
        state.setSyncDestinationEnabled(SyncDestination.GOOGLE_CALENDAR, true)

        state.requestManagedSyncPreview()

        assertEquals(AppScreen.MANAGED_SYNC_PREVIEW, state.screen)
        assertEquals(2, state.managedSyncPreview!!.desiredEventCount)
        assertEquals(2, state.managedSyncPreview!!.targetChanges.size)
    }

    @Test
    fun `手动事项不依赖来源读取也能独立生成预览`() {
        val state = CalendarControlState(
            initialRuleLedger = RuleLedger(
                manualEvents = listOf(
                    ManualScheduleItem(
                        id = "manual-1",
                        title = "合成手动事项",
                        startsAt = Instant.parse("2026-10-22T08:00:00Z"),
                        endsAt = Instant.parse("2026-10-22T09:00:00Z"),
                        attendanceState = AttendanceState.MUST_ATTEND,
                    ),
                ),
            ),
        )
        state.setSyncDestinationEnabled(SyncDestination.LOCAL_CALENDAR, true)

        state.requestManagedSyncPreview()

        assertEquals(AppScreen.MANAGED_SYNC_PREVIEW, state.screen)
        assertEquals(1, state.managedSyncPreview!!.desiredEventCount)
        assertEquals(0, state.managedSourceCandidateCount)
        assertEquals(1, state.managedManualCandidateCount)
    }

    @Test
    fun `保存范围不读取不清除选择 重新读取需要确认`() {
        val state = CalendarControlState()
        state.updatePrivateSyncConfiguration(PrivateSyncConfiguration(tumSubscriptionUrl = "https://synthetic.example.test/calendar"))
        state.requestSourcePreview()
        state.resolveSourcePreview(state.sourceReadToken, IcalReadOnlyImportResult.Success(sourcePreview()))
        val selected = SourceOccurrenceIdentity("uid-a", "a-instance")
        state.toggleSourceOccurrenceSelection(selected)

        assertTrue(state.saveSourceOutputRange("2026-09-14", "2026-09-30").isEmpty())
        assertEquals(setOf(selected), state.selectedSourceOccurrences)
        assertFalse(state.sourceReadRequested)
        state.requestSourceReadForCurrentRange()
        assertTrue(state.sourceReloadConfirmationPending)
        assertFalse(state.sourceReadRequested)

        state.resolveSourceReloadConfirmation(false)
        assertEquals(setOf(selected), state.selectedSourceOccurrences)
        state.requestSourceReadForCurrentRange()
        state.resolveSourceReloadConfirmation(true)
        assertTrue(state.sourceReadRequested)
    }

    @Test
    fun `未获本机权限时确认执行被拒绝且不发出后台请求`() {
        val state = CalendarControlState()
        state.updatePrivateSyncConfiguration(PrivateSyncConfiguration(tumSubscriptionUrl = "https://synthetic.example.test/calendar"))
        state.requestSourcePreview()
        state.resolveSourcePreview(state.sourceReadToken, IcalReadOnlyImportResult.Success(sourcePreview()))
        state.setSyncDestinationEnabled(SyncDestination.LOCAL_CALENDAR, true)
        state.requestManagedSyncPreview()

        state.requestManagedSyncExecution()

        assertFalse(state.managedSyncRequested)
        assertTrue(state.feedback!!.contains("没有获得系统权限"))
    }

    private fun sourcePreview(): IcalReadOnlyPreview = IcalReadOnlyPreview(
        sourceEventCount = 2,
        manageableOccurrenceCount = 2,
        skippedByReason = emptyMap(),
        firstManageableStart = Instant.parse("2026-09-14T08:00:00Z"),
        lastManageableEnd = Instant.parse("2026-09-15T10:00:00Z"),
        courses = listOf(
            SourceCourse("uid-a", "合成 A", "地点 A", Instant.parse("2026-09-14T08:00:00Z"), Instant.parse("2026-09-14T10:00:00Z"), "a-instance", 1, 0),
            SourceCourse("uid-b", "合成 B", "地点 B", Instant.parse("2026-09-15T08:00:00Z"), Instant.parse("2026-09-15T10:00:00Z"), "b-instance", 1, 0),
        ),
        occurrences = listOf(
            SourceCalendarOccurrence("uid-a", "a-instance", "合成 A", Instant.parse("2026-09-14T08:00:00Z"), Instant.parse("2026-09-14T10:00:00Z"), false, "地点 A"),
            SourceCalendarOccurrence("uid-b", "b-instance", "合成 B", Instant.parse("2026-09-15T08:00:00Z"), Instant.parse("2026-09-15T10:00:00Z"), false, "地点 B"),
        ),
    )
}
