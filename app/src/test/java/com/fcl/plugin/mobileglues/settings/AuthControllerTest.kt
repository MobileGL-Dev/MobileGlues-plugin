package com.fcl.plugin.mobileglues.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SAF 目录授权只接受名字恰好是 MG 的目录。
 *
 * native 端读的是写死的 `/sdcard/MG`：授权了别的目录，本 App 写得进去，游戏却读不到——
 * 那是一种最难排查的「设置不生效」，所以这条校验不能松。
 */
class AuthControllerTest {

    @Test
    fun `accepts exactly MG`() {
        assertTrue(AuthController.isMgDirectoryName("MG"))
    }

    @Test
    fun `rejects anything else`() {
        assertFalse(AuthController.isMgDirectoryName("mg"))
        assertFalse(AuthController.isMgDirectoryName("MG "))
        assertFalse(AuthController.isMgDirectoryName("MGlues"))
        assertFalse(AuthController.isMgDirectoryName("Download"))
        assertFalse(AuthController.isMgDirectoryName(""))
        assertFalse(AuthController.isMgDirectoryName(null))
    }
}
