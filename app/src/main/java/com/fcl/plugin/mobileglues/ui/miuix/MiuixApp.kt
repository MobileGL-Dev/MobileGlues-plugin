package com.fcl.plugin.mobileglues.ui.miuix

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fcl.plugin.mobileglues.R
import com.fcl.plugin.mobileglues.ui.AppController
import com.fcl.plugin.mobileglues.ui.AppSubPage
import com.fcl.plugin.mobileglues.ui.AppTab
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * Miuix 皮肤的外壳。
 *
 * 结构与 MD3 皮肤一一对应，用的却是完全不同的一套组件：这里是两套界面，不是换个配色。
 * 唯一共享的是 [AppController]——所有操作逻辑因此只有一份。
 */
@Composable
fun MiuixApp(controller: AppController) {
    val dark = isSystemInDarkTheme()
    MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
        val tab by controller.tab.collectAsStateWithLifecycle()
        val subPage by controller.subPage.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(controller) {
            controller.snackbar.collect { snackbarHostState.showSnackbar(it.toString()) }
        }

        BackHandler(enabled = subPage != null) { controller.navigateBack() }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            // Miuix 的语义与 MD3 相反：页面是 surface（深色下是纯黑），卡片才是 surfaceContainer。
            // 把页面画成 background 会和卡片同色，分组就看不见了。
            containerColor = MiuixTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                AnimatedVisibility(
                    visible = subPage == null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    MiuixNavigationBar(current = tab, onSelect = controller::navigateTab)
                }
            },
        ) { innerPadding ->
            // 对话框宿主要在 Scaffold 之内：Miuix 的弹窗渲染进 Scaffold 提供的 popup host。
            Box(modifier = Modifier.fillMaxSize()) {
                // 每页的滚动位置各自存一份：从子页面退回来时，列表还停在原处。
                val pageState = rememberSaveableStateHolder()
                AnimatedContent(
                    targetState = subPage ?: tab,
                    transitionSpec = { miuixPageTransition(initialState, targetState) },
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    label = "page",
                ) { destination ->
                    pageState.SaveableStateProvider(destination) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            when (destination) {
                                AppTab.Home -> MiuixHomePage(controller)
                                AppTab.Settings -> MiuixSettingsPage(controller)
                                AppTab.Info -> MiuixInfoPage(controller)
                                AppSubPage.GlInfo -> MiuixGlInfoPage(controller)
                                AppSubPage.Privacy -> MiuixPrivacyPage(controller)
                            }
                        }
                    }
                }
                MiuixDialogHost(controller)
            }
        }
    }
}

@Composable
private fun MiuixNavigationBar(current: AppTab, onSelect: (AppTab) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = current == AppTab.Home,
            onClick = { onSelect(AppTab.Home) },
            icon = rememberVectorIcon(R.drawable.ic_home),
            label = stringResource(R.string.nav_home),
        )
        NavigationBarItem(
            selected = current == AppTab.Settings,
            onClick = { onSelect(AppTab.Settings) },
            icon = rememberVectorIcon(R.drawable.ic_settings),
            label = stringResource(R.string.nav_settings),
        )
        NavigationBarItem(
            selected = current == AppTab.Info,
            onClick = { onSelect(AppTab.Info) },
            icon = rememberVectorIcon(R.drawable.ic_info),
            label = stringResource(R.string.nav_info),
        )
    }
}

/** 与 MD3 皮肤同样的过场语义：同级切页小位移，进出子页面从右侧推入。 */
private fun miuixPageTransition(from: Any, to: Any): ContentTransform {
    val fadeInSpec = tween<Float>(durationMillis = 260, easing = FastOutSlowInEasing)
    val fadeOutSpec = tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing)
    val slide = tween<IntOffset>(durationMillis = 320, easing = FastOutSlowInEasing)

    val (enterFraction, exitFraction) = when {
        to is AppSubPage -> 3 to -10
        from is AppSubPage -> -10 to 3
        from is AppTab && to is AppTab && to.ordinal > from.ordinal -> 6 to -6
        else -> -6 to 6
    }

    return ContentTransform(
        targetContentEnter = slideInHorizontally(slide) { it / enterFraction } + fadeIn(fadeInSpec),
        initialContentExit = slideOutHorizontally(slide) { it / exitFraction } + fadeOut(fadeOutSpec),
        sizeTransform = SizeTransform(clip = false),
    )
}
