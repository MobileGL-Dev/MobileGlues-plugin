package com.fcl.plugin.mobileglues.ui.miuix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fcl.plugin.mobileglues.R
import com.fcl.plugin.mobileglues.settings.MGConfig
import com.fcl.plugin.mobileglues.settings.MultidrawEntry
import com.fcl.plugin.mobileglues.settings.MultidrawOrderItem
import com.fcl.plugin.mobileglues.settings.MultidrawSettings
import com.fcl.plugin.mobileglues.ui.AppController
import com.fcl.plugin.mobileglues.ui.DragReorderColumn
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

/** 折叠状态下的一句话摘要。 */
@Composable
fun miuixMultidrawSummary(settings: MultidrawSettings): String {
    val exceptionCount = settings.exceptions.size
    if (!settings.globalCustomized && exceptionCount == 0) {
        return stringResource(R.string.md_summary_default)
    }
    val global = if (settings.globalCustomized) {
        stringResource(R.string.md_summary_global_custom)
    } else {
        stringResource(R.string.md_summary_global_default)
    }
    return if (exceptionCount == 0) {
        global
    } else {
        "$global · ${stringResource(R.string.md_summary_exception_count, exceptionCount)}"
    }
}

/** MultiDraw 排序设置（Miuix 版）：全局 8 项排序 + 每函数例外排序 + benchmark。 */
@Composable
fun ColumnScope.MiuixMultidrawOrderContent(controller: AppController, config: MGConfig) {
    val context = LocalContext.current
    val settings = config.multidraw

    MiuixSectionHint(stringResource(R.string.md_order_hint))
    MiuixSectionHint(stringResource(R.string.md_order_drag_hint))

    DragReorderColumn(
        items = settings.globalOrder,
        onMove = controller::moveMultidrawGlobalItem,
    ) { index, item, dragging, handle ->
        MiuixOrderRow(
            position = index + 1,
            label = item.label(context).toString(),
            sublabel = if (item == MultidrawOrderItem.Native) {
                stringResource(R.string.md_item_native_desc)
            } else {
                null
            },
            dragging = dragging,
            handle = handle,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(
            text = stringResource(R.string.md_bench_run_global),
            onClick = { controller.runMultidrawBench(AppController.BenchTarget.Global) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
        AnimatedVisibility(visible = settings.globalCustomized, enter = fadeIn(), exit = fadeOut()) {
            TextButton(
                text = stringResource(R.string.md_reset_default),
                onClick = controller::resetMultidrawGlobalOrder,
            )
        }
    }

    // ---- 例外函数 ----

    SmallTitle(text = stringResource(R.string.md_exceptions_group))
    MiuixSectionHint(stringResource(R.string.md_exceptions_hint))

    MultidrawEntry.entries.forEach { entry ->
        val hasException = settings.hasException(entry)
        MiuixSwitchRow(
            title = entry.glFunction,
            summary = if (hasException) {
                stringResource(R.string.md_exception_on)
            } else {
                stringResource(R.string.md_exception_off)
            },
            checked = hasException,
            onCheckedChange = { controller.setMultidrawException(entry, it) },
        )
        AnimatedVisibility(
            visible = hasException,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
                DragReorderColumn(
                    items = settings.effectiveOrderFor(entry),
                    onMove = { from, to ->
                        controller.moveMultidrawExceptionItem(entry, from, to)
                    },
                ) { index, backend, dragging, handle ->
                    MiuixOrderRow(
                        position = index + 1,
                        label = backend.label(context).toString(),
                        sublabel = null,
                        dragging = dragging,
                        handle = handle,
                    )
                }
                TextButton(
                    text = stringResource(R.string.md_bench_run_entry),
                    onClick = {
                        controller.runMultidrawBench(AppController.BenchTarget.Entry(entry))
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun MiuixSectionHint(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** 一行可排序项：序号 + 名称 + 拖动手柄；被拖起来的那行浮到卡片上方。 */
@Composable
private fun MiuixOrderRow(
    position: Int,
    label: String,
    sublabel: String?,
    dragging: Boolean,
    handle: Modifier,
) {
    // HyperOS 的拖动是「托起来一块」而不是投影，所以只换底色不加阴影。
    val background by animateColorAsState(
        targetValue = if (dragging) {
            MiuixTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        label = "drag-background",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .heightIn(min = 46.dp)
            .padding(start = 8.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MiuixTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(22.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = position.toString(),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp, top = 6.dp, bottom = 6.dp)) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
            )
            if (sublabel != null) {
                Text(
                    text = sublabel,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = handle.size(44.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_drag_handle),
                contentDescription = stringResource(R.string.md_order_drag_handle),
                tint = if (dragging) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantActions
                },
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ---- Benchmark 对话框 ----

@Composable
fun MiuixMultidrawBenchDialogs(controller: AppController) {
    val context = LocalContext.current
    val state by controller.benchState.collectAsStateWithLifecycle()

    // 退场动画期间内容要从上一份非空值里取。
    val lastState = rememberLastNonNull(state)

    SuperDialog(
        show = state is AppController.BenchState.Running,
        title = stringResource(R.string.md_bench_running_title),
        onDismissRequest = null,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                InfiniteProgressIndicator(color = MiuixTheme.colorScheme.primary)
                Spacer(Modifier.width(20.dp))
                val attempt = (state as? AppController.BenchState.Running)?.attempt ?: 1
                Text(
                    text = if (attempt > 1) {
                        stringResource(
                            R.string.md_bench_running_retry,
                            attempt,
                            AppController.BENCH_MAX_ATTEMPTS,
                        )
                    } else {
                        stringResource(R.string.md_bench_running_msg)
                    },
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
            // 进度是 native 那边的原子计数器；老渲染器没有，那就只剩上面那圈。
            val progress = (state as? AppController.BenchState.Running)?.progress
            if (progress != null) {
                val animated by animateFloatAsState(progress, label = "bench-progress")
                LinearProgressIndicator(
                    progress = animated,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                )
            }
        }
    }

    val doneState = lastState as? AppController.BenchState.Done
    SuperDialog(
        show = state is AppController.BenchState.Done,
        title = stringResource(R.string.md_bench_result_title),
        onDismissRequest = controller::dismissBench,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = when (val target = doneState?.target) {
                        is AppController.BenchTarget.Entry -> stringResource(
                            R.string.md_bench_result_entry_intro,
                            target.entry.glFunction,
                        )
                        else -> stringResource(R.string.md_bench_result_global_intro)
                    },
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.heightIn(min = 12.dp))
                when (doneState?.target) {
                    is AppController.BenchTarget.Entry -> doneState.entryRanking.forEachIndexed { index, ranked ->
                        MiuixRankedRow(index + 1, ranked.item.label(context).toString(), ranked.relativeCost)
                    }
                    else -> doneState?.globalRanking?.forEachIndexed { index, ranked ->
                        MiuixRankedRow(index + 1, ranked.item.label(context).toString(), ranked.relativeCost)
                    }
                }
                MiuixBenchQualityNote(
                    noise = doneState?.noise,
                    rounds = doneState?.rounds ?: 0,
                    attempts = doneState?.attempts ?: 1,
                    noisy = doneState?.noisy == true,
                )
            }
            Spacer(Modifier.heightIn(min = 12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(R.string.md_bench_discard),
                    onClick = controller::dismissBench,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(
                        if (doneState?.noisy == true) {
                            R.string.md_bench_adopt_anyway
                        } else {
                            R.string.md_bench_adopt
                        },
                    ),
                    onClick = controller::adoptBenchResult,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }

    val failedState = lastState as? AppController.BenchState.Failed
    SuperDialog(
        show = state is AppController.BenchState.Failed,
        title = stringResource(R.string.md_bench_result_title),
        onDismissRequest = controller::dismissBench,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = failedState?.message.orEmpty(),
                fontSize = MiuixTheme.textStyles.body1.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.heightIn(min = 12.dp))
            TextButton(
                text = stringResource(R.string.ok),
                onClick = controller::dismissBench,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

/** 结果底下的一行小字：跑了几轮、抖动多大。抖动盖过名次差时得说出来。 */
@Composable
private fun MiuixBenchQualityNote(noise: Double?, rounds: Int, attempts: Int, noisy: Boolean) {
    if (noise == null || rounds <= 0) return
    Spacer(Modifier.heightIn(min = 12.dp))
    Text(
        text = stringResource(
            R.string.md_bench_quality,
            rounds,
            String.format(Locale.US, "%.1f", noise * 100),
        ),
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    if (noisy) {
        Text(
            text = stringResource(
                R.string.md_bench_noisy,
                attempts,
                String.format(Locale.US, "%.1f", noise * 100),
            ),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MiuixRankedRow(position: Int, label: String, relativeCost: Double?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(
            text = "$position.",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(end = 8.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface,
            )
            if (relativeCost == null) {
                Text(
                    text = stringResource(R.string.md_bench_unmeasured),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        if (relativeCost != null) {
            Text(
                text = stringResource(
                    R.string.md_bench_cost,
                    String.format(Locale.US, "%.2f", relativeCost),
                ),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.primary,
            )
        }
    }
}
