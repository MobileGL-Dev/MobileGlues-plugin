package com.fcl.plugin.mobileglues.settings

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 这些用例盯的是「什么时候**不该**写文件」——配置丢失的老 bug 全部出在这里。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MGConfigStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = UnconfinedTestDispatcher(scheduler)
    private val storeScope = CoroutineScope(dispatcher)

    private lateinit var mgDirectory: File
    private lateinit var configFile: File

    @Before
    fun setUp() {
        mgDirectory = folder.newFolder("MG")
        configFile = File(mgDirectory, "config.json")
    }

    @After
    fun tearDown() {
        storeScope.cancel()
    }

    private fun newStore() = MGConfigStore(
        storage = DirectMgStorage(mgDirectory),
        scope = storeScope,
        io = dispatcher,
    )

    private fun readConfig(): JsonObject =
        JsonParser.parseString(configFile.readText()).asJsonObject

    @Test
    fun `a missing config file is reported as missing and only created on flush`() =
        runTest(dispatcher) {
            val store = newStore()

            assertEquals(ConfigLoadResult.Missing, store.load())
            assertEquals(MGConfig.Default, store.config.value)
            assertFalse("load() 不允许自己建文件", configFile.exists())

            store.flush()
            assertTrue(configFile.exists())
            assertEquals(MGConfig.Default, MGConfigCodec.decode(readConfig()))
        }

    @Test
    fun `flush writes nothing when the user changed nothing`() = runTest(dispatcher) {
        configFile.writeText("""{"enableANGLE":3,"hideMGEnvLevel":1}""")
        val store = newStore()
        assertTrue(store.load() is ConfigLoadResult.Loaded)

        // 模拟「加载之后文件被别人改了」，随后本 App 退到后台。
        val externallyWritten = """{"marker":true}"""
        configFile.writeText(externallyWritten)
        store.flush()

        assertEquals(externallyWritten, configFile.readText())
    }

    @Test
    fun `a corrupt config file is backed up and never overwritten`() = runTest(dispatcher) {
        val broken = "{ this is not json"
        configFile.writeText(broken)
        val store = newStore()

        val result = store.load()
        assertTrue(result is ConfigLoadResult.Corrupt)
        val backupName = (result as ConfigLoadResult.Corrupt).backupName
        assertEquals(broken, File(mgDirectory, backupName!!).readText())
        assertNull("损坏之后 store 必须回到未加载状态", store.config.value)

        // 用户选了「取消」：后续任何写入尝试都不能碰原文件。
        store.update { it.copy(angle = AngleConfig.ForceEnable) }
        store.flush()
        assertEquals(broken, configFile.readText())
    }

    @Test
    fun `forget stops the store from writing anything`() = runTest(dispatcher) {
        configFile.writeText("""{"enableANGLE":2}""")
        val store = newStore()
        store.load()
        store.update { it.copy(angle = AngleConfig.ForceEnable) }

        // 用户在外部删掉了 MG 目录，界面退回启动页。
        store.forget()
        configFile.delete()
        store.flush()

        assertFalse("退到后台不能把删掉的目录重建出来", configFile.exists())
    }

    @Test
    fun `an edit is written and unknown keys survive it`() = runTest(dispatcher) {
        configFile.writeText("""{"enableANGLE":0,"hideMGEnvLevel":1}""")
        val store = newStore()
        store.load()

        store.update { it.copy(angle = AngleConfig.ForceEnable, glslCache = GlslCacheSize.Disabled) }
        store.flush()

        val root = readConfig()
        assertEquals(3, root.get("enableANGLE").asInt)
        assertEquals(-1, root.get("maxGlslCacheSize").asInt)
        assertEquals(1, root.get("hideMGEnvLevel").asInt)
    }

    @Test
    fun `reloading does not clobber an edit that has not reached the disk yet`() =
        runTest(dispatcher) {
            configFile.writeText("""{"enableANGLE":0}""")
            val store = newStore()
            store.load()
            store.update { it.copy(angle = AngleConfig.ForceEnable) }

            // Activity 重建（旋转屏幕）会在去抖窗口内再 load 一次。
            assertTrue(store.load() is ConfigLoadResult.Loaded)

            assertEquals(AngleConfig.ForceEnable, store.config.value?.angle)
            assertEquals(3, readConfig().get("enableANGLE").asInt)
        }

    @Test
    fun `writing leaves no temporary file behind`() = runTest(dispatcher) {
        val store = newStore()
        store.load()
        store.update { it.copy(fsr1 = Fsr1Preset.UltraQuality) }
        store.flush()

        assertEquals(listOf("config.json"), mgDirectory.list()!!.sorted())
    }

    @Test
    fun `turning the cache off does not touch the cache file`() = runTest(dispatcher) {
        val cacheFile = File(mgDirectory, "glsl_cache.tmp")
        cacheFile.writeText("shaders")  // 7 字节
        configFile.writeText("""{"maxGlslCacheSize":64}""")
        val store = newStore()
        store.load()

        store.update { it.copy(glslCache = GlslCacheSize.Disabled) }
        store.flush()

        assertTrue("关闭缓存只改配置，不能顺手删文件", cacheFile.exists())
        assertEquals(7L, store.glslCacheBytes.value)
        assertEquals(-1, readConfig().get("maxGlslCacheSize").asInt)
    }

    @Test
    fun `clearing the cache deletes the file and updates the observable presence`() =
        runTest(dispatcher) {
            val cacheFile = File(mgDirectory, "glsl_cache.tmp")
            cacheFile.writeText("shaders")  // 7 字节
            configFile.writeText("{}")
            val store = newStore()
            store.load()
            assertEquals(7L, store.glslCacheBytes.value)

            assertTrue(store.clearGlslCache().isSuccess)

            assertFalse(cacheFile.exists())
            assertNull("删掉之后按钮要能立刻收回去", store.glslCacheBytes.value)
        }

    @Test
    fun `clearing an absent cache is a no-op success`() = runTest(dispatcher) {
        configFile.writeText("{}")
        val store = newStore()
        store.load()

        assertNull(store.glslCacheBytes.value)
        assertTrue(store.clearGlslCache().isSuccess)
    }

    @Test
    fun `update is ignored while the store is not loaded`() = runTest(dispatcher) {
        val store = newStore()

        store.update { it.copy(angle = AngleConfig.ForceEnable) }
        store.flush()

        assertNull(store.config.value)
        assertFalse(configFile.exists())
    }
}
