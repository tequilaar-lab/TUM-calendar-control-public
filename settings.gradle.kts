/*
 * 职责：定义项目模块与插件仓库。
 * 输入／输出：Gradle 设置；输出为单一 app 模块的构建图。
 * 外部副作用：解析公开构建依赖。
 * 不负责：日历数据、同步规则或 Android 界面。
 */
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TUM Calendar Control"
include(":app")
