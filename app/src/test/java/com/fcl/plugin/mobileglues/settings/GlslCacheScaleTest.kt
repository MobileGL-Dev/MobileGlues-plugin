package com.fcl.plugin.mobileglues.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 滑块刻度的换算。
 *
 * 界面依赖两条性质：量程永远盖得住当前配置值（否则 Slider 会抛异常），
 * 以及「档位 → MiB → 档位」不会改变 MiB（否则松手之后滑块会自己跳一下）。
 */
class GlslCacheScaleTest {

    private val gibibyte = 1024L * 1024 * 1024

    @Test
    fun `the base ceiling is a sixteenth of the memory`() {
        assertEquals(128, GlslCacheScale.baseCeiling(2 * gibibyte))
        assertEquals(768, GlslCacheScale.baseCeiling(12 * gibibyte))
    }

    @Test
    fun `tiny and huge devices are clamped`() {
        assertEquals(
            GlslCacheScale.MIN_UPPER_BOUND_MIB.toInt(),
            GlslCacheScale.baseCeiling(256L * 1024 * 1024),
        )
        assertEquals(
            GlslCacheScale.MAX_UPPER_BOUND_MIB.toInt(),
            GlslCacheScale.baseCeiling(1024 * gibibyte),
        )
    }

    @Test
    fun `an oversized configured value stretches the range instead of being clipped`() {
        // 手工编辑过的配置：4096 MiB 在一台 4 GiB 的设备上也必须选得中。
        val ceiling = GlslCacheScale.ceiling(totalRamBytes = 4 * gibibyte, currentMebibytes = 4096)
        assertEquals(4096, ceiling)
        assertEquals(4096, GlslCacheScale.mebibytesAt(GlslCacheScale.STEPS, ceiling))
    }

    @Test
    fun `position zero is off and the last position is the ceiling`() {
        val ceiling = GlslCacheScale.baseCeiling(8 * gibibyte)
        assertEquals(0, GlslCacheScale.mebibytesAt(0, ceiling))
        assertEquals(1, GlslCacheScale.mebibytesAt(1, ceiling))
        assertEquals(ceiling, GlslCacheScale.mebibytesAt(GlslCacheScale.STEPS, ceiling))
        assertEquals(0, GlslCacheScale.positionFor(0, ceiling))
        assertEquals(GlslCacheScale.STEPS, GlslCacheScale.positionFor(ceiling, ceiling))
    }

    @Test
    fun `the scale never runs backwards`() {
        val ceiling = GlslCacheScale.baseCeiling(6 * gibibyte)
        var previous = -1
        for (position in 0..GlslCacheScale.STEPS) {
            val mebibytes = GlslCacheScale.mebibytesAt(position, ceiling)
            assertTrue("档位 $position 比上一档小了", mebibytes >= previous)
            previous = mebibytes
        }
    }

    @Test
    fun `a value survives the round trip through a position`() {
        for (ceiling in listOf(64, 128, 512, 1024, 8192)) {
            for (position in 0..GlslCacheScale.STEPS) {
                val mebibytes = GlslCacheScale.mebibytesAt(position, ceiling)
                val roundTripped =
                    GlslCacheScale.mebibytesAt(GlslCacheScale.positionFor(mebibytes, ceiling), ceiling)
                assertEquals("上限 $ceiling 的第 $position 档", mebibytes, roundTripped)
            }
        }
    }
}
