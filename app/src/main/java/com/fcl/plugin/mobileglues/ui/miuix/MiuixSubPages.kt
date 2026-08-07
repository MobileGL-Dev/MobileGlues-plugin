package com.fcl.plugin.mobileglues.ui.miuix

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fcl.plugin.mobileglues.R
import com.fcl.plugin.mobileglues.ui.AppController
import com.fcl.plugin.mobileglues.ui.PrivacySections
import com.fcl.plugin.mobileglues.ui.ThirdPartyGroups
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** GL 信息页（Miuix）。 */
@Composable
fun MiuixGlInfoPage(controller: AppController) {
    val context = LocalContext.current
    val info by controller.glInfo.collectAsStateWithLifecycle()
    val loading by controller.glInfoLoading.collectAsStateWithLifecycle()
    val needsAngle by controller.glInfoNeedsAngle.collectAsStateWithLifecycle()
    val borrowedAngle by controller.glInfoBorrowedAngle.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { controller.loadGlInfo() }

    MiuixSubPage(
        title = stringResource(R.string.dialog_mg_gl_info_title),
        onBack = { controller.navigateBack() },
        actions = {
            AnimatedVisibility(
                visible = !info.isNullOrBlank(),
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
            ) {
                IconButton(onClick = { copyGlInfo(context, controller, info.orEmpty()) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_copy),
                        contentDescription = stringResource(R.string.copy),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        },
    ) {
        Crossfade(targetState = loading, label = "gl-info") { busy ->
            if (busy) {
                MiuixLoading(
                    text = stringResource(R.string.gl_info_loading),
                    modifier = Modifier.padding(top = 48.dp),
                )
            } else {
                Column {
                    // ANGLE 随启动器走，本 App 里没有；不借的话这一页讲的是系统驱动，
                    // 不是游戏里那个。借不借由用户点——不能因为他只想看一眼就自作主张
                    // 把别人的原生代码载进来。
                    if (needsAngle) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MiuixScreenPadding, vertical = 4.dp),
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = stringResource(R.string.md_glinfo_needs_angle),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.primary,
                                )
                                TextButton(
                                    text = stringResource(R.string.md_glinfo_borrow),
                                    onClick = { controller.reloadGlInfoWithAngle() },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                )
                            }
                        }
                    } else if (borrowedAngle) {
                        Text(
                            text = stringResource(R.string.md_glinfo_borrowed),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(horizontal = MiuixScreenPadding, vertical = 4.dp),
                        )
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MiuixScreenPadding),
                    ) {
                        MiuixSelectableBody(
                            text = info.orEmpty(),
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                }
            }
        }
        MiuixBottomSpacer()
    }
}

private fun copyGlInfo(context: Context, controller: AppController, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(GL_INFO_CLIP_LABEL, text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        controller.snackbar(context.getString(R.string.copied))
    }
}

/** 隐私政策页（Miuix）。 */
@Composable
fun MiuixPrivacyPage(controller: AppController) {
    MiuixSubPage(
        title = stringResource(R.string.info_privacy),
        onBack = { controller.navigateBack() },
    ) {
        Text(
            text = stringResource(R.string.privacy_intro),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            modifier = Modifier.padding(horizontal = MiuixScreenPadding + 16.dp, vertical = 8.dp),
        )
        // 标题在卡片外、正文在卡片内——和设置页的分组是同一套语法。
        PrivacySections.forEach { (title, body) ->
            MiuixGroup(
                title = stringResource(title),
                titleColor = MiuixTheme.colorScheme.onSurface,
            ) {
                Text(
                    text = stringResource(body),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        MiuixBottomSpacer()
    }
}

/**
 * 第三方开源项目（Miuix）。
 *
 * 分「渲染器」和「插件」两组：用户看到 SPIRV-Cross 的时候，应该同时知道它是被游戏里
 * 那个 .so 用的，而不是被这个设置界面用的。每一项都能点开自己的主页去看许可证原文——
 * 在这里抄一份许可证全文，既没人读，也保证不了和上游一致。
 */
@Composable
fun MiuixThirdPartyPage(controller: AppController) {
    MiuixSubPage(
        title = stringResource(R.string.third_party_title),
        onBack = { controller.navigateBack() },
    ) {
        Text(
            text = stringResource(R.string.third_party_intro),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            modifier = Modifier.padding(horizontal = MiuixScreenPadding + 16.dp, vertical = 8.dp),
        )
        ThirdPartyGroups.forEach { group ->
            MiuixGroup(title = stringResource(group.title)) {
                group.components.forEach { component ->
                    MiuixArrowRow(
                        title = component.name,
                        summary = "${component.author} · ${component.license}",
                        onClick = { controller.openThirdPartyComponent(component) },
                    )
                }
            }
        }
        MiuixBottomSpacer()
    }
}

/** 子页面骨架：返回 + 标题 + 操作，下面是可滚动内容。 */
@Composable
private fun MiuixSubPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.nav_back),
                    tint = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = title,
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            actions()
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 甩到顶或底时给一下振动——HyperOS 的滚动到此为止就是这个手感。
                .scrollEndHaptic()
                .verticalScroll(rememberScrollState()),
        ) {
            content()
        }
    }
}

private const val GL_INFO_CLIP_LABEL = "MobileGlues GL info"
