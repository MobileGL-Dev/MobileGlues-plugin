package com.fcl.plugin.mobileglues.ui.material

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fcl.plugin.mobileglues.R
import com.fcl.plugin.mobileglues.ui.AppController
import com.fcl.plugin.mobileglues.ui.AppSubPage
import com.fcl.plugin.mobileglues.ui.AppTab

/**
 * MD3 皮肤的外壳：底部导航三页 + 子页面，全部对话框挂在这一层。
 *
 * 页面本身不持有任何状态——它们从 [AppController] 读，把操作回调回去，
 * 所以和 Miuix 皮肤看到的是同一份真相。
 */
@Composable
fun MaterialApp(controller: AppController) {
    MgMaterialTheme {
        val tab by controller.tab.collectAsStateWithLifecycle()
        val subPage by controller.subPage.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(controller) {
            controller.snackbar.collect { snackbarHostState.showSnackbar(it.toString()) }
        }

        // 子页面吃掉返回键；没有子页面时交还系统（退出应用）。
        BackHandler(enabled = subPage != null) { controller.navigateBack() }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    AnimatedVisibility(
                        visible = subPage == null,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        MaterialNavigationBar(current = tab, onSelect = controller::navigateTab)
                    }
                },
            ) { innerPadding ->
                // 每页的滚动位置各自存一份：从子页面退回来时，列表还停在原处。
                val pageState = rememberSaveableStateHolder()
                AnimatedContent(
                    targetState = subPage ?: tab,
                    transitionSpec = { pageTransition(initialState, targetState) },
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    label = "page",
                ) { destination ->
                    pageState.SaveableStateProvider(destination) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            when (destination) {
                                AppTab.Home -> MaterialHomePage(controller)
                                AppTab.Settings -> MaterialSettingsPage(controller)
                                AppTab.Info -> MaterialInfoPage(controller)
                                AppSubPage.GlInfo -> MaterialGlInfoPage(controller)
                                AppSubPage.Privacy -> MaterialPrivacyPage(controller)
                                AppSubPage.ThirdParty -> MaterialThirdPartyPage(controller)
                            }
                        }
                    }
                }
            }
        }

        MaterialDialogHost(controller)
    }
}

@Composable
private fun MaterialNavigationBar(current: AppTab, onSelect: (AppTab) -> Unit) {
    NavigationBar(modifier = Modifier.fillMaxWidth()) {
        NavigationDestinations.forEach { (destination, icon, label) ->
            NavigationBarItem(
                selected = current == destination,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(painter = painterResource(icon), contentDescription = stringResource(label))
                },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

private data class NavigationDestination(val tab: AppTab, val icon: Int, val label: Int)

private val NavigationDestinations = listOf(
    NavigationDestination(AppTab.Home, R.drawable.ic_home, R.string.nav_home),
    NavigationDestination(AppTab.Settings, R.drawable.ic_settings, R.string.nav_settings),
    NavigationDestination(AppTab.Info, R.drawable.ic_info, R.string.nav_info),
)

/**
 * 过场动画：同级切页是「淡入淡出 + 顺着方向的小位移」，进出子页面是「从右侧推入」。
 * 方向取自底部导航的顺序，所以位移方向和用户的手指方向一致。
 */
private fun pageTransition(from: Any, to: Any): ContentTransform {
    val fadeInSpec = tween<Float>(durationMillis = 260, easing = FastOutSlowInEasing)
    val fadeOutSpec = tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing)
    val slide = tween<IntOffset>(durationMillis = 320, easing = FastOutSlowInEasing)

    // 位移是屏宽的几分之一：进出子页面推得多一点，同级切页只要一点点。
    val (enterFraction, exitFraction) = when {
        to is AppSubPage -> 3 to -10
        from is AppSubPage -> -10 to 3
        from is AppTab && to is AppTab && to.ordinal > from.ordinal -> 6 to -6
        else -> -6 to 6
    }

    return ContentTransform(
        targetContentEnter = slideInHorizontally(slide) { it / enterFraction } + fadeIn(fadeInSpec),
        initialContentExit = slideOutHorizontally(slide) { it / exitFraction } + fadeOut(fadeOutSpec),
        // 两页高度不同时不要裁剪，否则退场那一页会被切掉一块。
        sizeTransform = SizeTransform(clip = false),
    )
}
