/*
 * 职责：提供私有 iCal 地址的密码式输入与短暂末位显示。
 * 输入／输出：输入本次尚未保存的地址草稿；输出同长度的遮罩显示和用户修改后的草稿。
 * 外部副作用：仅维护 Compose 内存状态与短暂计时；不保存、不联网、不读取已保存地址。
 * 不负责：地址校验、Android Keystore 加密、配置持久化或日历同步。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField

private const val LAST_CHARACTER_REVEAL_MILLIS = 1500L

@Composable
fun PrivateAddressInput(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var showAddress by remember { mutableStateOf(false) }
    var lastVisibleIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(lastVisibleIndex, value, showAddress) {
        if (!showAddress && lastVisibleIndex >= 0) {
            delay(LAST_CHARACTER_REVEAL_MILLIS)
            lastVisibleIndex = -1
        }
    }
    TextField(
        value = value,
        onValueChange = { updated ->
            lastVisibleIndex = if (!showAddress && updated.length > value.length) updated.length - 1 else -1
            onValueChange(updated)
        },
        modifier = modifier.fillMaxWidth(),
        label = "新的 TUM 私有 iCal 地址",
        singleLine = true,
        visualTransformation = PrivateAddressVisualTransformation(showAddress, lastVisibleIndex),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Switch(checked = showAddress, onCheckedChange = { visible ->
            showAddress = visible
            if (!visible) lastVisibleIndex = -1
        })
        Text(if (showAddress) "显示地址（仅本次输入）" else "显示地址")
    }
}

private class PrivateAddressVisualTransformation(
    private val showAll: Boolean,
    private val visibleIndex: Int,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val rendered = text.text.mapIndexed { index, character ->
            if (showAll || index == visibleIndex) character else '•'
        }.joinToString(separator = "")
        return TransformedText(AnnotatedString(rendered), OffsetMapping.Identity)
    }
}
