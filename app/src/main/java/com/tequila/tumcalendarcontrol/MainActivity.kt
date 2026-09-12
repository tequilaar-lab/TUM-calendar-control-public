/*
 * 职责：启动离线演示界面并提供合成示例。
 * 输入／输出：无外部输入；输出为 Compose 界面。
 * 外部副作用：仅渲染本地内存中的合成数据。
 * 不负责：持久化、网络、账户、iCal 或日历 Provider 访问。
 */
package com.tequila.tumcalendarcontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.tequila.tumcalendarcontrol.domain.SampleSchedule
import com.tequila.tumcalendarcontrol.ui.CalendarControlScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalendarControlScreen(events = SampleSchedule.events())
        }
    }
}
