/*
 * Aufgabe: Android-Abhängigkeiten und bestätigte Berechtigungs-, Datei-, Lese-, Autorisierungs- und Ausgabeabsichten koordinieren.
 * Ein-/Ausgabe: Android Context und CalendarControlState -> Oberfläche und bestätigte Koordinationsaufträge.
 * Nebenwirkungen: Private Präferenzen; Quelle, Autorisierung und Ausgabe nur nach bestätigter Absicht.
 * Nicht zuständig: Seitenlayout, Navigationsleiste, Seitenmetadaten, automatische Synchronisierung, Hauptkalender, Google-Tokenspeicherung oder Ersatz der Geräteprüfung.
 */
package com.tequila.tumcalendarcontrol.ui

import com.tequila.tumcalendarcontrol.i18n.message
import com.tequila.tumcalendarcontrol.i18n.AppLanguagePreferences

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
    val session = androidx.lifecycle.ViewModelProvider(context as androidx.activity.ComponentActivity)[CalendarSessionModel::class.java]
    val languagePreferences = remember(context) { AppLanguagePreferences(context) }
    var language by remember { mutableStateOf(languagePreferences.load()) }
    val profilePreferences = remember(context) { ProfilePreferences(context) }
    val pageMemoryPreferences = remember(context) { PageMemoryPreferences(context) }
    val appearancePreferences = remember(context) { AppearancePreferences(context) }
    val courseTypeNamesPreferences = remember(context) { CourseTypeNamesPreferences(context) }
    val syncDestinationPreferences = remember(context) { SyncDestinationPreferences(context) }
    val privateSyncConfigurationStore = remember(context) { PrivateSyncConfigurationStore(context) }
    val ruleLedgerPreferences = remember(context) { RuleLedgerPreferences(context) }
    val courseOverridePreferences = remember(context) { CourseOverridePreferences(context) }
    val userDataPersistence = remember(ruleLedgerPreferences, courseOverridePreferences) {
        UserDataPersistence { ledger, courses ->
            ruleLedgerPreferences.saveBusinessState(ledger) { courseOverridePreferences.save(courses) }
        }
    }
    val sourceCheckSettingsStore = remember(context) { SourceCheckSettingsStore(context) }
    val periodicSourceCheckScheduler = remember(context) { PeriodicSourceCheckScheduler(context) }
    val sourceSnapshotPreferences = remember(context) { SourceSnapshotPreferences(context) }
    val initialSourceSnapshots = remember(context) { sourceSnapshotPreferences.load() }
    val readOnlyIcalImportService = remember { ReadOnlyIcalImportService() }
    val googleAuthorizationCoordinator = remember(context) { GoogleAuthorizationCoordinator(context) }
    val platformManagedSyncRunner = remember(context) { PlatformManagedSyncRunner(context.applicationContext) }
    val state = remember {
        session.state ?: CalendarControlState(
            initialCourses = if (sourceSnapshotPreferences.hasStoredData) emptyList() else courseOverridePreferences.applyTo(SampleCourses.all()),
            initialProfile = profilePreferences.load(),
            initialAppearance = appearancePreferences.load(),
            initialSyncDestinations = syncDestinationPreferences.load(),
            initialPrivateSyncConfiguration = privateSyncConfigurationStore.load(),
            initialRuleLedger = ruleLedgerPreferences.load(),
            initialSourceCheckSettings = sourceCheckSettingsStore.loadSettings(),
            initialSourceCheckStatus = sourceCheckSettingsStore.loadStatus(),
            initialCoursePresentations = courseOverridePreferences.loadPresentations(),
            initialSourceSnapshots = initialSourceSnapshots,
            userDataPersistence = userDataPersistence,
        ).also { it.courseTypeNames = courseTypeNamesPreferences.load(); it.restorePageMemory(pageMemoryPreferences.load()); session.state = it }
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
                } ?: error(message(215))
            }.isSuccess
        } == true
        state.resolveRuleBackupExport(saved)
    }
    val ruleBackupImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val backupText = uri?.let {
            runCatching {
                context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                    reader.readText().also { text -> require(text.length <= MAX_RULE_BACKUP_CHARS) }
                } ?: error(message(215))
            }.getOrNull()
        }
        state.resolveRuleBackupImport(backupText)
    }
    val googleAuthorizationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        googleAuthorizationCoordinator.resolveResult(result.resultCode, result.data, state::resolveGoogleAuthorization)
    }
    LaunchedEffect(state.profile) { profilePreferences.save(state.profile) }
    LaunchedEffect(state.persistedPageMemory) {
        runCatching { pageMemoryPreferences.save(state.persistedPageMemory) }
            .onFailure { state.feedback = message(216) }
    }
    LaunchedEffect(state.appearanceSettings) { appearancePreferences.save(state.appearanceSettings) }
    LaunchedEffect(state.courseTypeNames) { courseTypeNamesPreferences.save(state.courseTypeNames) }
    LaunchedEffect(state.syncDestinations) { syncDestinationPreferences.save(state.syncDestinations) }
    LaunchedEffect(state.privateSyncConfiguration) {
        if (privateConfigurationInitialized) runCatching { privateSyncConfigurationStore.save(state.privateSyncConfiguration) }
            .onFailure { state.feedback = message(217) }
        else privateConfigurationInitialized = true
    }
    LaunchedEffect(state.sourceCheckSettings) { sourceCheckSettingsStore.saveSettings(state.sourceCheckSettings) }
    LaunchedEffect(state.savedSourceSnapshots) { sourceSnapshotPreferences.save(state.savedSourceSnapshots) }
    LaunchedEffect(Unit) {
        sourceSnapshotPreferences.recoveryNotice?.let { state.feedback = it }
        state.refreshLocalCalendarPermission(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED,
        )
    }
    LaunchedEffect(state.screen) {
        if (state.screen == AppScreen.BACKGROUND_CHECK) state.refreshSourceCheckStatus(sourceCheckSettingsStore.loadStatus())
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
        if (!session.claim("source", requestToken)) return@LaunchedEffect
        val window = state.privateSyncConfiguration.sourceOutputWindow ?: return@LaunchedEffect
        val sourceReadCoordinator = SourceReadCoordinator({ url -> readOnlyIcalImportService.preview(url, window) }, SourceReadDeadlineMillis)
        val mainHandler = Handler(Looper.getMainLooper())
        Log.i(SourceReadLogTag, "stage=started token=$requestToken")
        sourceReadCoordinator.start(privateUrl) { completion ->
            Log.i(SourceReadLogTag, "stage=${completion.stage} token=$requestToken elapsedMs=${completion.elapsedMillis}")
            mainHandler.post {
                state.resolveSourcePreview(requestToken, completion.result)
                sourceSnapshotPreferences.save(state.savedSourceSnapshots)
            }
            sourceReadCoordinator.close()
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
        if (!session.claim("connection", connectionCheckToken)) return@LaunchedEffect
        Thread {
            val result = platformManagedSyncRunner.checkGoogleConnection(configuration, googleToken)
            Handler(Looper.getMainLooper()).post { state.resolveGoogleConnectionCheck(connectionCheckToken, result) }
        }.start()
    }
    LaunchedEffect(state.managedSyncRequested, state.managedRollbackRequested) {
        if (!state.managedSyncRequested && !state.managedRollbackRequested) return@LaunchedEffect
        val token = state.currentManagedOperationToken()
        if (!session.claim("write", token)) return@LaunchedEffect
        val rollback = state.managedRollbackRequested
        val configuration = state.privateSyncConfiguration
        val targets = state.currentManagedTargets()
        val desired = state.currentDesiredEvents()
        val mappings = state.ruleLedger.mappings
        val googleToken = state.currentGoogleAccessToken()
        Thread {
            val outcome = runCatching {
                if (rollback) platformManagedSyncRunner.rollback(mappings, targets, configuration, googleToken, state.currentManagedCleanupScope())
                else platformManagedSyncRunner.execute(desired, mappings, targets, configuration, googleToken)
            }
            Handler(Looper.getMainLooper()).post {
                outcome.onSuccess {
                    state.resolveManagedOperation(token, it.result, it.configuration)
                    if (!ruleLedgerPreferences.save(state.ruleLedger)) {
                        state.feedback = message(218)
                    }
                    runCatching { privateSyncConfigurationStore.save(state.privateSyncConfiguration) }
                        .onFailure { state.feedback = message(219) }
                    if (it.requiresGoogleReauthorization && !googleToken.isNullOrBlank()) {
                        state.invalidateGoogleAuthorizationAfterUnauthorized()
                        googleAuthorizationCoordinator.clearRejectedAccessToken(googleToken) { cleared ->
                            Handler(Looper.getMainLooper()).post {
                                state.resolveRejectedGoogleTokenCacheClear(cleared)
                            }
                        }
                    }
                }
                    .onFailure { state.failManagedOperation(token, message(220)) }
            }
        }.start()
    }
    LaunchedEffect(state.cleanupCatalogReadToken) {
        if (state.cleanupCatalogReadTargets.isEmpty()) return@LaunchedEffect
        val token = state.cleanupCatalogReadToken
        if (!session.claim("cleanup-read", token)) return@LaunchedEffect
        val targets = state.cleanupCatalogReadTargets
        val mappings = state.ruleLedger.mappings
        val configuration = state.privateSyncConfiguration
        val accessToken = state.currentGoogleAccessToken()
        Thread {
            val result = runCatching { platformManagedSyncRunner.readCleanupCatalog(mappings, targets, configuration, accessToken) }.getOrNull()
            Handler(Looper.getMainLooper()).post {
                state.resolveCleanupCatalogRead(token, result)
                if (result != null && !ruleLedgerPreferences.save(state.ruleLedger)) {
                    state.feedback = message(221)
                }
            }
        }.start()
    }
    BackHandler(enabled = state.canHandleBack()) { state.handleBack() }
    androidx.compose.runtime.CompositionLocalProvider(LocalAppLanguage provides LanguageSelection(language) { chosen ->
        languagePreferences.save(chosen)
        language = chosen
    }) { CalendarControlSurface(state) }
}

/** Reinen Compose-Rahmen für App und synthetische Bildtests kombinieren; keine Präferenzen oder externen Aktionen initialisieren. */
@Composable
internal fun CalendarControlSurface(state: CalendarControlState) {
    TumCalendarTheme(mode = state.themeMode, lightStyle = state.lightPaletteStyle, darkStyle = state.darkPaletteStyle) {
        androidx.compose.runtime.CompositionLocalProvider(LocalCourseTypeNames provides CourseTypeNames(state.courseTypeNames) { key, value ->
            if (value == null) state.courseTypeNames = state.courseTypeNames - key
            else if (validCourseTypeName(value)) state.courseTypeNames = state.courseTypeNames + (key to value.trim())
        }) { CalendarControlChrome(state) }
    }
}
