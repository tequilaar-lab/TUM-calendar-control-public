/*
 * 职责：启动离线的 Compose-Miuix 应用壳。
 * 输入／输出：无外部输入；输出为可交互的本地课程界面。
 * 外部副作用：仅渲染内存中的合成课程，进程结束后自动丢弃变更。
 * 不负责：持久化、网络、账户、iCal 或日历 Provider 访问。
 */
package com.tequila.tumcalendarcontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tequila.tumcalendarcontrol.ui.CalendarControlApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalendarControlApp()
        }
    }
}
