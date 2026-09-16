/*
 * 职责：在折叠、旋转引发的 Activity 重建期间保留同一应用会话和在途请求身份。
 * 输入／输出：界面状态和操作令牌；输出是否首次启动请求。
 * 外部副作用：仅内存保存，不写令牌到磁盘，不持有 Activity。
 * 不负责：进程死亡恢复、OAuth、来源读取或日历操作。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.lifecycle.ViewModel

class CalendarSessionModel : ViewModel() {
    var state: CalendarControlState? = null
    private val started = mutableSetOf<String>()

    /** 重建后的组合不能再次发起仍在运行的同一写入。 */
    fun claim(kind: String, token: Long): Boolean = started.add("$kind:$token")
}
