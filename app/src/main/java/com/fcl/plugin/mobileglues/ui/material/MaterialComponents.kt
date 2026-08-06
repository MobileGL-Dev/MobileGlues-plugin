package com.fcl.plugin.mobileglues.ui.material

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import com.fcl.plugin.mobileglues.R
import com.fcl.plugin.mobileglues.ui.ConfirmRequest
import kotlinx.coroutines.delay

/** MD3 皮肤主题：Android 12+ 用动态取色（Material You），以下回落到默认色板。 */
@Composable
fun MgMaterialTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/**
 * 解析含 HTML 的警告文案。`@colorError` 是作者写在 strings.xml 里的占位符，
 * 在这里换成当前皮肤的 error 色——同一套文案两个皮肤各自上色。
 */
@Composable
fun rememberHtmlAnnotatedString(html: String, errorColor: Color): AnnotatedString {
    return remember(html, errorColor) {
        val hex = String.format("#%06X", 0xFFFFFF and errorColor.toArgb())
        AnnotatedString.fromHtml(html.replace("@colorError", hex))
    }
}

/**
 * 「先问再改」的确认对话框，可选倒计时（确认键读秒，结束后变警示色）。
 * 返回键算「否」，点遮罩不关闭——和旧界面的语义一致。
 */
@Composable
fun MgConfirmDialog(request: ConfirmRequest) {
    var secondsLeft by remember(request) { mutableIntStateOf(request.countdownSeconds) }
    LaunchedEffect(request) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }

    val counting = secondsLeft > 0
    AlertDialog(
        onDismissRequest = { request.resolve(false) },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
        title = { Text(stringResource(request.titleRes)) },
        text = {
            if (request.messageIsHtml) {
                Text(rememberHtmlAnnotatedString(request.message, MaterialTheme.colorScheme.error))
            } else {
                Text(request.message)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { request.resolve(true) },
                enabled = !counting,
            ) {
                val text = if (counting) {
                    stringResource(R.string.ok_with_countdown, secondsLeft)
                } else {
                    stringResource(request.positiveRes)
                }
                Text(
                    text,
                    color = if (request.errorAccent && !counting) {
                        MaterialTheme.colorScheme.error
                    } else {
                        androidx.compose.ui.graphics.Color.Unspecified
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { request.resolve(false) }) {
                Text(stringResource(R.string.dialog_negative))
            }
        },
    )
}

/** 通用标题 + 文案对话框。 */
@Composable
fun MgTextDialog(
    title: String,
    text: String,
    positive: String,
    onPositive: () -> Unit,
    negative: String? = null,
    onNegative: (() -> Unit)? = null,
    onDismiss: () -> Unit = {},
    cancelable: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = { if (cancelable) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = cancelable,
            dismissOnClickOutside = cancelable,
        ),
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onPositive) { Text(positive) } },
        dismissButton = if (negative != null) {
            { TextButton(onClick = { onNegative?.invoke() }) { Text(negative) } }
        } else {
            null
        },
    )
}

/** 设置分组：小节标题 + 一组行。 */
@Composable
fun PreferenceGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

/** 一行设置：标题 + 尾部当前值，整行可点。 */
@Composable
fun TextPreferenceRow(
    title: String,
    value: String? = null,
    enabled: Boolean = true,
    titleColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                titleColor != Color.Unspecified -> titleColor
                else -> Color.Unspecified
            },
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

/** 一行开关设置。 */
@Composable
fun SwitchPreferenceRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                Color.Unspecified
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** 单选对话框：Spinner 的 Compose 替代。 */
@Composable
fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, label ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(index)
                                onDismiss()
                            }
                            .padding(vertical = 6.dp),
                    ) {
                        RadioButton(selected = index == selectedIndex, onClick = null)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_negative)) }
        },
    )
}

/** 整页居中的加载指示。 */
@Composable
fun CenteredLoading(text: String? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    ) {
        androidx.compose.material3.CircularProgressIndicator()
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/** 可选择中的整段文本（GL 信息）。 */
@Composable
fun SelectableBody(text: String, modifier: Modifier = Modifier) {
    SelectionContainer(modifier = modifier) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}
