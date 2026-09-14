/*
 * 职责：配置 Android app 模块与 Compose、测试依赖。
 * 输入／输出：公开插件和库坐标；输出为 debug APK 与 JVM 单元测试任务。
 * 外部副作用：首次构建下载公开 AndroidX/Compose 依赖。
 * 不负责：网络、账户授权、iCal 解析和任何真实日历写入。
 */
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tequila.tumcalendarcontrol"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tequila.tumcalendarcontrol"
        minSdk = 26
        targetSdk = 37
        // 每次可验收发布均递增构建号；完整记录见仓库根目录 VERSIONING.md。
        versionCode = 27
        versionName = "0.4.5"

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
}
