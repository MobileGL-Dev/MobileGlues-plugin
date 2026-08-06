package com.fcl.plugin.mobileglues

import java.io.File

object MGInfoGetter {

    init {
        System.loadLibrary("mobileglues_info_getter")
    }

    external fun setenv(key: String, value: String, overwrite: Int): Int

    external fun getMobileGluesGLInfo(): String

    /**
     * 查询 MobileGlues 的 GL 信息。
     *
     * 这是一个阻塞调用：native 侧会 dlopen libmobileglues 并创建 EGL 上下文，必须放在后台线程。
     * [mgDirectory] 显式传入，而不是像以前那样从一个可变静态字段里读。
     */
    fun info(mgDirectory: File): String = try {
        // 这里不设 MG_COUNT_LAUNCH：我们自己把渲染器加载起来问一句话，不是一次「启动」。
        setenv("MG_PLUGIN_STATUS", "1", 1)
        setenv("MG_DIR_PATH", mgDirectory.path, 1)
        getMobileGluesGLInfo()
    } catch (e: Throwable) {
        "Error: ${e.message}"
    }
}
