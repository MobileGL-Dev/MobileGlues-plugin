package com.fcl.plugin.mobileglues

import android.app.Application
import com.fcl.plugin.mobileglues.settings.AuthController
import com.fcl.plugin.mobileglues.settings.MGCacheExporter
import com.fcl.plugin.mobileglues.settings.MGConfigStore
import com.fcl.plugin.mobileglues.settings.PluginConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 持有各仓库与授权控制器。
 *
 * 放在 Application 而不是 Activity 上，是因为去抖写盘的协程需要一个比界面更长的生命周期：
 * 用户改完设置立刻按 Home 键时，落盘不应该被 Activity 的销毁打断。
 * 启动次数也记在这里：旋转屏幕会重建 Activity，但不算一次启动。
 */
class MGApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val pluginConfigStore: PluginConfigStore by lazy { PluginConfigStore(this) }

    val authController: AuthController by lazy { AuthController(this, pluginConfigStore) }

    val configStore: MGConfigStore by lazy {
        MGConfigStore(
            storage = null,
            scope = scope,
        )
    }

    val cacheExporter: MGCacheExporter by lazy { MGCacheExporter(this, configStore) }

    /** 本次冷启动是第几次启动。赞助弹窗按它取模，只在这里 +1。 */
    var launchCount: Int = 0
        private set

    /** 赞助弹窗每个进程最多弹一次——旋转屏幕重建 Activity 不该再弹。 */
    var sponsorPromptedThisProcess: Boolean = false

    override fun onCreate() {
        super.onCreate()
        launchCount = pluginConfigStore.incrementLaunchCount()
        authController.refresh()
    }
}
