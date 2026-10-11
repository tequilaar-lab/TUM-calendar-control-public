# Inventar chinesischer Texte

Vor der Lokalisierung am 11.10.2026 enthielten **145 versionierte Textdateien** insgesamt **1.804 Zeilen mit chinesischen Zeichen**. Ignorierte lokale Audits, Caches und Buildartefakte sind ausgeschlossen. Binäre Bilder werden separat behandelt; die vorhandenen Testbilder enthalten teilweise chinesische Oberfläche.

Die Liste dokumentiert den Ausgangszustand, keinen offenen Übersetzungsbedarf. Die bewusst erhaltenen Fälle erklärt [Lokalisierung](LOKALISIERUNG.md).

| Datei | Betroffene Zeilen vorher |
| --- | ---: |
| `.gitignore` | 4 |
| `AGENTS.md` | 26 |
| `README.md` | 17 |
| `app/build.gradle.kts` | 19 |
| `app/proguard-rules.pro` | 2 |
| `app/src/main/AndroidManifest.xml` | 4 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/MainActivity.kt` | 4 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/domain/AttendanceResolver.kt` | 8 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/domain/AttendanceState.kt` | 7 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/domain/Course.kt` | 13 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/domain/CourseOccurrence.kt` | 4 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/domain/CourseRule.kt` | 4 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/domain/SampleCourses.kt` | 9 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/domain/SampleSchedule.kt` | 6 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/AndroidLocalCalendarGateway.kt` | 25 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/GoogleCalendarGateway.kt` | 47 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/GoogleConnectionChecker.kt` | 9 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/GroupAttendanceRule.kt` | 6 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/HttpsIcalSourceFetcher.kt` | 11 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/Ical4jRuntimeConfiguration.kt` | 5 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/IcalSourceParser.kt` | 8 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/KeystoreTextCipher.kt` | 6 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/ManagedCleanupCatalog.kt` | 5 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/ManagedSyncEngine.kt` | 19 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/ManualScheduleEditor.kt` | 4 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/OfflinePreviewSamples.kt` | 4 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/OwnedCalendarSyncExecutor.kt` | 30 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/PeriodicSourceCheckScheduler.kt` | 4 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/PeriodicSourceCheckWorker.kt` | 7 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/PlatformManagedSyncRunner.kt` | 10 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/PrivateSyncConfiguration.kt` | 12 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/PrivateSyncConfigurationStore.kt` | 6 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/ReadOnlyIcalImportService.kt` | 10 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/RuleLedger.kt` | 10 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/RuleLedgerBackupCodec.kt` | 4 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/RuleLedgerCodec.kt` | 6 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/RuleLedgerEditor.kt` | 7 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/RuleLedgerPreferences.kt` | 9 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/SourceCalendarOccurrence.kt` | 12 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/SourceCheckFingerprint.kt` | 5 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/SourceCheckSettings.kt` | 7 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/SourceCourseCatalog.kt` | 5 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/SourceOutputDateWindow.kt` | 6 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/SourceReadCoordinator.kt` | 9 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/SyncCandidateFactory.kt` | 8 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/SyncDiffPlanner.kt` | 12 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/SyncPreview.kt` | 4 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/SyncRuleResolver.kt` | 8 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/TargetStateReconciliation.kt` | 8 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/AppChrome.kt` | 21 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/AppComponents.kt` | 7 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/AppScreenMetadata.kt` | 35 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/AppScreens.kt` | 9 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/AppearancePaletteSelector.kt` | 8 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/AppearancePreferences.kt` | 6 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/AttendanceEffectiveDateParser.kt` | 5 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/AttendanceScreen.kt` | 11 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/BackgroundCheckScreen.kt` | 16 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/CalendarControlApp.kt` | 13 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/CalendarControlState.kt` | 148 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/CalendarSessionModel.kt` | 5 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/CourseDisplayGroupModel.kt` | 15 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/CourseOverridePreferences.kt` | 6 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/CourseScreens.kt` | 49 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/CourseTypeNames.kt` | 11 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/GoogleAuthorizationCoordinator.kt` | 14 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/IndividualSourceAttendance.kt` | 9 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/ManagedOutputWorkflowState.kt` | 19 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/ManagedSyncPreviewScreen.kt` | 20 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/ManualEventsScreen.kt` | 15 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/NavigationState.kt` | 6 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/OutputCleanupScreen.kt` | 39 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/PageMemory.kt` | 5 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/PrivateAddressInput.kt` | 6 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/ProfilePreferences.kt` | 4 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/RuleBackupScreen.kt` | 11 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/SettingsScreens.kt` | 86 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/SourceAttendanceScope.kt` | 4 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/SourceAttendanceScreen.kt` | 19 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/SourceCourseBrowserModel.kt` | 11 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/SourceCourseDateBrowser.kt` | 12 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/SourceCourseProjection.kt` | 6 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/SourceCourseSelectorScreen.kt` | 20 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/SourceOccurrenceChoiceRow.kt` | 6 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/SourceRangeScreen.kt` | 27 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/SourceReadWorkflowState.kt` | 22 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/SourceSnapshotStore.kt` | 16 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/SyncDestinationPreferences.kt` | 4 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/SyncDestinations.kt` | 21 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/TactileControls.kt` | 11 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/TumTheme.kt` | 7 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/UserDataPersistence.kt` | 5 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/UserProfile.kt` | 9 |
| `app/src/main/java/com/tequila/tumcalendarcontrol/ui/WriteTargetSelector.kt` | 6 |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | 1 |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | 1 |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | 1 |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | 1 |
| `app/src/main/res/values/colors.xml` | 1 |
| `app/src/main/res/values/strings.xml` | 2 |
| `app/src/main/res/values/themes.xml` | 3 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/domain/AttendanceResolverTest.kt` | 8 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/domain/CourseMeetingTimeTest.kt` | 12 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/AppOwnedCalendarContractTest.kt` | 6 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/GoogleCalendarGatewayTest.kt` | 13 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/GoogleConnectionCheckerTest.kt` | 10 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/IcalSourceParserTest.kt` | 11 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/ManagedSyncEngineTest.kt` | 11 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/ManualScheduleEditorTest.kt` | 7 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/OwnedCalendarSyncExecutorTest.kt` | 25 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/PrivateSyncConfigurationTest.kt` | 11 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/ReadOnlyIcalImportServiceTest.kt` | 15 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/RuleLedgerBackupCodecTest.kt` | 8 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/RuleLedgerEditorTest.kt` | 13 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/RuleLedgerPreferencesTest.kt` | 6 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/SourceCheckFingerprintTest.kt` | 9 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/SourceCourseCatalogTest.kt` | 9 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/SourceOutputDateWindowTest.kt` | 6 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/SourceReadCoordinatorTest.kt` | 10 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/SyncCandidateFactoryTest.kt` | 28 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/SyncDiffPlannerTest.kt` | 14 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/SyncPreviewTest.kt` | 6 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/SyncRuleResolverTest.kt` | 7 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/TargetReadbackRegressionTest.kt` | 4 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/TargetStateReconcilerTest.kt` | 10 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/AppScreenRouterTest.kt` | 4 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/AppearancePreferencesTest.kt` | 4 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/AttendanceEffectiveDateParserTest.kt` | 6 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/AttendanceScopeRegressionTest.kt` | 4 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/BusinessSemanticsRegressionTest.kt` | 12 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/CalendarControlScreenshotTest.kt` | 46 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/CalendarControlStateTest.kt` | 65 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/CourseDisplayGroupModelTest.kt` | 14 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/CourseEditingRegressionTest.kt` | 7 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/PageMemoryRegressionTest.kt` | 4 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/PreinstallBoundaryRegressionTest.kt` | 4 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/PreinstallFixRegressionTest.kt` | 5 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/SourceCourseBrowserModelTest.kt` | 12 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/SourceCourseProjectionTest.kt` | 21 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/SourceSnapshotStoreTest.kt` | 19 |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/WorkflowStateTest.kt` | 16 |
| `build.gradle.kts` | 5 |
| `gradle.properties` | 2 |
| `gradle/wrapper/gradle-wrapper.properties` | 1 |
| `settings.gradle.kts` | 4 |

## Erhaltener Bestand nach der Umstellung

Nach der Umstellung enthalten **44 Textdateien** noch chinesische Zeichen. Es gibt keine chinesischen Kotlin-, Gradle- oder XML-Kommentare mehr. Sprachressourcen, verbindliche Regeln, Kompatibilitätskonstanten und synthetische Tests bleiben bewusst erhalten.

| Datei | Grund |
| --- | --- |
| `AGENTS.md` | Verbindliches Original |
| `app/src/main/java/com/tequila/tumcalendarcontrol/i18n/LanguageSettings.kt` | Sprachname in seiner eigenen Schreibweise |
| `app/src/main/java/com/tequila/tumcalendarcontrol/i18n/MessageSources.kt` | Sprachressource / kanonischer Nachrichtenkatalog |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/AndroidLocalCalendarGateway.kt` | Kompatibilität bestehender Kalender |
| `app/src/main/java/com/tequila/tumcalendarcontrol/sync/PrivateSyncConfiguration.kt` | Kompatibilität bestehender Kalender |
| `app/src/main/res/values-zh/strings.xml` | Sprachressource / kanonischer Nachrichtenkatalog |
| `app/src/test/java/com/tequila/tumcalendarcontrol/domain/AttendanceResolverTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/domain/CourseMeetingTimeTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/i18n/AppMessageTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/AppOwnedCalendarContractTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/GoogleCalendarGatewayTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/GoogleConnectionCheckerTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/IcalSourceParserTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/ManagedSyncEngineTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/ManualScheduleEditorTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/OwnedCalendarSyncExecutorTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/PrivateSyncConfigurationTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/ReadOnlyIcalImportServiceTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/RuleLedgerBackupCodecTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/RuleLedgerEditorTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/RuleLedgerPreferencesTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/SourceCheckFingerprintTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/SourceCourseCatalogTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/SourceOutputDateWindowTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/SourceReadCoordinatorTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/SyncCandidateFactoryTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/SyncDiffPlannerTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/SyncPreviewTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/SyncRuleResolverTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/sync/TargetStateReconcilerTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/AppLanguageTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/AttendanceEffectiveDateParserTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/BusinessSemanticsRegressionTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/CalendarControlScreenshotTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/CalendarControlStateTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/CourseDisplayGroupModelTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/CourseEditingRegressionTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/LocalizationScreenshotTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/PreinstallFixRegressionTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/SourceCourseBrowserModelTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/SourceCourseProjectionTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/SourceSnapshotStoreTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `app/src/test/java/com/tequila/tumcalendarcontrol/ui/WorkflowStateTest.kt` | Synthetische Eingaben, Testnamen oder Prüfung der chinesischen Oberfläche |
| `docs/LOKALISIERUNG.md` | Sprachname in seiner eigenen Schreibweise |
