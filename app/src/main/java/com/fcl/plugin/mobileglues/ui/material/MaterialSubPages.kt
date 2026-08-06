package com.fcl.plugin.mobileglues.ui.material

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

/**
 * GL 信息页：每次进来都重新查一次（渲染器的 .so 可能刚被游戏更新过），
 * 查询期间给进度指示，回来之后文本可选中、可一键复制。
 */
@Composable
fun MaterialGlInfoPage(controller: AppController) {
    val context = LocalContext.current
    val info by controller.glInfo.collectAsStateWithLifecycle()
    val loading by controller.glInfoLoading.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { controller.loadGlInfo() }

    SubPageScaffold(
        title = stringResource(R.string.dialog_mg_gl_info_title),
        onBack = { controller.navigateBack() },
        actions = {
            // 没有内容可复制的时候按钮不该在那儿等着被按。
            AnimatedVisibility(
                visible = !info.isNullOrBlank(),
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
            ) {
                IconButton(onClick = { copyGlInfo(context, controller, info.orEmpty()) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_copy),
                        contentDescription = stringResource(R.string.copy),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) {
        Crossfade(targetState = loading, label = "gl-info") { busy ->
            if (busy) {
                CenteredLoading(
                    text = stringResource(R.string.gl_info_loading),
                    modifier = Modifier.padding(top = 48.dp),
                )
            } else {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
                ) {
                    SelectableBody(
                        text = info.orEmpty(),
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
        BottomSpacer()
    }
}

private fun copyGlInfo(context: Context, controller: AppController, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(GL_INFO_CLIP_LABEL, text))
    // Android 13 起系统自己会弹一个复制提示，再来一条就是重复了。
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        controller.snackbar(context.getString(R.string.copied))
    }
}

/**
 * 隐私政策页。
 *
 * 逐条写清楚碰哪些文件、为什么要存储权限、本地存了什么，最后给出一条可以自己核实的事实：
 * 清单里没有 INTERNET 权限，所以「上传」在技术上根本做不到。
 */
@Composable
fun MaterialPrivacyPage(controller: AppController) {
    SubPageScaffold(
        title = stringResource(R.string.info_privacy),
        onBack = { controller.navigateBack() },
    ) {
        Text(
            text = stringResource(R.string.privacy_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = ScreenPadding + 4.dp, vertical = 8.dp),
        )
        // 标题在卡片外、正文在卡片内——和设置页的分组是同一套语法。
        PrivacySections.forEach { (title, body) ->
            PreferenceGroup(title = stringResource(title)) {
                Text(
                    text = stringResource(body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        }
        BottomSpacer()
    }
}

/** 子页面的骨架：返回键 + 标题 + 可选操作，下面是可滚动的内容。 */
@Composable
private fun SubPageScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.nav_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
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
