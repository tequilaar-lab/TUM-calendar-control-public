/*
 * 职责：提供符合 Compose-Miuix 视觉令牌的可复用按压反馈图标与底部导航项。
 * 输入／输出：输入图标、文字、选中态和点击回调；输出带圆形／圆角按压色与缩放反馈的控件。
 * 外部副作用：仅在用户点击时调用上层回调。
 * 不负责：页面导航决策、主题持久化、Material UI 组件或真实日历操作。
 */
package com.tequila.tumcalendarcontrol.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.CompositionLocalProvider

/** 顶栏使用的圆形触感按钮：按住时缩小且出现明显圆形着色。 */
@Composable
fun TactileIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTumPalette.current
    TactileSurface(
        modifier = modifier.size(48.dp),
        onClick = onClick,
        shape = CircleShape,
        restingColor = Color.Transparent,
        pressedColor = palette.primary.copy(alpha = 0.18f),
    ) {
        Icon(icon, contentDescription)
    }
}

/** 浮动底栏的单项：选中态和按压态独立，使切换时有可见确认感。 */
@Composable
fun TactileNavigationItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTumPalette.current
    val contentColor = if (selected) palette.primary else palette.onSurfaceVariant
    TactileSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        restingColor = Color.Transparent,
        pressedColor = palette.primary.copy(alpha = 0.18f),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, label)
                Text(label, color = contentColor, style = MiuixTheme.textStyles.body2)
            }
        }
    }
}

/** 将触感状态集中在单一容器，避免各页面重复实现点击、形状与按压动画。 */
@Composable
private fun TactileSurface(
    modifier: Modifier,
    onClick: () -> Unit,
    shape: Shape,
    restingColor: Color,
    pressedColor: Color,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "按压缩放")
    val backgroundColor by animateColorAsState(
        if (pressed) pressedColor else restingColor,
        label = "按压着色",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}
