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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** GL 信息页（Miuix）。 */
@Composable
fun MiuixGlInfoPage(controller: AppController) {
    val context = LocalContext.current
    val info by controller.glInfo.collectAsStateWithLifecycle()
    val loading by controller.glInfoLoading.collectAsStateWithLifecycle()

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
            MiuixGroup(title = stringResource(title)) {
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
                .verticalScroll(rememberScrollState()),
        ) {
            content()
        }
    }
}

private const val GL_INFO_CLIP_LABEL = "MobileGlues GL info"
