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
        versionCode = 1
        versionName = "0.1.0"

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

    testImplementation("junit:junit:4.13.2")
}
