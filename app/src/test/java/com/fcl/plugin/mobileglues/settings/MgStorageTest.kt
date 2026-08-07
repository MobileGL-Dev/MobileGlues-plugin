package com.fcl.plugin.mobileglues.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * `deleteAll()` 只能删 MobileGlues 和插件自己创建的文件——MG 目录是用户看得见的目录，
 * 谁都可能手动放点别的东西进去，那份东西不该被「移除 MobileGlues」连坐。
 */
class MgStorageTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var mgDirectory: File

    @Before
    fun setUp() {
        mgDirectory = folder.newFolder("MG")
    }

    private fun newStorage() = DirectMgStorage(mgDirectory)

    @Test
    fun `deleteAll removes only the files MobileGlues and the plugin create`() {
        for (name in KNOWN_MG_FILE_NAMES) {
            File(mgDirectory, name).writeText("x")
        }
        val userFile = File(mgDirectory, "my_backup.zip")
        userFile.writeText("not ours")

        newStorage().deleteAll()

        for (name in KNOWN_MG_FILE_NAMES) {
            assertFalse("$name 应当被删除", File(mgDirectory, name).exists())
        }
        assertTrue("用户自己的文件不该被删", userFile.exists())
        assertTrue("目录里还有用户的文件，目录不能被删", mgDirectory.isDirectory)
    }

    @Test
    fun `deleteAll also removes the directory once it is empty`() {
        File(mgDirectory, CONFIG_FILE_NAME).writeText("x")
        File(mgDirectory, STATS_FILE_NAME).writeText("x")

        newStorage().deleteAll()

        assertFalse("清空之后目录本身也要删掉", mgDirectory.exists())
    }

    @Test
    fun `deleteAll on a missing directory is a no-op`() {
        mgDirectory.deleteRecursively()

        newStorage().deleteAll()

        assertFalse(mgDirectory.exists())
    }
}
