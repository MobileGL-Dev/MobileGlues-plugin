package com.fcl.plugin.mobileglues

import android.app.Application
import com.fcl.plugin.mobileglues.settings.MGCacheExporter
import com.fcl.plugin.mobileglues.settings.MGConfigStore
import com.fcl.plugin.mobileglues.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * 持有配置仓库。
 *
 * 放在 Application 而不是 Activity 上，是因为去抖写盘的协程需要一个比界面更长的生命周期：
 * 用户改完设置立刻按 Home 键时，落盘不应该被 Activity 的销毁打断。
 */
class MGApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val configStore: MGConfigStore by lazy {
        MGConfigStore(
            configFile = File(Constants.CONFIG_FILE_PATH),
            glslCacheFile = File(Constants.GLSL_CACHE_FILE_PATH),
            scope = scope,
        )
    }

    val cacheExporter: MGCacheExporter by lazy { MGCacheExporter(this, configStore) }
}
