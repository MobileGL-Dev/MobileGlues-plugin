package com.fcl.plugin.mobileglues.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 赞助弹窗的判定。
 *
 * 启动次数由 native 库记在 MG/stats.json 里，本 App 只记「上次问是在第几次」——它不掌控
 * 那个计数，也不知道两次打开设置页之间用户开了几局游戏，所以判据是差值而不是取模：
 * 取模会在计数一次跳过那个点时永远不弹。
 */
class SponsorPromptTest {

    @Test
    fun `prompts once the count has moved a full interval`() {
        assertTrue(SponsorPrompt.shouldPrompt(SponsorPrompt.INTERVAL, 0, donated = false))
        assertTrue(SponsorPrompt.shouldPrompt(137, 117, donated = false))
    }

    @Test
    fun `stays quiet until then`() {
        assertFalse(SponsorPrompt.shouldPrompt(SponsorPrompt.INTERVAL - 1, 0, donated = false))
        assertFalse(SponsorPrompt.shouldPrompt(136, 117, donated = false))
    }

    @Test
    fun `a count that jumps past the mark still prompts`() {
        // 用户连开了几局游戏才回到设置页：计数从 19 跳到 44，取模会正好跨过 20 和 40。
        assertTrue(SponsorPrompt.shouldPrompt(44, 19, donated = false))
    }

    @Test
    fun `never prompts before the renderer has ever run`() {
        assertFalse(SponsorPrompt.shouldPrompt(0, 0, donated = false))
    }

    @Test
    fun `never prompts again once the user says they donated`() {
        assertFalse(SponsorPrompt.shouldPrompt(SponsorPrompt.INTERVAL, 0, donated = true))
        assertFalse(SponsorPrompt.shouldPrompt(10_000, 0, donated = true))
    }

    @Test
    fun `asking resets the distance, so the next prompt is another interval away`() {
        val asked = 20
        assertFalse(SponsorPrompt.shouldPrompt(asked + 1, asked, donated = false))
        assertTrue(SponsorPrompt.shouldPrompt(asked + SponsorPrompt.INTERVAL, asked, donated = false))
    }

    @Test
    fun `a count that went backwards does not prompt`() {
        // MG 目录被重建（或用户换了设备）时计数会从头开始，别因此立刻弹一次。
        assertFalse(SponsorPrompt.shouldPrompt(3, 120, donated = false))
    }

    @Test
    fun `stats parsing survives anything the file can be`() {
        assertEquals(42, MgStats.parse("""{"launchCount":42}""").launchCount)
        // 未来 native 加了别的计数器，旧版本读到多出来的键也不能崩。
        assertEquals(7, MgStats.parse("""{"launchCount":7,"totalSeconds":900}""").launchCount)
        assertEquals(0, MgStats.parse("""{"launchCount":-5}""").launchCount)
        assertEquals(0, MgStats.parse("""{}""").launchCount)
        assertEquals(0, MgStats.parse("{ this is not json").launchCount)
        assertEquals(0, MgStats.parse("").launchCount)
        assertEquals(0, MgStats.parse(null).launchCount)
    }
}
