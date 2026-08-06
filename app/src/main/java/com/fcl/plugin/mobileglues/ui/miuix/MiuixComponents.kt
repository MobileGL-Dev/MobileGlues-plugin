package com.fcl.plugin.mobileglues.ui.miuix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.fcl.plugin.mobileglues.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/** Miuix 的卡片列表左右留白。 */
val MiuixScreenPadding = 12.dp

/** 页面大标题。 */
@Composable
fun MiuixPageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.title2,
        color = MiuixTheme.colorScheme.onBackground,
        modifier = modifier.padding(start = MiuixScreenPadding + 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** 设置分组：小标题 + 一张卡片，和 HyperOS 设置里的分组一致。 */
@Composable
fun MiuixGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) SmallTitle(text = title)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MiuixScreenPadding),
            cornerRadius = CardDefaults.CornerRadius,
            insideMargin = PaddingValues(vertical = 4.dp),
            content = content,
        )
    }
}

/** 一行只读信息：标题 +（可选）副标题。 */
@Composable
fun MiuixTextRow(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    titleColor: Color? = null,
    endActions: @Composable (RowScope.() -> Unit)? = null,
) {
    BasicComponent(
        title = title,
        titleColor = titleColors(titleColor),
        summary = summary,
        endActions = endActions,
        enabled = enabled,
    )
}

/** 一行「点进去」：右侧带箭头，是 Miuix 里表示「还有下一步」的方式。 */
@Composable
fun MiuixArrowRow(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    titleColor: Color? = null,
    onClick: () -> Unit,
) {
    SuperArrow(
        title = title,
        titleColor = titleColors(titleColor),
        summary = summary,
        enabled = enabled,
        onClick = onClick,
    )
}

/** 一行开关。 */
@Composable
fun MiuixSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    SuperSwitch(
        title = title,
        summary = summary,
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
    )
}

/** 一行下拉选择：点开就地弹出选项列表，这是 Miuix 版的 Spinner。 */
@Composable
fun MiuixDropdownRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
) {
    SuperDropdown(
        title = title,
        items = options,
        selectedIndex = selectedIndex,
        enabled = enabled,
        onSelectedIndexChange = onSelect,
    )
}

@Composable
private fun titleColors(color: Color?): BasicComponentColors = if (color != null) {
    BasicComponentDefaults.titleColor(color = color, disabledColor = color.copy(alpha = 0.4f))
} else {
    BasicComponentDefaults.titleColor()
}

/** 一行滑块：标题 + 当前值 + 滑块。 */
@Composable
fun MiuixSliderRow(
    title: String,
    valueLabel: String,
    position: Int,
    steps: Int,
    onPositionChange: (Int) -> Unit,
    onDragFinished: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Slider(
            value = position.toFloat(),
            onValueChange = { onPositionChange(it.roundToInt()) },
            onValueChangeFinished = onDragFinished,
            valueRange = 0f..steps.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 可展开的一节。 */
@Composable
fun MiuixExpandableSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Column(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = title,
            summary = summary,
            onClick = onToggle,
            endActions = {
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.size(22.dp).rotate(rotation),
                )
            },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

/** 一枚可开关的小标签（MultiDraw 的「禁用这些后端」）。 */
@Composable
fun MiuixToggleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        targetValue = if (selected) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.secondaryContainer
        },
        label = "chip-container",
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MiuixTheme.colorScheme.onPrimary
        } else {
            MiuixTheme.colorScheme.onSecondaryContainer
        },
        label = "chip-content",
    )
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = container,
        modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = content,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** 居中的加载指示。 */
@Composable
fun MiuixLoading(text: String? = null, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth().padding(32.dp),
    ) {
        InfiniteProgressIndicator(color = MiuixTheme.colorScheme.primary)
        if (text != null) {
            Text(
                text = text,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/** 可选中的整段文本（GL 信息）。 */
@Composable
fun MiuixSelectableBody(text: String, modifier: Modifier = Modifier) {
    SelectionContainer(modifier = modifier) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

/** 页面底部留白。 */
@Composable
fun MiuixBottomSpacer() {
    Spacer(Modifier.height(24.dp))
}

/** 矢量图标（Miuix 的组件要 [ImageVector]，不收 painter）。 */
@Composable
fun rememberVectorIcon(id: Int): ImageVector = ImageVector.vectorResource(id)

/**
 * 记住最后一个非空值。
 *
 * Miuix 的对话框靠 `show` 参数播放退场动画，状态一旦被清成 null，内容就跟着消失了——
 * 退场动画会变成「原地闪没」。留住上一份内容，动画才播得完。
 */
@Composable
fun <T : Any> rememberLastNonNull(value: T?): T? {
    var last by remember { mutableStateOf(value) }
    if (value != null) last = value
    return last
}
