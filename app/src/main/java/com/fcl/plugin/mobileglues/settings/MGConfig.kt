package com.capslock800000.optimizedmg.settings

import android.content.Context
import com.capslock800000.optimizedmg.utils.Constants
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * OptimizedMG 配置类。
 */
class MGConfig private constructor(val context: Context, private var isInitializing: Boolean) {

    // 默认构造函数，供正常实例化使用
    constructor(context: Context) : this(context, false)

    // ---- 配置字段（UI 触发变更后自动保存） ----

    var enableANGLE: Int = 1
        set(value) {
            if (field != value) {
                field = value; saveIfReady()
            }
        }

    var enableNoError: Int = 0
        set(value) {
            if (field != value) {
                field = value; saveIfReady()
            }
        }

    var enableExtTimerQuery: Int = 1
        set(value) {
            if (field != value) {
                field = value; saveIfReady()
            }
        }

    var enableExtComputeShader: Int = 0
        set(value) {
            if (field != value) {
                field = value; saveIfReady()
            }
        }

    var enableExtDirectStateAccess: Int = 0
        set(value) {
            if (field != value) {
                field = value; saveIfReady()
            }
        }

    var maxGlslCacheSize: Int = 32
        set(value) {
            if (field != value) {
                field = value
                if (value == -1) clearCacheFile()
                saveIfReady()
            }
        }

    var multidrawMode: Int = 0
        set(value) {
            if (field != value) {
                field = value; saveIfReady()
            }
        }

    var angleDepthClearFixMode: Int = 0
        set(value) {
            if (field != value) {
                field = value; saveIfReady()
            }
        }

    var customGLVersion: Int = 0
        set(value) {
            if (field != value) {
                field = value; saveIfReady()
            }
        }

    var fsr1Setting: Int = 0
        set(value) {
            if (field != value) {
                field = value; saveIfReady()
            }
        }

    // ---- 对外操作 ----

    private fun saveIfReady() {
        if (!isInitializing) save()
    }

    fun save() {
        runCatching {
            val configFile = File(Constants.CONFIG_FILE_PATH)
            configFile.parentFile?.mkdirs()
            configFile.writeText(Gson().toJson(buildConfigMap()))
        }
    }

    fun saveToCachePath() {
        if (cacheConfigPath == null) {
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            cacheMGDir = File(cacheDir, "MG").apply { mkdirs() }
            cacheConfigPath = File(cacheMGDir, "config.json").absolutePath
        }
        runCatching {
            File(cacheConfigPath!!).writeText(Gson().toJson(buildConfigMap()))
        }
    }

    // ---- 私有辅助 ----

    private fun clearCacheFile() {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { File(Constants.GLSL_CACHE_FILE_PATH).delete() }
        }
    }

    private fun buildConfigMap(): Map<String, Int> = mapOf(
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

    companion object {
        var cacheConfigPath: String? = null
        var cacheMGDir: File = File("")

        /**
         * 从磁盘加载配置，文件不存在或解析失败时返回 null。
         */
        fun loadConfig(context: Context): MGConfig? {
            val configFile = File(Constants.CONFIG_FILE_PATH)
            if (!configFile.exists()) return null

            val configStr = runCatching { configFile.readText() }.getOrNull() ?: return null

            return runCatching {
                val obj: JsonObject = JsonParser.parseString(configStr).asJsonObject
                // 开启 isInitializing 拦截，防止在读取 JSON 赋值时触发大量冗余的 save() 磁盘 I/O
                val config = MGConfig(context, isInitializing = true)
                config.applyFromJson(obj)
                config.isInitializing = false
                config
            }.getOrNull()
        }

        private fun MGConfig.applyFromJson(obj: JsonObject) {
            fun JsonObject.int(key: String, default: Int) = get(key)?.asInt ?: default

            enableANGLE = obj.int("enableANGLE", 1)
            enableNoError = obj.int("enableNoError", 0)
            enableExtTimerQuery = obj.int("enableExtTimerQuery", 1)
            enableExtComputeShader = obj.int("enableExtComputeShader", 0)
            enableExtDirectStateAccess = obj.int("enableExtDirectStateAccess", 0)
            maxGlslCacheSize = obj.int("maxGlslCacheSize", 32)
            multidrawMode = obj.int("multidrawMode", 0)
            angleDepthClearFixMode = obj.int("angleDepthClearFixMode", 0)
            customGLVersion = obj.int("customGLVersion", 0)
            fsr1Setting = obj.int("fsr1Setting", 0)
        }
    }
}