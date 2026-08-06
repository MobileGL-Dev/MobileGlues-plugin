package com.fcl.plugin.mobileglues.ui.miuix

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fcl.plugin.mobileglues.R
import com.fcl.plugin.mobileglues.ui.AppController
import com.fcl.plugin.mobileglues.ui.AppSubPage
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 信息页（Miuix）。 */
@Composable
fun MiuixInfoPage(controller: AppController) {
    val auth by controller.auth.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        MiuixPageTitle(stringResource(R.string.nav_info))

        MiuixGroup(title = stringResource(R.string.info_section_app)) {
            MiuixTextRow(
                title = stringResource(R.string.info_version_label),
                summary = controller.appVersionName,
            )
            MiuixArrowRow(
                title = stringResource(R.string.dialog_github),
                summary = stringResource(R.string.repo_summary),
                onClick = controller::openSourceRepositories,
            )
            MiuixArrowRow(
                title = stringResource(R.string.dialog_sponsor),
                summary = stringResource(R.string.sponsor_channels_summary),
                onClick = controller::openSponsorChannels,
            )
        }

        MiuixGroup(title = stringResource(R.string.info_section_about)) {
            MiuixTextRow(
                title = label(R.string.view_author),
                summary = stringResource(R.string.info_author),
            )
            MiuixTextRow(
                title = label(R.string.view_copyright),
                summary = stringResource(R.string.info_copyright),
            )
            MiuixTextRow(
                title = label(R.string.view_launcher),
                summary = stringResource(R.string.info_launcher),
            )
            MiuixTextRow(
                title = label(R.string.view_logo),
                summary = stringResource(R.string.info_logo),
            )
            MiuixArrowRow(
                title = stringResource(R.string.third_party_title),
                summary = stringResource(R.string.third_party_summary),
                onClick = { controller.openSubPage(AppSubPage.ThirdParty) },
            )
        }

        MiuixGroup(title = stringResource(R.string.info_section_details)) {
            MiuixArrowRow(
                title = stringResource(R.string.info_mg_info),
                onClick = { controller.openSubPage(AppSubPage.GlInfo) },
            )
            MiuixArrowRow(
                title = stringResource(R.string.info_privacy),
                onClick = { controller.openSubPage(AppSubPage.Privacy) },
            )
        }

        MiuixGroup(title = stringResource(R.string.info_danger_zone)) {
            // 撤销和删除是包含关系，摆成两个平级按钮会互相锁死：撤了就删不动，
            // 删完了也没什么可撤。所以入口只有一个，进去再选做到哪一步。
            MiuixArrowRow(
                title = stringResource(R.string.menu_item_reset),
                titleColor = MiuixTheme.colorScheme.error,
                onClick = controller::openResetPrompt,
            )
        }

        // 致谢摆在最后：它是这一页读到底之后的落款，不是一个要跳过去的功能。
        MiuixContributorsSection(controller)

        MiuixBottomSpacer()
    }
}

/** 「版本：」这类文案自带冒号，当行标题用的时候要去掉。 */
@Composable
private fun label(@StringRes id: Int): String = stringResource(id).trimEnd(' ', ':', '：')
