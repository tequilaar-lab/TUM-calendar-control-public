/*
 * 职责：固定全项目 Android 与 Kotlin 构建插件版本。
 * 输入／输出：Gradle 插件坐标；输出为模块可引用的插件。
 * 外部副作用：首次构建下载公开插件。
 * 不负责：模块依赖、应用逻辑或日历访问。
 */
plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
