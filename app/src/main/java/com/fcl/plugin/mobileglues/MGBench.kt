package com.fcl.plugin.mobileglues

import java.io.File

object MGBench {

    init {
        System.loadLibrary("mobileglues_info_getter")
    }

    private external fun runMultidrawBench(): String

    private external fun benchProgress(): Int

    /**
     * 在本进程内跑 MultiDraw 微基准。
     *
     * 与 [MGInfoGetter.info] 同一条通道：dlopen libmobileglues，经渲染器自己的 EGL 层
     * 建上下文，调用 `mg_multidraw_bench_run`，拿回一段 JSON。阻塞且耗时（默认预算 8 秒，
     * 见 native 侧的 MG_BENCH_BUDGET_MS），必须放在后台线程。
     */
    fun run(mgDirectory: File): String = try {
        // 跑分不是一次「启动」，不设 MG_COUNT_LAUNCH。
        MGInfoGetter.setenv("MG_PLUGIN_STATUS", "1", 1)
        MGInfoGetter.setenv("MG_DIR_PATH", mgDirectory.path, 1)
        runMultidrawBench()
    } catch (e: Throwable) {
        """{"error":"${e.message ?: e.javaClass.simpleName}"}"""
    }

    /**
     * 正在跑的那一轮的进度，0f..1f；没有在跑、或渲染器版本太老没有这个计数器时为 null。
     *
     * 由别的线程调用（[run] 正阻塞着它自己那条）。
     */
    fun progress(): Float? = try {
        benchProgress().takeIf { it >= 0 }?.let { (it / 1000f).coerceIn(0f, 1f) }
    } catch (_: Throwable) {
        null
    }
}
