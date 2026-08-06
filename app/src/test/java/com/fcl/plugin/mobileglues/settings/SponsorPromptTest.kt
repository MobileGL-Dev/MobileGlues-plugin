package com.fcl.plugin.mobileglues.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 赞助弹窗的判定。用取模而不是「上次问的次数」，所以即便某一次没弹成，节奏也不会错位——
 * 这些用例盯的就是这一点。
 */
class SponsorPromptTest {

    @Test
    fun `prompts on every interval-th launch`() {
        assertTrue(SponsorPrompt.shouldPrompt(SponsorPrompt.INTERVAL, donated = false))
        assertTrue(SponsorPrompt.shouldPrompt(SponsorPrompt.INTERVAL * 2, donated = false))
        assertTrue(SponsorPrompt.shouldPrompt(SponsorPrompt.INTERVAL * 17, donated = false))
    }

    @Test
    fun `stays quiet in between`() {
        assertFalse(SponsorPrompt.shouldPrompt(SponsorPrompt.INTERVAL - 1, donated = false))
        assertFalse(SponsorPrompt.shouldPrompt(SponsorPrompt.INTERVAL + 1, donated = false))
    }

    @Test
    fun `never prompts on the very first launch`() {
        assertFalse(SponsorPrompt.shouldPrompt(0, donated = false))
        assertFalse(SponsorPrompt.shouldPrompt(1, donated = false))
    }

    @Test
    fun `never prompts again once the user says they donated`() {
        assertFalse(SponsorPrompt.shouldPrompt(SponsorPrompt.INTERVAL, donated = true))
        assertFalse(SponsorPrompt.shouldPrompt(SponsorPrompt.INTERVAL * 100, donated = true))
    }

    @Test
    fun `a missed prompt does not shift the schedule`() {
        // 第 20 次没弹成（进程被杀），第 40 次照样会弹。
        assertTrue(SponsorPrompt.shouldPrompt(40, donated = false))
        assertFalse(SponsorPrompt.shouldPrompt(41, donated = false))
    }
}
