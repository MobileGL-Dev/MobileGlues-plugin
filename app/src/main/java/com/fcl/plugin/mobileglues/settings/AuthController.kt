package com.fcl.plugin.mobileglues.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.fcl.plugin.mobileglues.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** 一次授权判定的结果。[storage] 非空即「现在有访问权」。 */
data class AuthState(
    val method: AuthMethod?,
    val storage: MgStorage?,
) {
    val granted: Boolean get() = storage != null
}

/**
 * 决定「现在能不能碰 MG 目录」，并在授权建立后装配对应的 [MgStorage]。
 *
 * 授权只认本 App 记下来的那次明确选择（[PluginConfigStore.authMethod]），不从系统状态反推：
 * 「所有文件访问」这种系统级权限撤不掉，若是看到它开着就认作已授权，用户做完重置一开 App
 * 又会变成已授权，撤销等于没撤。
 *
 * 反过来，选择也不等于当前有效——所有文件访问可能被系统在设置里收回，SAF 的 URI 可能被吊销、
 * 目录可能被外部删除。所以每次界面回到前台都要 [refresh] 一次，用真实状态驱动权限门。
 */
class AuthController(
    private val context: Context,
    private val pluginConfigStore: PluginConfigStore,
) {

    private val appContext = context.applicationContext

    private val mutableState = MutableStateFlow(AuthState(pluginConfigStore.authMethod.value, null))
    val state: StateFlow<AuthState> = mutableState.asStateFlow()

    /** 重新核验当前记录的授权方式，并把结果发出去。 */
    fun refresh() {
        val method = pluginConfigStore.authMethod.value
        val storage = when (method) {
            AuthMethod.AllFiles ->
                if (hasAllFilesAccess()) directStorage() else null

            AuthMethod.Saf -> pluginConfigStore.safTreeUri
                ?.let { SafMgStorage(appContext, it.toUri()) }
                ?.takeIf { it.isAccessible() }

            AuthMethod.Legacy ->
                if (hasLegacyPermissions(appContext)) directStorage() else null

            null -> null
        }
        mutableState.value = AuthState(method, storage)
    }

    /** 「所有文件访问」已在系统设置里开好。 */
    fun grantAllFiles() {
        pluginConfigStore.setAuthMethod(AuthMethod.AllFiles)
        refresh()
    }

    /** [grantSaf] 的结果。 */
    enum class SafGrantResult { Success, WrongFolder, Inaccessible }

    /**
     * SAF 选择器返回的 tree URI。URI 的持久化（takePersistableUriPermission）由调用方做。
     * [SafGrantResult.WrongFolder] 时什么都没记录。
     */
    fun grantSaf(treeUri: Uri): SafGrantResult {
        if (!isMgDirectoryName(DocumentFile.fromTreeUri(appContext, treeUri)?.name)) {
            return SafGrantResult.WrongFolder
        }
        pluginConfigStore.safTreeUri = treeUri.toString()
        pluginConfigStore.setAuthMethod(AuthMethod.Saf)
        refresh()
        return if (state.value.granted) SafGrantResult.Success else SafGrantResult.Inaccessible
    }

    /** 旧版运行时权限已授予（Android 10 及以下）。 */
    fun grantLegacy() {
        pluginConfigStore.setAuthMethod(AuthMethod.Legacy)
        refresh()
    }

    /**
     * 忘掉授权选择。SAF 的持久化权限一并释放。
     *
     * 「所有文件访问」是系统级权限，本 App 撤不掉；但授权与否只看本 App 有没有记录，
     * 记录一清就等同于未授权，系统那边的权限开着也不会被用到。
     *
     * 两处会用到：「撤销授权」和「撤销并删除全部文件」。
     */
    fun revoke() {
        pluginConfigStore.safTreeUri?.let { raw ->
            runCatching {
                appContext.contentResolver.releasePersistableUriPermission(
                    raw.toUri(),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        pluginConfigStore.safTreeUri = null
        pluginConfigStore.setAuthMethod(null)
        refresh()
    }

    private fun directStorage() = DirectMgStorage(File(Constants.MG_DIRECTORY))

    companion object {
        /** SAF 目录授权只接受名字恰好是 MG 的目录——native 端读的是写死的 /sdcard/MG。 */
        fun isMgDirectoryName(name: String?): Boolean = name == SafMgStorage.MG_DIRECTORY_NAME

        fun hasAllFilesAccess(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

        fun hasLegacyPermissions(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED
    }
}
