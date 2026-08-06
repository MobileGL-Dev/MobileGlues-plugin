package com.fcl.plugin.mobileglues.ui.miuix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fcl.plugin.mobileglues.R
import com.fcl.plugin.mobileglues.settings.AuthMethod
import com.fcl.plugin.mobileglues.ui.AppController
import com.fcl.plugin.mobileglues.ui.AuthPrompt
import com.fcl.plugin.mobileglues.ui.ConfirmRequest
import com.fcl.plugin.mobileglues.ui.Farewell
import com.fcl.plugin.mobileglues.ui.PrivacySections
import com.fcl.plugin.mobileglues.ui.SponsorChannel
import com.fcl.plugin.mobileglues.ui.SponsorChannels
import com.fcl.plugin.mobileglues.ui.SponsorPromptState
import com.fcl.plugin.mobileglues.utils.Constants
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.toArgb

/** Miuix 皮肤的全部全局对话框。与 MD3 皮肤一一对应，行为完全相同。 */
@Composable
fun MiuixDialogHost(controller: AppController) {
    val confirm by controller.confirmRequest.collectAsStateWithLifecycle()
    val corrupt by controller.corruptPrompt.collectAsStateWithLifecycle()
    val authPrompt by controller.authPrompt.collectAsStateWithLifecycle()
    val sponsor by controller.sponsorPrompt.collectAsStateWithLifecycle()
    val removing by controller.removing.collectAsStateWithLifecycle()
    val farewell by controller.farewell.collectAsStateWithLifecycle()
    val privacyConsentNeeded by controller.privacyConsentNeeded.collectAsStateWithLifecycle()
    val resetPrompt by controller.resetPrompt.collectAsStateWithLifecycle()
    val sponsorPicker by controller.sponsorPicker.collectAsStateWithLifecycle()
    val auth by controller.auth.state.collectAsStateWithLifecycle()

    // 道别排在最前：这时候隐私同意已经被收回了，别让同意弹窗抢在道别前面糊上来。
    farewell?.let { reason ->
        MiuixMessageDialog(
            show = true,
            title = stringResource(
                if (reason == Farewell.Removed) {
                    R.string.remove_complete_title
                } else {
                    R.string.revoke_complete_title
                },
            ),
            message = stringResource(
                if (reason == Farewell.Removed) {
                    R.string.remove_complete_message
                } else {
                    R.string.revoke_complete_message
                },
            ),
            positive = stringResource(R.string.exit),
            onPositive = controller::exitAfterFarewell,
            cancelable = false,
        )
        return
    }

    // 首次启动：先讲清楚这个 App 会碰什么，再谈别的。
    if (privacyConsentNeeded) {
        MiuixPrivacyConsentDialog(
            onAccept = controller::acceptPrivacy,
            onDecline = controller::declinePrivacy,
        )
        return
    }

    MiuixConfirmDialog(confirm)

    // 退场动画期间状态已经是 null 了，内容要从上一份非空值里取。
    val lastCorrupt = rememberLastNonNull(corrupt)
    MiuixMessageDialog(
        show = corrupt != null,
        title = stringResource(R.string.dialog_config_corrupt_title),
        message = lastCorrupt?.let {
            stringResource(
                R.string.dialog_config_corrupt_message,
                it.backupName ?: "-",
                it.cause.message ?: it.cause.javaClass.simpleName,
            )
        }.orEmpty(),
        positive = stringResource(R.string.dialog_config_corrupt_reset),
        onPositive = controller::resetCorruptConfig,
        negative = stringResource(R.string.dialog_negative),
        onNegative = controller::dismissCorruptConfig,
        onDismiss = controller::dismissCorruptConfig,
    )

    MiuixAuthMethodDialog(
        show = authPrompt == AuthPrompt.ChooseMethod,
        onSelect = controller::onAuthMethodSelected,
        onDismiss = controller::dismissAuthPrompt,
    )
    MiuixMessageDialog(
        show = authPrompt == AuthPrompt.AllFilesIntro,
        title = stringResource(R.string.dialog_permission_title),
        message = stringResource(R.string.dialog_permission_msg_android_Q, Constants.MG_DIRECTORY),
        positive = stringResource(R.string.dialog_positive),
        onPositive = controller::proceedAllFiles,
        negative = stringResource(R.string.dialog_negative),
        onNegative = controller::dismissAuthPrompt,
        onDismiss = controller::dismissAuthPrompt,
    )
    MiuixMessageDialog(
        show = authPrompt == AuthPrompt.SafGuide,
        title = stringResource(R.string.auth_guide_saf_title),
        message = stringResource(R.string.auth_guide_saf_msg),
        positive = stringResource(R.string.dialog_positive),
        onPositive = controller::proceedSaf,
        negative = stringResource(R.string.dialog_negative),
        onNegative = controller::dismissAuthPrompt,
        onDismiss = controller::dismissAuthPrompt,
    )
    MiuixMessageDialog(
        show = authPrompt == AuthPrompt.LegacyDenied,
        title = stringResource(R.string.dialog_permission_title),
        message = stringResource(R.string.dialog_permission_msg),
        positive = stringResource(R.string.dialog_positive),
        onPositive = controller::proceedAppDetails,
        negative = stringResource(R.string.dialog_negative),
        onNegative = controller::dismissAuthPrompt,
        onDismiss = controller::dismissAuthPrompt,
    )

    val lastSponsor = rememberLastNonNull(sponsor)
    MiuixMessageDialog(
        show = sponsor is SponsorPromptState.Ask,
        title = stringResource(R.string.sponsor_dialog_title),
        message = stringResource(
            R.string.sponsor_dialog_msg,
            (lastSponsor as? SponsorPromptState.Ask)?.launchCount ?: 0,
        ),
        positive = stringResource(R.string.sponsor_action_donate),
        onPositive = controller::onSponsorDonate,
        negative = stringResource(R.string.sponsor_action_later),
        onNegative = controller::onSponsorLater,
        onDismiss = controller::onSponsorLater,
    )
    MiuixMessageDialog(
        show = sponsor == SponsorPromptState.Confirm,
        title = stringResource(R.string.sponsor_confirm_title),
        message = stringResource(R.string.sponsor_confirm_msg),
        positive = stringResource(R.string.sponsor_action_donated),
        onPositive = controller::onSponsorDonated,
        negative = stringResource(R.string.sponsor_action_not_yet),
        onNegative = controller::onSponsorNotYet,
        onDismiss = controller::onSponsorNotYet,
    )

    MiuixSponsorChannelDialog(
        show = sponsorPicker,
        onSelect = controller::onSponsorChannelSelected,
        onDismiss = controller::dismissSponsorPicker,
    )

    MiuixResetDialog(
        show = resetPrompt,
        canDelete = auth.granted,
        onRevoke = controller::revokeAuthorization,
        onRemove = controller::removeMobileGlues,
        onDismiss = controller::dismissResetPrompt,
    )

    MiuixProgressDialog(show = removing, text = stringResource(R.string.removing_mobileglues))

}

/**
 * 首次启动的隐私政策同意框。
 *
 * 里面是政策全文而不是摘要——同意的那一刻用户看到的，就该是他事后能翻回去看的那些字。
 * 不可取消：返回键和点遮罩都不算表态。
 */
@Composable
private fun MiuixPrivacyConsentDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    SuperDialog(
        show = true,
        title = stringResource(R.string.privacy_consent_title),
        onDismissRequest = null,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.privacy_intro),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                PrivacySections.forEach { (title, body) ->
                    Text(
                        text = stringResource(title),
                        style = MiuixTheme.textStyles.title4,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        text = stringResource(body),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.privacy_consent_footer),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
            Spacer(Modifier.size(20.dp))
            DialogButtons(
                negative = stringResource(R.string.privacy_consent_decline),
                onNegative = onDecline,
                positive = stringResource(R.string.privacy_consent_accept),
                onPositive = onAccept,
            )
        }
    }
}

/** 「先问再改」确认框，可选倒计时。 */
@Composable
private fun MiuixConfirmDialog(request: ConfirmRequest?) {
    val current = rememberLastNonNull(request)
    var secondsLeft by remember(current) { mutableIntStateOf(current?.countdownSeconds ?: 0) }
    LaunchedEffect(current, request != null) {
        if (request == null) return@LaunchedEffect
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }
    if (current == null) return

    val counting = secondsLeft > 0
    val errorColor = MiuixTheme.colorScheme.error
    val message = if (current.messageIsHtml) {
        remember(current, errorColor) {
            AnnotatedString.fromHtml(
                current.message.replace("@colorError", String.format("#%06X", 0xFFFFFF and errorColor.toArgb())),
            )
        }
    } else {
        remember(current) { AnnotatedString(current.message) }
    }

    SuperDialog(
        show = request != null,
        title = stringResource(current.titleRes),
        onDismissRequest = { request?.resolve(false) },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = message,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.size(20.dp))
            DialogButtons(
                negative = stringResource(R.string.dialog_negative),
                onNegative = { request?.resolve(false) },
                positive = if (counting) {
                    stringResource(R.string.ok_with_countdown, secondsLeft)
                } else {
                    stringResource(current.positiveRes)
                },
                onPositive = { request?.resolve(true) },
                positiveEnabled = !counting,
                positiveColor = if (current.errorAccent && !counting) errorColor else null,
            )
        }
    }
}

/** 标题 + 正文 + 一到两个按钮。 */
@Composable
private fun MiuixMessageDialog(
    show: Boolean,
    title: String,
    message: String,
    positive: String,
    onPositive: () -> Unit,
    negative: String? = null,
    onNegative: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    cancelable: Boolean = true,
) {
    SuperDialog(
        show = show,
        title = title,
        onDismissRequest = if (cancelable) ({ onDismiss?.invoke() }) else null,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = message,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.size(20.dp))
            DialogButtons(
                negative = negative,
                onNegative = { onNegative?.invoke() },
                positive = positive,
                onPositive = onPositive,
            )
        }
    }
}

/** 授权方式二选一。 */
@Composable
private fun MiuixAuthMethodDialog(
    show: Boolean,
    onSelect: (AuthMethod) -> Unit,
    onDismiss: () -> Unit,
) {
    SuperDialog(
        show = show,
        title = stringResource(R.string.auth_choose_title),
        summary = stringResource(R.string.auth_choose_msg),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AuthMethodOption(
                title = stringResource(R.string.auth_method_all_files),
                description = stringResource(R.string.auth_method_all_files_desc),
                onClick = { onSelect(AuthMethod.AllFiles) },
            )
            Spacer(Modifier.size(10.dp))
            AuthMethodOption(
                title = stringResource(R.string.auth_method_saf),
                description = stringResource(R.string.auth_method_saf_desc),
                onClick = { onSelect(AuthMethod.Saf) },
            )
            Spacer(Modifier.size(20.dp))
            DialogButtons(
                negative = null,
                onNegative = {},
                positive = stringResource(R.string.dialog_negative),
                onPositive = onDismiss,
                positiveIsPrimary = false,
            )
        }
    }
}

/**
 * 赞助渠道。
 *
 * 每条都把网址写出来：爱发电有三个域名在用、收款方也各不相同，只写平台名的话
 * 用户点下去之前不知道钱会到谁那里。
 */
@Composable
private fun MiuixSponsorChannelDialog(
    show: Boolean,
    onSelect: (SponsorChannel) -> Unit,
    onDismiss: () -> Unit,
) {
    SuperDialog(
        show = show,
        title = stringResource(R.string.dialog_sponsor),
        summary = stringResource(R.string.sponsor_channels_msg),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                SponsorChannels.forEachIndexed { index, channel ->
                    if (index > 0) Spacer(Modifier.size(10.dp))
                    AuthMethodOption(
                        title = channel.label,
                        description = channel.url,
                        onClick = { onSelect(channel) },
                    )
                }
            }
            Spacer(Modifier.size(20.dp))
            DialogButtons(
                negative = null,
                onNegative = {},
                positive = stringResource(R.string.dialog_negative),
                onPositive = onDismiss,
                positiveIsPrimary = false,
            )
        }
    }
}

/** 撤销 / 重置的二选一。删文件那条在未授权时点不动——没有访问权就删不了。 */
@Composable
private fun MiuixResetDialog(
    show: Boolean,
    canDelete: Boolean,
    onRevoke: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    SuperDialog(
        show = show,
        title = stringResource(R.string.menu_item_reset),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AuthMethodOption(
                title = stringResource(R.string.reset_option_revoke),
                description = stringResource(R.string.reset_option_revoke_desc),
                onClick = onRevoke,
            )
            Spacer(Modifier.size(10.dp))
            AuthMethodOption(
                title = stringResource(R.string.reset_option_remove),
                description = if (canDelete) {
                    stringResource(R.string.reset_option_remove_desc)
                } else {
                    stringResource(R.string.reset_option_remove_needs_auth)
                },
                onClick = onRemove,
                enabled = canDelete,
                titleColor = MiuixTheme.colorScheme.error,
            )
            Spacer(Modifier.size(20.dp))
            DialogButtons(
                negative = null,
                onNegative = {},
                positive = stringResource(R.string.dialog_negative),
                onPositive = onDismiss,
                positiveIsPrimary = false,
            )
        }
    }
}

@Composable
private fun AuthMethodOption(
    title: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    titleColor: Color? = null,
) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = if (enabled) onClick else null) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title4,
                color = (titleColor ?: MiuixTheme.colorScheme.onSurface)
                    .copy(alpha = if (enabled) 1f else 0.4f),
            )
            Text(
                text = description,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    .copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** 不可取消的进度对话框。 */
@Composable
private fun MiuixProgressDialog(show: Boolean, text: String) {
    SuperDialog(show = show, onDismissRequest = null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            InfiniteProgressIndicator(color = MiuixTheme.colorScheme.primary)
            Spacer(Modifier.width(20.dp))
            Text(
                text = text,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 对话框底部按钮：左取消右确认，等宽。 */
@Composable
private fun DialogButtons(
    negative: String?,
    onNegative: () -> Unit,
    positive: String,
    onPositive: () -> Unit,
    positiveEnabled: Boolean = true,
    positiveIsPrimary: Boolean = true,
    positiveColor: Color? = null,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (negative != null) {
            TextButton(
                text = negative,
                onClick = onNegative,
                modifier = Modifier.weight(1f),
            )
        }
        TextButton(
            text = positive,
            onClick = onPositive,
            enabled = positiveEnabled,
            modifier = Modifier.weight(1f),
            colors = when {
                positiveColor != null -> ButtonDefaults.textButtonColors(
                    textColor = positiveColor,
                )

                positiveIsPrimary -> ButtonDefaults.textButtonColorsPrimary()
                else -> ButtonDefaults.textButtonColors()
            },
        )
    }
}
