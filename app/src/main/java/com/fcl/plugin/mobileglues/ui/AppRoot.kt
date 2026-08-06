package com.fcl.plugin.mobileglues.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fcl.plugin.mobileglues.settings.UiStyle
import com.fcl.plugin.mobileglues.ui.material.MaterialApp
import com.fcl.plugin.mobileglues.ui.miuix.MiuixApp

/**
 * 根：按「界面风格」选择皮肤。两套皮肤是完整独立的两套界面（不是换肤），
 * 但它们吃进同一个 [AppController]，所以操作逻辑严格一致。
 */
@Composable
fun MobileGluesApp(controller: AppController) {
    val style by controller.pluginConfig.uiStyle.collectAsStateWithLifecycle()
    when (style) {
        UiStyle.Material -> MaterialApp(controller)
        UiStyle.Miuix -> MiuixApp(controller)
    }
}
