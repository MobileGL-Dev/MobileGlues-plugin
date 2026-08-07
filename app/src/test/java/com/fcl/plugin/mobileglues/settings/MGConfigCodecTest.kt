package com.fcl.plugin.mobileglues.settings

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `MG/config.json` 是和 native 端共享的格式，这些用例锁住的是那份契约。
 */
class MGConfigCodecTest {

    private fun parse(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    private fun encode(config: MGConfig, foreign: JsonObject? = null): JsonObject =
        MGConfigCodec.encode(config, foreign)

    @Test
    fun `wire values match the native settings header`() {
        assertEquals(0, AngleConfig.DisableIfPossible.wire)
        assertEquals(1, AngleConfig.EnableIfPossible.wire)
        assertEquals(2, AngleConfig.ForceDisable.wire)
        assertEquals(3, AngleConfig.ForceEnable.wire)

        // native 还有 Mode2 = 2，但那一档不对外开放，App 不提供也不接受。
        assertEquals(1, DepthClearFixMode.entries.last().wire)
        assertEquals(4, Fsr1Preset.Performance.wire)
        assertEquals(46, GlVersion.Gl46.wire)
        assertEquals(0, GlVersion.Default.wire)
    }

    @Test
    fun `spinner position equals ordinal for every option`() {
        // 适配器是按 entries 的顺序构建的，render() 依赖 ordinal 就是位置。
        listOf(
            AngleConfig.entries,
            NoErrorConfig.entries,
            DepthClearFixMode.entries,
            GlVersion.entries,
        ).forEach { entries ->
            entries.forEachIndexed { index, option -> assertEquals(index, (option as Enum<*>).ordinal) }
        }
    }

    @Test
    fun `an empty object decodes to the defaults`() {
        assertEquals(MGConfig.Default, MGConfigCodec.decode(parse("{}")))
    }

    @Test
    fun `every field survives a round trip`() {
        val config = MGConfig(
            angle = AngleConfig.ForceDisable,
            noError = NoErrorConfig.IgnoreShaderProgramFramebuffer,
            multidraw = MultidrawSettings(
                globalOrder = MultidrawOrderItem.normalize(
                    listOf(MultidrawOrderItem.Compute, MultidrawOrderItem.Unroll),
                ),
                exceptions = mapOf(
                    MultidrawEntry.Elements to MultidrawEntry.Elements.normalize(
                        listOf(MultidrawBackend.Indirect),
                    ),
                ),
            ),
            depthClearFix = DepthClearFixMode.Mode1,
            glVersion = GlVersion.Gl33,
            glslCache = GlslCacheSize.Disabled,
            extComputeShader = true,
            extTimerQuery = false,
            extDirectStateAccess = true,
            fsr1 = Fsr1Preset.Balanced,
        )

        val json = Gson().toJson(encode(config))
        assertEquals(config, MGConfigCodec.decode(parse(json)))
    }

    @Test
    fun `keys the app does not know survive a save`() {
        // native 会读 hideMGEnvLevel，App 不认识它——保存时必须原样写回，否则等于替用户删设置。
        val root = parse("""{"enableANGLE":3,"hideMGEnvLevel":1,"somethingFromTheFuture":7}""")

        val config = MGConfigCodec.decode(root)
        val encoded = encode(config, MGConfigCodec.foreignKeysOf(root))

        assertEquals(1, encoded.get("hideMGEnvLevel").asInt)
        assertEquals(7, encoded.get("somethingFromTheFuture").asInt)
        assertEquals(3, encoded.get("enableANGLE").asInt)
    }

    @Test
    fun `foreign keys never include keys the app owns`() {
        val root = parse("""{"enableANGLE":3,"maxGlslCacheSize":64,"hideMGEnvLevel":1}""")
        val foreign = MGConfigCodec.foreignKeysOf(root)

        assertNull(foreign.get("enableANGLE"))
        assertNull(foreign.get("maxGlslCacheSize"))
        assertEquals(1, foreign.get("hideMGEnvLevel").asInt)
    }

    @Test
    fun `out of range values fall back to the defaults`() {
        val decoded = MGConfigCodec.decode(
            parse("""{"enableANGLE":99,"enableNoError":-4,"fsr1Setting":9}""")
        )

        assertEquals(MGConfig.Default.angle, decoded.angle)
        assertEquals(MGConfig.Default.noError, decoded.noError)
        assertEquals(MGConfig.Default.fsr1, decoded.fsr1)
    }

    @Test
    fun `an unlisted customGLVersion is clamped exactly like native does`() {
        // settings.cpp: >46 -> 46, 34..39 -> 33, 1..31 -> 32, 0 -> DEFAULT_GL_VERSION.
        // 若这些值被读成 Default(0)，下一次保存就会把它们写成 0，native 随即当成 4.0，
        // 用户没动过任何开关，目标版本却变了。
        assertEquals(GlVersion.Gl46, GlVersion.fromWire(47))
        assertEquals(GlVersion.Gl46, GlVersion.fromWire(99))
        assertEquals(GlVersion.Gl33, GlVersion.fromWire(38))
        assertEquals(GlVersion.Gl33, GlVersion.fromWire(34))
        assertEquals(GlVersion.Gl33, GlVersion.fromWire(39))
        assertEquals(GlVersion.Gl32, GlVersion.fromWire(31))
        assertEquals(GlVersion.Gl32, GlVersion.fromWire(1))
        assertEquals(GlVersion.Default, GlVersion.fromWire(0))
        assertEquals(GlVersion.Default, GlVersion.fromWire(-3))
        assertEquals(GlVersion.Default, GlVersion.fromWire(null))

        // 已知档位必须原样通过。
        GlVersion.entries.forEach { assertEquals(it, GlVersion.fromWire(it.wire)) }

        // 读进来再写回去，落到磁盘上的必须是 native 会夹到的那一档，不能是 0。
        assertEquals(33, encode(MGConfigCodec.decode(parse("""{"customGLVersion":38}""")))
            .get("customGLVersion").asInt)
    }

    @Test
    fun `one broken field does not discard the rest of the config`() {
        // 以前 applyFromJson 里任何一个 asInt 抛异常都会让整份配置作废并被默认值覆盖。
        val decoded = MGConfigCodec.decode(
            parse("""{"enableANGLE":{"nope":true},"multidrawOrderElements":"indirect","maxGlslCacheSize":128}""")
        )

        assertEquals(MGConfig.Default.angle, decoded.angle)
        assertEquals(
            MultidrawBackend.Indirect,
            decoded.multidraw.effectiveOrderFor(MultidrawEntry.Elements).first(),
        )
        assertEquals(GlslCacheSize.Limited(128), decoded.glslCache)
    }

    @Test
    fun `numbers written as strings are accepted and normalised`() {
        val decoded = MGConfigCodec.decode(parse("""{"maxGlslCacheSize":"128"}"""))
        assertEquals(GlslCacheSize.Limited(128), decoded.glslCache)
        assertTrue(encode(decoded).get("maxGlslCacheSize").asJsonPrimitive.isNumber)
    }

    @Test
    fun `booleans follow the native greater-than-zero rule`() {
        val decoded = MGConfigCodec.decode(
            parse("""{"enableExtComputeShader":2,"enableExtTimerQuery":0,"enableExtDirectStateAccess":-1}""")
        )

        assertTrue(decoded.extComputeShader)
        assertEquals(false, decoded.extTimerQuery)
        assertEquals(false, decoded.extDirectStateAccess)

        val encoded = encode(decoded)
        assertEquals(1, encoded.get("enableExtComputeShader").asInt)
        assertEquals(0, encoded.get("enableExtTimerQuery").asInt)
    }

    @Test
    fun `every non-positive cache size means disabled, exactly like native reads it`() {
        // native: `if (config_get_int("maxGlslCacheSize") > 0)` —— 否则缓存大小为 0，即不缓存。
        assertEquals(GlslCacheSize.Disabled, GlslCacheSize.fromWire(-1))
        assertEquals(GlslCacheSize.Disabled, GlslCacheSize.fromWire(0))
        assertEquals(GlslCacheSize.Disabled, GlslCacheSize.fromWire(-7))
        assertEquals(GlslCacheSize.Limited(64), GlslCacheSize.fromWire(64))
        assertEquals(GlslCacheSize.Default, GlslCacheSize.fromWire(null))

        // 关闭状态写回磁盘统一用 -1，与历史配置一致。
        assertEquals(-1, GlslCacheSize.Disabled.wire)
        assertEquals(-1, encode(MGConfigCodec.decode(parse("""{"maxGlslCacheSize":0}""")))
            .get("maxGlslCacheSize").asInt)

        assertThrows(IllegalArgumentException::class.java) { GlslCacheSize.Limited(0) }
    }

    @Test
    fun `slider mebibytes map to config values and back`() {
        assertEquals(GlslCacheSize.Disabled, GlslCacheSize.ofMebibytes(0))
        assertEquals(GlslCacheSize.Limited(1), GlslCacheSize.ofMebibytes(1))
        assertEquals(GlslCacheSize.Limited(512), GlslCacheSize.ofMebibytes(512))

        assertEquals(0, GlslCacheSize.Disabled.mebibytesOrZero)
        assertEquals(512, GlslCacheSize.Limited(512).mebibytesOrZero)
    }

    @Test
    fun `the defaults are the same ones the previous implementation wrote`() {
        val encoded = encode(MGConfig.Default)

        assertEquals(1, encoded.get("enableANGLE").asInt)
        assertEquals(0, encoded.get("enableNoError").asInt)
        assertEquals(1, encoded.get("enableExtTimerQuery").asInt)
        assertEquals(0, encoded.get("enableExtComputeShader").asInt)
        assertEquals(0, encoded.get("enableExtDirectStateAccess").asInt)
        assertEquals(32, encoded.get("maxGlslCacheSize").asInt)
        // MultiDraw 默认顺序、无例外 = 一个键都不写。
        assertNull(encoded.get("multidrawOrder"))
        MultidrawEntry.entries.forEach { assertNull(encoded.get(it.orderKey)) }
        assertNull(encoded.get("multidrawMode"))
        assertNull(encoded.get("multidrawDisableBackends"))
        assertEquals(0, encoded.get("angleDepthClearFixMode").asInt)
        assertEquals(0, encoded.get("customGLVersion").asInt)
        assertEquals(0, encoded.get("fsr1Setting").asInt)
    }

    @Test
    fun `the global order round-trips as a comma separated name list`() {
        val decoded = MGConfigCodec.decode(
            parse("""{"multidrawOrder":"compute, unroll ; nonsense, native, compute"}""")
        )

        // 未知名丢弃、重复项保留首个、漏项按默认顺序补齐——全 8 项的置换。
        val order = decoded.multidraw.globalOrder
        assertEquals(MultidrawOrderItem.entries.size, order.size)
        assertEquals(MultidrawOrderItem.Compute, order[0])
        assertEquals(MultidrawOrderItem.Unroll, order[1])
        assertEquals(MultidrawOrderItem.Native, order[2])
        assertEquals(MultidrawOrderItem.entries.toSet(), order.toSet())

        assertEquals(
            "compute,unroll,native,multiindirect,multibasevertex,multiarrays,indirect,basevertex",
            encode(decoded).get("multidrawOrder").asString,
        )
    }

    @Test
    fun `an exception key means an independent order for that function`() {
        val decoded = MGConfigCodec.decode(
            // basevertex 对 glMultiDrawElements 不是独立实现，native 也会拒绝它。
            parse("""{"multidrawOrderElements":"indirect, basevertex, native"}""")
        )

        assertTrue(decoded.multidraw.hasException(MultidrawEntry.Elements))
        assertEquals(false, decoded.multidraw.hasException(MultidrawEntry.Arrays))

        val order = decoded.multidraw.effectiveOrderFor(MultidrawEntry.Elements)
        assertEquals(MultidrawEntry.Elements.implemented.toSet(), order.toSet())
        assertEquals(MultidrawBackend.Indirect, order[0])
        assertTrue(MultidrawBackend.BaseVertex !in order)

        assertEquals(
            "indirect,multiarrays,multiindirect,multibasevertex,unroll",
            encode(decoded).get("multidrawOrderElements").asString,
        )
    }

    @Test
    fun `the native item expands per function and deduplicates`() {
        // native 在 glMultiDrawArrays 上落到 multiarrays；后面的 multiarrays 重复项被吃掉。
        val settings = MultidrawSettings()
        assertEquals(
            listOf(MultidrawBackend.MultiArrays, MultidrawBackend.MultiIndirect, MultidrawBackend.Unroll),
            settings.globalOrderFor(MultidrawEntry.Arrays),
        )
        assertEquals(
            listOf(MultidrawBackend.MultiIndirect, MultidrawBackend.Indirect),
            settings.globalOrderFor(MultidrawEntry.ArraysIndirect),
        )
        // ElementsBaseVertex 的 native 是 multibasevertex，排最前。
        assertEquals(
            MultidrawBackend.MultiBaseVertex,
            settings.globalOrderFor(MultidrawEntry.ElementsBaseVertex).first(),
        )
    }

    @Test
    fun `order names are parsed the way native parses them`() {
        // md_parse_order_list：忽略大小写以及空格 / 下划线 / 连字符
        assertEquals(MultidrawBackend.MultiIndirect, MultidrawBackend.parse("MultiIndirect"))
        assertEquals(MultidrawBackend.MultiIndirect, MultidrawBackend.parse("  multi_indirect "))
        assertEquals(MultidrawBackend.MultiArrays, MultidrawBackend.parse("multi-arrays"))
        assertNull(MultidrawBackend.parse("nonsense"))
        assertNull(MultidrawBackend.parse(""))
        assertNull(MultidrawBackend.parse(null))
        assertEquals(MultidrawOrderItem.Native, MultidrawOrderItem.parse(" Native "))
    }

    @Test
    fun `keys are exactly the names native accepts`() {
        // 这些名字是和 native 的 k_md_backend_names / k_md_entries 共享的契约。
        assertEquals(
            listOf(
                "unroll", "basevertex", "indirect",
                "multiarrays", "multibasevertex", "multiindirect", "compute",
            ),
            MultidrawBackend.entries.map { it.key },
        )
        assertEquals(
            listOf(
                "native", "multiindirect", "multibasevertex", "multiarrays",
                "indirect", "basevertex", "unroll", "compute",
            ),
            MultidrawOrderItem.entries.map { it.key },
        )
        assertEquals(
            listOf(
                "multidrawOrderArrays",
                "multidrawOrderElements",
                "multidrawOrderElementsBaseVertex",
                "multidrawOrderArraysIndirect",
                "multidrawOrderElementsIndirect",
            ),
            MultidrawEntry.entries.map { it.orderKey },
        )
    }

    @Test
    fun `all three generations of legacy multidraw keys are dropped on save`() {
        // native 已经不读它们，留着只会让它每次启动都打弃用警告。
        val root = parse(
            """{"multidrawMode":5,"multidrawModeElements":"multibasevertex",""" +
                """"multidrawDisableBackends":"compute","hideMGEnvLevel":1}"""
        )
        val encoded = encode(MGConfigCodec.decode(root), MGConfigCodec.foreignKeysOf(root))

        assertNull(encoded.get("multidrawMode"))
        assertNull(encoded.get("multidrawModeElements"))
        assertNull(encoded.get("multidrawDisableBackends"))
        assertEquals(1, encoded.get("hideMGEnvLevel").asInt)
    }
}
