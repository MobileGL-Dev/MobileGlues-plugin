package com.fcl.plugin.mobileglues.settings

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.fcl.plugin.mobileglues.MainActivity
import com.fcl.plugin.mobileglues.utils.Constants
import com.fcl.plugin.mobileglues.utils.FileUtils
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.properties.Delegates

data class MGConfig(val context: Context) {
    // 使用 Delegates.observable 委托属性
    var enableANGLE: Int by Delegates.observable(1) { _, old, new -> if (old != new) save() }
    var enableNoError: Int by Delegates.observable(0) { _, old, new -> if (old != new) save() }
    var enableExtTimerQuery: Int by Delegates.observable(1) { _, old, new -> if (old != new) save() }
    var enableExtComputeShader: Int by Delegates.observable(0) { _, old, new -> if (old != new) save() }
    var enableExtDirectStateAccess: Int by Delegates.observable(0) { _, old, new -> if (old != new) save() }
    var maxGlslCacheSize: Int by Delegates.observable(32) { _, old, new ->
        if (old != new) {
            if (new == -1) clearCacheFile()
            save()
        }
    }
    var multidrawMode: Int by Delegates.observable(0) { _, old, new -> if (old != new) save() }
    var angleDepthClearFixMode: Int by Delegates.observable(0) { _, old, new -> if (old != new) save() }
    var customGLVersion: Int by Delegates.observable(0) { _, old, new -> if (old != new) save() }
    var fsr1Setting: Int by Delegates.observable(0) { _, old, new -> if (old != new) save() }

    companion object {
        public var cacheConfigPath: String? = null
        public var cacheMGDir: File = File("")

        fun loadConfig(context: Context): MGConfig? {
            val configStr: String = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MainActivity.MGDirectoryUri?.let { uri ->
                        val configUri = DocumentsContract.buildDocumentUriUsingTree(
                            uri,
                            DocumentsContract.getTreeDocumentId(uri) + "/config.json"
                        )
                        FileUtils.readText(context, configUri)
                    } ?: return null
                } else {
                    val configFile = File(Constants.CONFIG_FILE_PATH)
                    if (!Files.exists(configFile.toPath())) return null
                    FileUtils.readText(configFile)
                }
            } catch (_: Exception) {
                return null
            }

            val config = MGConfig(context) // 使用修改后的构造函数
            try {
                // 将从文件读取的配置赋值给新创建的 config 对象
                Gson().fromJson(configStr, JsonObject::class.java).apply {
                    config.enableANGLE = this.get("enableANGLE")?.asInt ?: 1
                    config.enableNoError = this.get("enableNoError")?.asInt ?: 0
                    config.enableExtTimerQuery = this.get("enableExtTimerQuery")?.asInt ?: 1
                    config.enableExtComputeShader = this.get("enableExtComputeShader")?.asInt ?: 0
                    config.enableExtDirectStateAccess =
                        this.get("enableExtDirectStateAccess")?.asInt ?: 1
                    config.maxGlslCacheSize = this.get("maxGlslCacheSize")?.asInt ?: 32
                    config.multidrawMode = this.get("multidrawMode")?.asInt ?: 0
                    config.angleDepthClearFixMode = this.get("angleDepthClearFixMode")?.asInt ?: 0
                    config.customGLVersion = this.get("customGLVersion")?.asInt ?: 0
                    config.fsr1Setting = this.get("fsr1Setting")?.asInt ?: 0
                }

                // 处理历史遗留问题
                val obj = JsonParser.parseString(configStr).asJsonObject
                if (!obj.has("enableExtTimerQuery")) config.enableExtTimerQuery = 1

                if (!obj.has("enableExtDirectStateAccess")) config.enableExtDirectStateAccess = 0
            } catch (_: Exception) {
            }

            return config
        }
    }

    // 省略 saveConfig 和 save 方法，因为 observable 委托会直接调用 save(context)
    // 另外，Data Class 的特性不再适用，因为属性委托会改变属性的 getter/setter
    // 所以我将 data class 关键字移除，并修改了构造函数来接收 Context

    fun deleteConfig() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MainActivity.MGDirectoryUri?.let {
                    FileUtils.deleteFileViaSAF(context, it, "config.json")
                }
            } else {
                val configFile = File(Environment.getExternalStorageDirectory(), "MG/config.json")
                if (configFile.exists()) FileUtils.deleteFile(configFile)
            }
        } catch (_: Exception) {
        }
    }

    private fun clearCacheFile() {
        (context as LifecycleOwner).lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(
                        MainActivity.MGDirectoryUri,
                        DocumentsContract.getTreeDocumentId(MainActivity.MGDirectoryUri) + "/glsl_cache.tmp"
                    )
                    context.contentResolver?.let {
                        DocumentsContract.deleteDocument(it, uri)
                    }
                } else {
                    FileUtils.deleteFile(File(Constants.GLSL_CACHE_FILE_PATH))
                }
            } catch (_: Exception) {
            }
        }
    }

    @Throws(IOException::class)
    fun save() {
        val configMap = buildConfigMap()

        val configStr = Gson().toJson(configMap)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = MainActivity.MGDirectoryUri
                ?: throw IOException("SAF directory not selected")
            FileUtils.writeText(context, uri, "config.json", configStr, "application/json")
        } else {
            FileUtils.writeText(File(Constants.CONFIG_FILE_PATH), configStr)
        }
    }

    fun saveToCachePath() {
        if (cacheConfigPath == null) {
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            cacheMGDir = File(cacheDir, "MG")
            if (!cacheMGDir.exists()) {
                cacheMGDir.mkdirs()
            }
            cacheConfigPath = File(cacheMGDir, "config.json").absolutePath
        }

        val configMap = buildConfigMap()
        FileUtils.writeText(File(cacheConfigPath!!), Gson().toJson(configMap))
    }

    private fun buildConfigMap(): Map<String, Int> {
        return mapOf(
            "enableANGLE" to enableANGLE,
            "enableNoError" to enableNoError,
            "enableExtTimerQuery" to enableExtTimerQuery,
            "enableExtComputeShader" to enableExtComputeShader,
            "enableExtDirectStateAccess" to enableExtDirectStateAccess,
            "maxGlslCacheSize" to maxGlslCacheSize,
            "multidrawMode" to multidrawMode,
            "angleDepthClearFixMode" to angleDepthClearFixMode,
            "customGLVersion" to customGLVersion,
            "fsr1Setting" to fsr1Setting
        )
    }
}