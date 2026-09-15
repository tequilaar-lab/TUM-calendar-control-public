/*
 * 职责：装配 Android 本机依赖，并把用户已确认的 UI 意图交给权限、文件、读取、授权和同步协调器。
 * 输入／输出：输入 Android Context 与内存中的 CalendarControlState；输出纯 Compose 外壳。
 * 外部副作用：保存私有偏好；仅在用户点按后读取 iCal、申请授权或执行已预览的受控输出。
 * 不负责：页面布局、导航栏、页面元数据、自动同步、访问用户主日历、保存 Google 令牌或替代真机验证。
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
            initialCoursePresentations = courseOverridePreferences.loadPresentations(),
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
        googleAuthorizationCoordinator.resolveResult(result.resultCode, result.data, state::resolveGoogleAuthorization)
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
    LaunchedEffect(state.googleConnectionCheckRequested) {
        if (!state.googleConnectionCheckRequested) return@LaunchedEffect
        val configuration = state.privateSyncConfiguration
        val googleToken = state.currentGoogleAccessToken()
        val connectionCheckToken = state.googleConnectionCheckToken
        Thread {
            val result = platformManagedSyncRunner.checkGoogleConnection(configuration, googleToken)
            Handler(Looper.getMainLooper()).post { state.resolveGoogleConnectionCheck(connectionCheckToken, result.userSummary) }
        }.start()
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
                outcome.onSuccess {
                    state.resolveManagedOperation(token, it.result, it.configuration)
                    if (it.requiresGoogleReauthorization && !googleToken.isNullOrBlank()) {
                        state.invalidateGoogleAuthorizationAfterUnauthorized()
                        googleAuthorizationCoordinator.clearRejectedAccessToken(googleToken) { cleared ->
                            Handler(Looper.getMainLooper()).post {
                                state.resolveRejectedGoogleTokenCacheClear(cleared)
                            }
                        }
                    }
                }
                    .onFailure { state.failManagedOperation(token, "写入日历未完成；请检查权限或网络后重试") }
            }
        }.start()
    }
    BackHandler(enabled = state.canHandleBack()) { state.handleBack() }
    CalendarControlSurface(state)
}

/** 组合纯 Compose 外壳，供应用入口与合成截图测试共享；不初始化偏好或触发外部操作。 */
@Composable
internal fun CalendarControlSurface(state: CalendarControlState) {
    TumCalendarTheme(mode = state.themeMode) {
        CalendarControlChrome(state)
    }
}
