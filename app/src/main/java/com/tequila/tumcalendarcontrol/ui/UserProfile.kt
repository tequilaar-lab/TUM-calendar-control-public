/*
 * 职责：定义应用内个人资料的昵称与非图片头像颜色模型。
 * 输入／输出：输入昵称和颜色枚举；输出不可变的 UserProfile 值对象。
 * 外部副作用：无。
 * 不负责：文件头像、相册访问、账户身份、网络传输或日历权限。
 */
package com.tequila.tumcalendarcontrol.ui

enum class AvatarTone(val label: String) {
    TUM_BLUE("TUM 蓝"),
    SEA_GREEN("海绿"),
    SUNSET("暖橙"),
    LAVENDER("薰衣草"),
}

data class UserProfile(
    val displayName: String = "我",
    val avatarTone: AvatarTone = AvatarTone.TUM_BLUE,
)
