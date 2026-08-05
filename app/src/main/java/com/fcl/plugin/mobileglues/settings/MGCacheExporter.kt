package com.fcl.plugin.mobileglues.settings

import android.content.Context
import com.fcl.plugin.mobileglues.utils.Constants
import java.io.File

/**
 * 把当前配置导出到应用私有缓存目录，供 MGInfoGetter 通过 `MG_DIR_PATH` 读取。
 *
 * 取代了原先挂在 MGConfig 伴生对象上的两个可变静态字段（`cacheConfigPath` / `cacheMGDir`）：
 * 那两个字段默认是 `File("")`，能工作只是因为调用方恰好先调了一次导出。
 */
class MGCacheExporter(context: Context, private val store: MGConfigStore) {

    private val appContext = context.applicationContext

    val directory: File
        get() = File(appContext.externalCacheDir ?: appContext.cacheDir, DIRECTORY_NAME)

    suspend fun export(): Result<File> =
        store.exportTo(File(directory, Constants.CONFIG_FILE_NAME)).map { directory }

    private companion object {
        const val DIRECTORY_NAME = "MG"
    }
}
