/*
 * 职责：配置 Android app 模块与 Compose、测试依赖。
 * 输入／输出：公开插件和库坐标；输出为 debug APK 与 JVM 单元测试任务。
 * 外部副作用：首次构建下载公开 AndroidX/Compose 依赖。
 * 不负责：网络、账户授权、iCal 解析和任何真实日历写入。
 */
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import dev.detekt.gradle.Detekt

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
    // 只生成诊断报告；不会格式化源文件，也不会成为 check 的前置任务。
    id("org.jlleitschuh.gradle.ktlint")
    id("dev.detekt")
}

android {
    namespace = "com.tequila.tumcalendarcontrol"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tequila.tumcalendarcontrol"
        minSdk = 26
        targetSdk = 37
        // 每次可验收发布均递增构建号；完整记录见仓库根目录 VERSIONING.md。
        versionCode = 54
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += "META-INF/groovy/**"
        resources.excludes += "META-INF/groovy-release-info.properties"
        resources.excludes += "META-INF/INDEX.LIST"
    }
}

kotlin {
    // 使用现有较高版本 JDK 运行构建，同时保持产物与 Java 17 兼容。
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// 职责：提供可手动运行的静态分析报告，不改写代码、不设质量门槛、不接入常规构建。
// 输入／输出：Kotlin 与 Gradle 源码；输出为 app/build/reports 下的 Ktlint 与 Detekt 报告。
// 外部副作用：首次运行解析公开分析器依赖；不访问日历、账户或设备。
// 不负责：自动格式化、基线生成、抑制规则或阻断 check／assemble。
ktlint {
    ignoreFailures.set(true)
    outputToConsole.set(false)
}

detekt {
    toolVersion = "2.0.0-alpha.6"
    ignoreFailures = true
    autoCorrect = false
}

tasks.withType<Detekt>().configureEach {
    reports {
        html.required.set(true)
        checkstyle.required.set(true)
        sarif.required.set(false)
        markdown.required.set(false)
    }
}

val lintCompatibilityReport = tasks.register("lintCompatibilityReport") {
    group = "verification"
    description = "试验性生成 Ktlint 与 Detekt 报告；不格式化且不阻断构建。"
    dependsOn("ktlintCheck", "detekt")
}

// 两插件会默认把检查任务接到 Gradle check；恢复 Android app 原有的 lint 与 test 直接依赖，
// 使新增分析只能由上方手动入口触发，而不影响既有 Android 校验范围。
tasks.named("check") {
    setDependsOn(listOf(tasks.named("lint"), tasks.named("test")))
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    // 仅提供 Rounded 图标向量；界面组件仍全部来自 Compose-Miuix。
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.3")
    // 成熟的 RFC 5545 解析器；最低 API 26 与现有 Java 17 构建设置满足其 Android 前提。
    implementation("org.mnode.ical4j:ical4j:3.1.0")
    // 官方 Android 授权客户端；只在用户点按连接 Google 时请求最小的 app.created 范围。
    implementation("com.google.android.gms:play-services-auth:21.6.0")
    // Android 官方稳定 WorkManager；后台任务只读检查来源，不执行日历同步。
    implementation("androidx.work:work-runtime:2.11.2")

    testImplementation("junit:junit:4.13.2")
    // 仅用于本机 Compose 截图回归；不连接设备、账户或日历。
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.74.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.74.0")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.7.8")
    testImplementation("org.robolectric:robolectric:4.16")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.8")
}
