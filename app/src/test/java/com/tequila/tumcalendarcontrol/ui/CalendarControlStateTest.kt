/*
 * 职责：验证离线 UI 状态机的课程选择、主题、层级返回、收藏及同步目标开关。
 * 输入／输出：输入合成课程与本地操作；输出内存状态变化断言。
 * 外部副作用：无。
 * 不负责：Compose 视觉渲染、持久化、网络或真实日历验证。
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
    fun `更多操作会返回进入前的主页面`() {
        val state = CalendarControlState()

        state.open(AppScreen.FAVORITES)
        state.openMore()

        assertEquals(AppScreen.MORE, state.screen)
        assertTrue(state.handleBack())
        assertEquals(AppScreen.FAVORITES, state.screen)
        assertEquals(NavigationDirection.BACKWARD, state.navigationDirection)
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
        assertTrue(state.feedback!!.contains("先保存私有 iCal 地址"))

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
    fun `重启后仅凭已持久化映射也可进入安全撤销页`() {
        val mapping = com.tequila.tumcalendarcontrol.sync.StoredEventMapping(
            com.tequila.tumcalendarcontrol.sync.OutputTarget.LOCAL_CALENDAR,
            com.tequila.tumcalendarcontrol.sync.ManagedEventIdentity("synthetic-uid", "synthetic-instance"),
            "event-id",
            "fingerprint",
        )
        val state = CalendarControlState(initialRuleLedger = RuleLedger(mappings = listOf(mapping)))

        state.open(AppScreen.SETTINGS)
        state.openMappedOutputRollback()

        assertEquals(AppScreen.MANAGED_SYNC_PREVIEW, state.screen)
        assertEquals(null, state.managedSyncPreview)
        assertFalse(state.managedSyncRequested)
        assertTrue(state.handleBack())
        assertEquals(AppScreen.SETTINGS, state.screen)
    }

    @Test
    fun `更多操作打开时保留原页面作为稳定背景`() {
        val state = CalendarControlState()

        state.open(AppScreen.SEARCH)
        state.openMore()

        assertTrue(state.isMoreOpen)
        assertEquals(AppScreen.SEARCH, state.contentScreen)
        assertTrue(state.handleBack())
        assertFalse(state.isMoreOpen)
        assertEquals(AppScreen.SEARCH, state.contentScreen)
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
        assertTrue(state.feedback!!.contains("未发起网络请求"))
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
        assertTrue(state.feedback!!.contains("不会创建或写入"))
    }

    @Test
    fun `四种范围都可写入规则资料库 且指定日期格式无效不会确认`() {
        val state = CalendarControlState()
        AttendanceEditScope.entries.forEach { scope ->
            state.openCourse("course-a")
            state.openAttendance()
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
        state.selectAllSourceCourses()
        state.openSourceAttendanceEditor()
        state.pendingSourceAttendance = AttendanceState.MUST_ATTEND
        state.pendingSourceAttendanceScope = AttendanceEditScope.ALL_OCCURRENCES
        state.confirmSourceAttendance()

        assertEquals(AppScreen.SOURCE_PREVIEW, state.screen)
        assertEquals(AttendanceState.MUST_ATTEND, state.sourceAttendanceFor("uid-a"))
        assertEquals(AttendanceState.MUST_ATTEND, state.sourceAttendanceFor("uid-b"))
    }

    @Test
    fun `来源课程仅本次按 UID 保存各自实例键`() {
        val state = CalendarControlState()
        state.updatePrivateSyncConfiguration(PrivateSyncConfiguration(tumSubscriptionUrl = "https://synthetic.example.test/calendar"))
        state.requestSourcePreview()
        state.resolveSourcePreview(state.sourceReadToken, IcalReadOnlyImportResult.Success(sourcePreview()))
        state.selectAllSourceCourses()
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
        state.selectAllSourceCourses()
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
        state.toggleSourceCourseSelection("uid-a")

        assertTrue(state.saveSourceOutputRange("2026-09-14", "2026-09-30").isEmpty())
        assertEquals(setOf("uid-a"), state.selectedSourceCourseUids)
        assertFalse(state.sourceReadRequested)
        state.requestSourceReadForCurrentRange()
        assertTrue(state.sourceReloadConfirmationPending)
        assertFalse(state.sourceReadRequested)

        state.resolveSourceReloadConfirmation(false)
        assertEquals(setOf("uid-a"), state.selectedSourceCourseUids)
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
        assertTrue(state.feedback!!.contains("尚未获得系统权限"))
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
