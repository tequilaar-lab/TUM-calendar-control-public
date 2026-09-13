/*
 * 职责：组合主题、固定顶栏、浮动主导航、全屏更多覆盖层及本机偏好装载。
 * 输入／输出：输入内存中的 CalendarControlState；输出 Compose-Miuix 应用及用户确认的同步流程。
 * 外部副作用：保存私有偏好；仅在用户点按后读取 iCal、申请授权或执行已预览的受控输出。
 * 不负责：自动同步、访问用户主日历、保存 Google 令牌或替代真机验证。
 */
package com.tequila.tumcalendarcontrol.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tequila.tumcalendarcontrol.sync.PrivateSyncConfigurationStore
import com.tequila.tumcalendarcontrol.sync.RuleLedgerPreferences
import com.tequila.tumcalendarcontrol.sync.ReadOnlyIcalImportService
import com.tequila.tumcalendarcontrol.sync.SourceReadCoordinator
import com.tequila.tumcalendarcontrol.sync.PlatformManagedSyncRunner
import com.tequila.tumcalendarcontrol.sync.PeriodicSourceCheckScheduler
import com.tequila.tumcalendarcontrol.sync.RuleLedgerBackupCodec
import com.tequila.tumcalendarcontrol.sync.SourceCheckSettingsStore
import com.tequila.tumcalendarcontrol.domain.SampleCourses
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar

private val FixedTopBarHeight = 136.dp
private val FloatingNavigationShape = RoundedCornerShape(30.dp)
private const val SourceReadDeadlineMillis = 35_000L
private const val MAX_RULE_BACKUP_CHARS = 1_000_000
private const val SourceReadLogTag = "TumSourceRead"

@Composable
fun CalendarControlApp() {
    val context = LocalContext.current
    val profilePreferences = remember(context) { ProfilePreferences(context) }
    val syncDestinationPreferences = remember(context) { SyncDestinationPreferences(context) }
    val privateSyncConfigurationStore = remember(context) { PrivateSyncConfigurationStore(context) }
    val ruleLedgerPreferences = remember(context) { RuleLedgerPreferences(context) }
    val sourceCheckSettingsStore = remember(context) { SourceCheckSettingsStore(context) }
    val periodicSourceCheckScheduler = remember(context) { PeriodicSourceCheckScheduler(context) }
    val courseOverridePreferences = remember(context) { CourseOverridePreferences(context) }
    val readOnlyIcalImportService = remember { ReadOnlyIcalImportService() }
    val sourceReadCoordinator = remember { SourceReadCoordinator(readOnlyIcalImportService::preview, SourceReadDeadlineMillis) }
    val googleAuthorizationCoordinator = remember(context) { GoogleAuthorizationCoordinator(context) }
    val platformManagedSyncRunner = remember(context) { PlatformManagedSyncRunner(context) }
    val state = remember {
        CalendarControlState(
            initialCourses = courseOverridePreferences.applyTo(SampleCourses.all()),
            initialProfile = profilePreferences.load(),
            initialSyncDestinations = syncDestinationPreferences.load(),
            initialPrivateSyncConfiguration = privateSyncConfigurationStore.load(),
            initialRuleLedger = ruleLedgerPreferences.load(),
            initialSourceCheckSettings = sourceCheckSettingsStore.loadSettings(),
            initialSourceCheckStatus = sourceCheckSettingsStore.loadStatus(),
        )
    }
    var privateConfigurationInitialized by remember { mutableStateOf(false) }
    val localCalendarPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        state.resolveLocalCalendarPermission(
            grants[Manifest.permission.READ_CALENDAR] == true && grants[Manifest.permission.WRITE_CALENDAR] == true,
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        state.resolveNotificationPermission(granted)
    }
    val ruleBackupExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val saved = uri?.let {
            runCatching {
                context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer ->
                    writer.write(RuleLedgerBackupCodec.encode(state.ruleLedger))
                } ?: error("无法打开备份文件")
            }.isSuccess
        } == true
        state.resolveRuleBackupExport(saved)
    }
    val ruleBackupImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val backupText = uri?.let {
            runCatching {
                context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                    reader.readText().also { text -> require(text.length <= MAX_RULE_BACKUP_CHARS) }
                } ?: error("无法打开备份文件")
            }.getOrNull()
        }
        state.resolveRuleBackupImport(backupText)
    }
    val googleAuthorizationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        googleAuthorizationCoordinator.resolveResult(result.data, state::resolveGoogleAuthorization)
    }
    LaunchedEffect(state.profile) { profilePreferences.save(state.profile) }
    LaunchedEffect(state.syncDestinations) { syncDestinationPreferences.save(state.syncDestinations) }
    LaunchedEffect(state.privateSyncConfiguration) {
        if (privateConfigurationInitialized) privateSyncConfigurationStore.save(state.privateSyncConfiguration)
        else privateConfigurationInitialized = true
    }
    LaunchedEffect(state.ruleLedger) { ruleLedgerPreferences.save(state.ruleLedger) }
    LaunchedEffect(state.sourceCheckSettings) { sourceCheckSettingsStore.saveSettings(state.sourceCheckSettings) }
    LaunchedEffect(state.courses) { courseOverridePreferences.save(state.courses) }
    LaunchedEffect(Unit) {
        state.refreshLocalCalendarPermission(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED,
        )
    }
    LaunchedEffect(state.localCalendarPermissionRequestPending) {
        if (state.localCalendarPermissionRequestPending) localCalendarPermissionLauncher.launch(
            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
        )
    }
    LaunchedEffect(state.notificationPermissionRequestPending) {
        if (!state.notificationPermissionRequestPending) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= 33) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else state.resolveNotificationPermission(true)
    }
    LaunchedEffect(state.sourceCheckSchedulingRequested) {
        if (!state.sourceCheckSchedulingRequested) return@LaunchedEffect
        periodicSourceCheckScheduler.apply(state.sourceCheckSettings)
        state.consumeSourceCheckSchedulingRequest()
    }
    LaunchedEffect(state.ruleBackupExportRequested) {
        if (!state.ruleBackupExportRequested) return@LaunchedEffect
        state.consumeRuleBackupExportRequest()
        ruleBackupExportLauncher.launch("tum-calendar-rules-backup.tumrules")
    }
    LaunchedEffect(state.ruleBackupImportRequested) {
        if (!state.ruleBackupImportRequested) return@LaunchedEffect
        state.consumeRuleBackupImportRequest()
        ruleBackupImportLauncher.launch(arrayOf("text/plain", "application/octet-stream"))
    }
    LaunchedEffect(state.sourceReadToken) {
        if (!state.sourceReadInProgress || !state.sourceReadRequested) return@LaunchedEffect
        val privateUrl = state.privateSyncConfiguration.tumSubscriptionUrl
        val requestToken = state.sourceReadToken
        val mainHandler = Handler(Looper.getMainLooper())
        Log.i(SourceReadLogTag, "stage=started token=$requestToken")
        sourceReadCoordinator.start(privateUrl) { completion ->
            Log.i(SourceReadLogTag, "stage=${completion.stage} token=$requestToken elapsedMs=${completion.elapsedMillis}")
            mainHandler.post { state.resolveSourcePreview(requestToken, completion.result) }
        }
    }
    LaunchedEffect(state.googleAuthorizationRequested) {
        if (!state.googleAuthorizationRequested) return@LaunchedEffect
        googleAuthorizationCoordinator.authorize { outcome ->
            state.resolveGoogleAuthorization(outcome)
            if (outcome is GoogleAuthorizationOutcome.LaunchConsent) {
                googleAuthorizationLauncher.launch(IntentSenderRequest.Builder(outcome.pendingIntent.intentSender).build())
            }
        }
    }
    LaunchedEffect(state.managedSyncRequested, state.managedRollbackRequested) {
        if (!state.managedSyncRequested && !state.managedRollbackRequested) return@LaunchedEffect
        val token = state.currentManagedOperationToken()
        val rollback = state.managedRollbackRequested
        val configuration = state.privateSyncConfiguration
        val targets = state.currentManagedTargets()
        val desired = state.currentDesiredEvents()
        val mappings = state.ruleLedger.mappings
        val googleToken = state.currentGoogleAccessToken()
        Thread {
            val outcome = runCatching {
                if (rollback) platformManagedSyncRunner.rollback(mappings, targets, configuration, googleToken)
                else platformManagedSyncRunner.execute(desired, mappings, targets, configuration, googleToken)
            }
            Handler(Looper.getMainLooper()).post {
                outcome.onSuccess { state.resolveManagedOperation(token, it.result, it.configuration) }
                    .onFailure { state.failManagedOperation(token, "受控同步未完成；请检查权限或网络后重试") }
            }
        }.start()
    }
    BackHandler(enabled = state.canHandleBack()) { state.handleBack() }
    TumCalendarTheme(mode = state.themeMode) {
        Box(Modifier.fillMaxSize()) {
            CalendarControlScaffold(state)
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
                        TactileProfileButton(
                            profile = state.profile,
                            onClick = state::openProfile,
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
                        top = innerPadding.calculateTopPadding() + 16.dp,
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
private fun AppBottomNavigation(state: CalendarControlState, selectedScreen: AppScreen) {
    val palette = LocalTumPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(12.dp, FloatingNavigationShape)
            .clip(FloatingNavigationShape)
            .background(palette.surfaceContainer.copy(alpha = 0.88f))
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
                    Icons.Rounded.Home, "首页", selectedScreen == AppScreen.OVERVIEW,
                    onClick = { state.open(AppScreen.OVERVIEW) },
                    modifier = Modifier.width(itemWidth).offset(x = itemWidth * 0),
                )
                TactileNavigationItem(
                    Icons.Rounded.Search, "搜索", selectedScreen == AppScreen.SEARCH,
                    onClick = { state.open(AppScreen.SEARCH) },
                    modifier = Modifier.width(itemWidth).offset(x = itemWidth * 1),
                )
                TactileNavigationItem(
                    Icons.Rounded.Favorite, "收藏", selectedScreen == AppScreen.FAVORITES,
                    onClick = { state.open(AppScreen.FAVORITES) },
                    modifier = Modifier.width(itemWidth).offset(x = itemWidth * 2),
                )
                TactileNavigationItem(
                    Icons.Rounded.Settings, "设置", selectedScreen == AppScreen.SETTINGS,
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

private fun AppScreen.usesBottomNavigation(): Boolean = this in setOf(
    AppScreen.OVERVIEW,
    AppScreen.SEARCH,
    AppScreen.FAVORITES,
    AppScreen.SETTINGS,
)

private fun AppScreen.usesBackArrow(): Boolean = this in setOf(
    AppScreen.DETAIL,
    AppScreen.ATTENDANCE,
    AppScreen.COURSE_EDIT,
    AppScreen.PROFILE,
    AppScreen.SYNC_CONFIGURATION,
    AppScreen.SYNC_PREVIEW,
    AppScreen.SOURCE_RANGE,
    AppScreen.SOURCE_PREVIEW,
    AppScreen.SOURCE_ATTENDANCE,
    AppScreen.MANUAL_EVENTS,
    AppScreen.MANAGED_SYNC_PREVIEW,
    AppScreen.BACKGROUND_CHECK,
    AppScreen.RULE_BACKUP,
)

private fun AppScreen.bottomNavigationIndex(): Int = when (this) {
    AppScreen.OVERVIEW -> 0
    AppScreen.SEARCH -> 1
    AppScreen.FAVORITES -> 2
    AppScreen.SETTINGS -> 3
    else -> 0
}

private fun AppScreen.title(): String = when (this) {
    AppScreen.OVERVIEW -> "TUM 日历控制"
    AppScreen.DETAIL -> "课程详情"
    AppScreen.ATTENDANCE -> "状态选择"
    AppScreen.COURSE_EDIT -> "编辑课程"
    AppScreen.SEARCH -> "搜索课程"
    AppScreen.FAVORITES -> "收藏课程"
    AppScreen.MORE -> "更多操作"
    AppScreen.SETTINGS -> "设置与同步"
    AppScreen.PROFILE -> "个人资料"
    AppScreen.SYNC_CONFIGURATION -> "来源与 Google 连接"
    AppScreen.SYNC_PREVIEW -> "同步预览"
    AppScreen.SOURCE_RANGE -> "选择同步时间范围"
    AppScreen.SOURCE_PREVIEW -> "选择来源课程"
    AppScreen.SOURCE_ATTENDANCE -> "设置来源课程状态"
    AppScreen.MANUAL_EVENTS -> "手动校园事项"
    AppScreen.MANAGED_SYNC_PREVIEW -> "受控同步预览"
    AppScreen.BACKGROUND_CHECK -> "后台 iCal 检查"
    AppScreen.RULE_BACKUP -> "规则备份与恢复"
}

private fun AppScreen.subtitle(): String? = when (this) {
    AppScreen.OVERVIEW -> "离线原型 · 仅合成数据"
    AppScreen.SETTINGS -> "可手动只读 iCal；不写入日历"
    AppScreen.SYNC_CONFIGURATION -> "仅保存本机配置，不会联网"
    AppScreen.SYNC_PREVIEW -> "离线合成预览 · 不写入日历"
    AppScreen.SOURCE_RANGE -> "1/4 范围可选 · 保存不会重新读取"
    AppScreen.SOURCE_PREVIEW -> "2/4 只读目录 · 不写入日历"
    AppScreen.SOURCE_ATTENDANCE -> "只保存本机规则 · 不写入日历"
    AppScreen.MANUAL_EVENTS -> "先保存本机 · 下次同步预览可见"
    AppScreen.MANAGED_SYNC_PREVIEW -> "先预览，后确认执行"
    AppScreen.BACKGROUND_CHECK -> "只读检查 · 不自动写入日历"
    AppScreen.RULE_BACKUP -> "仅规则与手动事项 · 导入需确认"
    else -> null
}
