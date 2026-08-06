@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.fcl.plugin.mobileglues.ui.miuix

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fcl.plugin.mobileglues.R
import com.fcl.plugin.mobileglues.ui.AppController
import com.fcl.plugin.mobileglues.ui.Contributor
import com.fcl.plugin.mobileglues.ui.ContributorGroups
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 贡献者致谢（Miuix）：三个仓库各一面头像墙。
 *
 * 头像是随包内置的，不是运行时拉的——本应用没有 INTERNET 权限，而那是隐私政策里
 * 请用户自己去核实的一条。名单因此停在打包的那一刻，这个代价比作废那条承诺小得多。
 */
@Composable
fun MiuixContributorsSection(controller: AppController) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(text = stringResource(R.string.contributors_title))
        Text(
            text = stringResource(R.string.contributors_intro),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            modifier = Modifier.padding(horizontal = MiuixScreenPadding + 16.dp),
        )

        ContributorGroups.forEach { group ->
            Text(
                text = stringResource(group.title),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.padding(
                    start = MiuixScreenPadding + 16.dp,
                    top = 16.dp,
                    bottom = 8.dp,
                ),
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MiuixScreenPadding + 4.dp),
            ) {
                group.contributors.forEach { contributor ->
                    ContributorFace(
                        contributor = contributor,
                        onClick = { controller.openContributor(contributor) },
                    )
                }
            }
        }
    }
}

/** 一张头像加一个名字。名字比头像窄的时候截断，不去挤旁边那位。 */
@Composable
private fun ContributorFace(contributor: Contributor, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(FaceWidth)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
    ) {
        Image(
            painter = painterResource(contributor.avatar),
            contentDescription = contributor.login,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(CircleShape),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = contributor.login,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val FaceWidth = 72.dp
