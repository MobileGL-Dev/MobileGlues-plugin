package com.fcl.plugin.mobileglues.settings

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 对 `MG/` 目录的访问抽象。
 *
 * 两种实现：[DirectMgStorage]（「所有文件访问」或旧版存储权限，直接走文件系统）和
 * [SafMgStorage]（SAF 目录授权，走 DocumentsContract）。native 端永远读 `/sdcard/MG` 的真实路径，
 * 这个抽象只对 App 自己的读写生效——两种授权方式写出来的是同一个目录、同一个 config.json。
 *
 * 所有函数都可能做 IO，调用方负责放到后台线程。
 */
interface MgStorage {

    /** 给用户看的路径，两种模式下都指 `/sdcard/MG`。 */
    val displayPath: String

    /** 授权此刻是否仍然有效：SAF 的 URI 可能被吊销，目录也可能被用户在外部删掉。 */
    fun isAccessible(): Boolean

    fun configExists(): Boolean

    /** 读配置全文。文件不存在或不可读时抛 [IOException]。 */
    @Throws(IOException::class)
    fun readConfig(): String

    /**
     * 写入配置全文。
     *
     * [DirectMgStorage] 是原子写（临时文件 + rename）；SAF 没有 rename 可用，
     * [SafMgStorage] 只能截断式覆写，是尽力而为。
     */
    @Throws(IOException::class)
    fun writeConfig(text: String)

    /** 配置损坏时把原文备份到配置旁边，返回备份文件名；备份失败返回 null。 */
    fun writeCorruptBackup(text: String): String?

    /** GLSL 缓存文件的字节数；文件不存在为 null。 */
    fun glslCacheBytes(): Long?

    @Throws(IOException::class)
    fun deleteGlslCache()

    /** 连 MG 目录一起删除（危险区域的「移除 MobileGlues」）。 */
    @Throws(IOException::class)
    fun deleteAll()
}

/** 直接文件访问：拥有「所有文件访问」或旧版存储权限时使用。 */
class DirectMgStorage(private val root: File) : MgStorage {

    private val configFile = File(root, CONFIG_FILE_NAME)
    private val glslCacheFile = File(root, GLSL_CACHE_FILE_NAME)

    override val displayPath: String get() = root.absolutePath

    override fun isAccessible(): Boolean = root.isDirectory || root.exists().not()

    override fun configExists(): Boolean = configFile.isFile

    override fun readConfig(): String = configFile.readText()

    override fun writeConfig(text: String) = configFile.writeAtomically(text)

    override fun writeCorruptBackup(text: String): String? = runCatching {
        val backup = File(root, CONFIG_FILE_NAME + CORRUPT_BACKUP_SUFFIX)
        backup.writeText(text)
        backup.name
    }.getOrNull()

    override fun glslCacheBytes(): Long? = glslCacheFile.takeIf { it.isFile }?.length()

    override fun deleteGlslCache() {
        if (glslCacheFile.exists() && !glslCacheFile.delete()) {
            throw IOException("Could not delete ${glslCacheFile.path}")
        }
    }

    override fun deleteAll() {
        if (root.exists() && !root.deleteRecursively()) {
            throw IOException("Could not delete ${root.path}")
        }
    }
}

/**
 * SAF 目录授权访问：用户通过系统文件选择器把 MG 目录授给本 App。
 *
 * URI 已经过 `takePersistableUriPermission` 持久化，但仍有失效路径——用户在系统设置里
 * 吊销授权、或者直接把目录删了，[isAccessible] 都会返回 false。
 */
class SafMgStorage(
    context: Context,
    private val treeUri: Uri,
) : MgStorage {

    private val appContext = context.applicationContext

    override val displayPath: String get() = "/sdcard/$MG_DIRECTORY_NAME"

    private fun tree(): DocumentFile? =
        DocumentFile.fromTreeUri(appContext, treeUri)?.takeIf { it.isDirectory }

    override fun isAccessible(): Boolean {
        val persisted = appContext.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission && it.isWritePermission
        }
        return persisted && tree() != null
    }

    private fun child(name: String): DocumentFile? = tree()?.findFile(name)

    @Throws(IOException::class)
    private fun childForWrite(name: String, mimeType: String): DocumentFile {
        child(name)?.takeIf { it.isFile }?.let { return it }
        val dir = tree() ?: throw IOException("MG directory is not accessible")
        return dir.createFile(mimeType, name)
            ?: throw IOException("Could not create $name")
    }

    override fun configExists(): Boolean = child(CONFIG_FILE_NAME)?.isFile == true

    override fun readConfig(): String {
        val doc = child(CONFIG_FILE_NAME)?.takeIf { it.isFile }
            ?: throw IOException("$MG_DIRECTORY_NAME/$CONFIG_FILE_NAME not found")
        return appContext.contentResolver.openInputStream(doc.uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: throw IOException("Could not open ${doc.uri}")
    }

    override fun writeConfig(text: String) {
        val doc = childForWrite(CONFIG_FILE_NAME, JSON_MIME_TYPE)
        appContext.contentResolver.openOutputStream(doc.uri, "wt")?.use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
        } ?: throw IOException("Could not open ${doc.uri} for writing")
    }

    override fun writeCorruptBackup(text: String): String? = runCatching {
        val name = CONFIG_FILE_NAME + CORRUPT_BACKUP_SUFFIX
        // 不能用 JSON_MIME_TYPE：扩展名对不上时系统提供器可能擅自再补一个 .json 后缀。
        val doc = childForWrite(name, FALLBACK_MIME_TYPE)
        appContext.contentResolver.openOutputStream(doc.uri, "wt")?.use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
        }
        name
    }.getOrNull()

    override fun glslCacheBytes(): Long? =
        child(GLSL_CACHE_FILE_NAME)?.takeIf { it.isFile }?.length()

    override fun deleteGlslCache() {
        val doc = child(GLSL_CACHE_FILE_NAME) ?: return
        if (!doc.delete()) throw IOException("Could not delete $GLSL_CACHE_FILE_NAME")
    }

    override fun deleteAll() {
        val dir = tree() ?: return
        if (!dir.delete()) throw IOException("Could not delete MG directory")
    }

    companion object {
        const val MG_DIRECTORY_NAME = "MG"
        private const val JSON_MIME_TYPE = "application/json"
        private const val FALLBACK_MIME_TYPE = "application/octet-stream"
    }
}

internal const val CONFIG_FILE_NAME = "config.json"
internal const val GLSL_CACHE_FILE_NAME = "glsl_cache.tmp"
internal const val CORRUPT_BACKUP_SUFFIX = ".corrupt"

/**
 * 先写临时文件再 rename。
 *
 * 同目录下的 rename 是原子的，所以读的一方（游戏里的 MobileGlues）要么看到旧内容，
 * 要么看到完整的新内容，不会看到写到一半的文件。
 */
private fun File.writeAtomically(text: String) {
    parentFile?.mkdirs()
    val temporary = File(parentFile, "$name.tmp")
    FileOutputStream(temporary).use { output ->
        output.write(text.toByteArray())
        output.flush()
        output.fd.sync()
    }
    if (!temporary.renameTo(this)) {
        // 同目录 rename 正常不会失败；万一失败就退回直接写，至少不会把配置丢掉。
        temporary.delete()
        writeText(text)
    }
}
