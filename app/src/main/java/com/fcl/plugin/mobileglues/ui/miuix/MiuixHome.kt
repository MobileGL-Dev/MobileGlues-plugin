package com.fcl.plugin.mobileglues.ui.miuix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fcl.plugin.mobileglues.DeviceInfo
import com.fcl.plugin.mobileglues.R
import com.fcl.plugin.mobileglues.ui.AppController
import com.fcl.plugin.mobileglues.ui.AppTab
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 首页（Miuix）：与 MD3 皮肤同样的内容与动画节奏，换成 Miuix 的字体与配色。 */
@Composable
fun MiuixHomePage(controller: AppController) {
    val auth by controller.auth.state.collectAsStateWithLifecycle()
    val deviceInfo by controller.deviceInfo.collectAsStateWithLifecycle()
    val config by controller.configStore.config.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { controller.ensureDeviceInfo() }

    // 启动次数记在 MG 目录里，未授权时读不到；授权建立之后再问一次。
    LaunchedEffect(auth.granted) {
        if (auth.granted) controller.maybeShowSponsorPrompt()
    }

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(horizontal = MiuixScreenPadding + 8.dp),
    ) {
        EnterUp(entered, delayMillis = 0) { Wordmark(controller.appVersionName) }

        Spacer(Modifier.height(28.dp))

        EnterUp(entered, delayMillis = 90) {
            AuthPill(
                granted = auth.granted,
                onClick = { if (!auth.granted) controller.requestAccess() },
            )
        }

        Spacer(Modifier.height(28.dp))

        EnterUp(entered, delayMillis = 160) { DeviceInfoBlock(deviceInfo) }

        Spacer(Modifier.height(24.dp))

        EnterUp(entered, delayMillis = 230) {
            Crossfade(targetState = config, label = "summary") { current ->
                if (current != null) {
                    ConfigSummaryCard(
                        summary = controller.configSummary(current),
                        onClick = { controller.navigateTab(AppTab.Settings) },
                    )
                } else {
                    Spacer(Modifier.height(1.dp))
                }
            }
        }

        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun Wordmark(version: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = MiuixTheme.colorScheme.onBackground)) { append("Mobile") }
                withStyle(SpanStyle(color = MiuixTheme.colorScheme.primary)) { append("Glues") }
            },
            style = MiuixTheme.textStyles.title1,
            fontSize = 40.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = "v$version",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun AuthPill(granted: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        targetValue = if (granted) {
            MiuixTheme.colorScheme.primaryContainer
        } else {
            MiuixTheme.colorScheme.errorContainer
        },
        animationSpec = tween(320),
        label = "pill-container",
    )
    val content by animateColorAsState(
        targetValue = if (granted) {
            MiuixTheme.colorScheme.onPrimaryContainer
        } else {
            MiuixTheme.colorScheme.error
        },
        animationSpec = tween(320),
        label = "pill-content",
    )

    Surface(shape = CircleShape, color = container) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(enabled = !granted, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(content))
            Spacer(Modifier.size(10.dp))
            Text(
                text = stringResource(
                    if (granted) R.string.home_status_granted else R.string.home_status_denied,
                ),
                style = MiuixTheme.textStyles.body2,
                color = content,
            )
        }
    }
}

@Composable
private fun DeviceInfoBlock(info: DeviceInfo?) {
    val unknown = stringResource(R.string.home_device_unknown)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
    ) {
        DeviceInfoRow(
            label = stringResource(R.string.home_device_gpu),
            value = info?.gpuRenderer?.takeIf { it.isNotBlank() } ?: unknown,
            loaded = info != null,
        )
        DeviceInfoRow(
            label = stringResource(R.string.home_device_gles),
            value = info?.glesVersion?.takeIf { it.isNotBlank() } ?: unknown,
            loaded = info != null,
        )
        DeviceInfoRow(
            label = stringResource(R.string.home_device_ram),
            value = info?.let { stringResource(R.string.home_ram_value, it.totalRamBytes / GIBIBYTE) }
                ?: unknown,
            loaded = info != null,
        )
    }
}

@Composable
private fun DeviceInfoRow(label: String, value: String, loaded: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Spacer(Modifier.size(16.dp))
        Crossfade(targetState = loaded, label = "device-value", modifier = Modifier.weight(1f)) {
            Text(
                text = if (it) value else "…",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ConfigSummaryCard(summary: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(
                text = summary,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.home_config_hint),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun EnterUp(visible: Boolean, delayMillis: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(durationMillis = 420, delayMillis = delayMillis)) +
            slideInVertically(
                animationSpec = tween(
                    durationMillis = 420,
                    delayMillis = delayMillis,
                    easing = FastOutSlowInEasing,
                ),
            ) { it / 3 },
        exit = ExitTransition.None,
    ) {
        content()
    }
}

private const val GIBIBYTE = 1024.0 * 1024.0 * 1024.0
