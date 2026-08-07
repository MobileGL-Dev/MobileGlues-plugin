package com.fcl.plugin.mobileglues.settings

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * MultiDraw 微基准的结果：每个入口点 → 每个后端 → 每次调用多少微秒（多轮的中位数）。
 *
 * JSON 由 native 端 `gl/multidraw_bench.cpp` 生成。没测出来的后端（设备不支持、
 * 或测量中发生了降级）直接缺席——宁缺毋滥，一个错的数字比没有数字糟得多。
 *
 * [noise] 是同一个后端在各轮之间的离散度（MAD/中位数），用来判断两个数字之间的差距
 * 是真的还是噪声。老版本渲染器不报这个，缺席即 null。
 */
data class MultidrawBenchReport(
    val timings: Map<MultidrawEntry, Map<MultidrawBackend, Double>>,
    val noise: Map<MultidrawEntry, Map<MultidrawBackend, Double>> = emptyMap(),
    val rounds: Int = 0,
    val elapsedMs: Double = 0.0,
    /** 一共测了几遍。抖得太厉害时 native 会加长每批重测，最多四遍。 */
    val attempts: Int = 1,
    /** 四遍里最稳的一遍仍然没压到目标，这份排名只能算参考。 */
    val noisy: Boolean = false,
    val error: String? = null,
) {
    /** 这次跑分里最大的一项离散度，UI 用它决定要不要提醒结果不够稳。 */
    val worstNoise: Double? get() = noise.values.flatMap { it.values }.maxOrNull()

    companion object {

        fun parse(text: String?): MultidrawBenchReport {
            val root = runCatching {
                JsonParser.parseString(text.orEmpty()).asJsonObject
            }.getOrNull() ?: return MultidrawBenchReport(emptyMap(), error = "unparseable result")

            root.get("error")?.takeIf { it.isJsonPrimitive }?.let {
                return MultidrawBenchReport(emptyMap(), error = it.asString)
            }

            val entriesObj = root.get("entries")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return MultidrawBenchReport(emptyMap(), error = "no entries in result")
            val statsObj = root.get("stats")?.takeIf { it.isJsonObject }?.asJsonObject

            val timings = MultidrawEntry.entries.mapNotNull { entry ->
                val obj = entriesObj.get(entry.glFunction)
                    ?.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val perBackend = obj.entrySet().mapNotNull { (name, value) ->
                    val backend = MultidrawBackend.parse(name) ?: return@mapNotNull null
                    val us = runCatching { value.asDouble }.getOrNull() ?: return@mapNotNull null
                    backend to us
                }.toMap()
                if (perBackend.isEmpty()) null else entry to perBackend
            }.toMap()

            val noise = MultidrawEntry.entries.mapNotNull { entry ->
                val obj = statsObj?.get(entry.glFunction)
                    ?.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val perBackend = obj.entrySet().mapNotNull { (name, value) ->
                    val backend = MultidrawBackend.parse(name) ?: return@mapNotNull null
                    val rsd = value.takeIf { it.isJsonObject }?.asJsonObject
                        ?.get("rsd")?.let { runCatching { it.asDouble }.getOrNull() }
                        ?: return@mapNotNull null
                    backend to rsd
                }.toMap()
                if (perBackend.isEmpty()) null else entry to perBackend
            }.toMap()

            return if (timings.isEmpty()) {
                MultidrawBenchReport(emptyMap(), error = "empty result")
            } else {
                MultidrawBenchReport(
                    timings = timings,
                    noise = noise,
                    rounds = root.get("rounds")?.let { runCatching { it.asInt }.getOrNull() } ?: 0,
                    elapsedMs = root.get("elapsedMs")
                        ?.let { runCatching { it.asDouble }.getOrNull() } ?: 0.0,
                    attempts = root.get("attempts")
                        ?.let { runCatching { it.asInt }.getOrNull() } ?: 1,
                    noisy = root.get("noisy")
                        ?.let { runCatching { it.asBoolean }.getOrNull() } ?: false,
                )
            }
        }
    }
}

/** 排好序的一项推荐：条目 + 相对耗时（最快 = 1.0；没测出来为 null，排在末尾）。 */
data class RankedItem<T>(val item: T, val relativeCost: Double?)

/**
 * 从跑分结果算推荐排序。
 *
 * 全局排序的比较基准是**相对**耗时：每个函数内先除以该函数的最快值，再对函数取平均。
 * 不同函数的绝对耗时差着数量级（unroll 一次 96 个驱动调用，multiindirect 一次一个），
 * 直接平均绝对值会让最重的函数独裁整张榜单。
 */
object MultidrawBenchAnalyzer {

    fun rankGlobal(report: MultidrawBenchReport): List<RankedItem<MultidrawOrderItem>> {
        // item -> 每个函数上的相对耗时
        val scores = mutableMapOf<MultidrawOrderItem, MutableList<Double>>()
        for ((entry, perBackend) in report.timings) {
            val fastest = perBackend.values.min()
            for (item in MultidrawOrderItem.entries) {
                val backend = item.backend ?: entry.nativeBackend
                val us = perBackend[backend] ?: continue
                scores.getOrPut(item) { mutableListOf() }.add(us / fastest)
            }
        }

        val measured = scores.mapValues { (_, list) -> list.average() }
        val ranked = measured.entries.sortedBy { it.value }
            .map { RankedItem(it.key, it.value) }
        val unmeasured = MultidrawOrderItem.entries
            .filter { it !in measured }
            .map { RankedItem(it, null) }
        return ranked + unmeasured
    }

    fun rankEntry(
        report: MultidrawBenchReport,
        entry: MultidrawEntry,
    ): List<RankedItem<MultidrawBackend>> {
        val perBackend = report.timings[entry] ?: emptyMap()
        val fastest = perBackend.values.minOrNull()
        val ranked = perBackend.entries.sortedBy { it.value }
            .map { RankedItem(it.key, it.value / fastest!!) }
        val unmeasured = entry.implemented
            .filter { it !in perBackend }
            .map { RankedItem(it, null) }
        return ranked + unmeasured
    }
}
