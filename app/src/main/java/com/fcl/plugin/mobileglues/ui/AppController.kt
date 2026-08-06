package com.fcl.plugin.mobileglues.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.net.toUri
import com.fcl.plugin.mobileglues.BuildConfig
import com.fcl.plugin.mobileglues.DeviceInfo
import com.fcl.plugin.mobileglues.DeviceInfoProvider
import com.fcl.plugin.mobileglues.MGApplication
import com.fcl.plugin.mobileglues.MGInfoGetter
import com.fcl.plugin.mobileglues.R
import com.fcl.plugin.mobileglues.settings.AngleConfig
import com.fcl.plugin.mobileglues.settings.AuthController
import com.fcl.plugin.mobileglues.settings.AuthMethod
import com.fcl.plugin.mobileglues.settings.ConfigLoadResult
import com.fcl.plugin.mobileglues.settings.ConfigStoreEvent
import com.fcl.plugin.mobileglues.settings.DepthClearFixMode
import com.fcl.plugin.mobileglues.settings.Fsr1Preset
import com.fcl.plugin.mobileglues.settings.GlVersion
import com.fcl.plugin.mobileglues.settings.GlslCacheScale
import com.fcl.plugin.mobileglues.settings.GlslCacheSize
import com.fcl.plugin.mobileglues.settings.MGConfig
import com.fcl.plugin.mobileglues.settings.MgStats
import com.fcl.plugin.mobileglues.settings.MultidrawBackend
import com.fcl.plugin.mobileglues.settings.MultidrawEntry
import com.fcl.plugin.mobileglues.settings.NoErrorConfig
import com.fcl.plugin.mobileglues.settings.SponsorPrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** 底部导航的三个页。 */
enum class AppTab { Home, Settings, Info }

/** 从信息页进入的子页面（带返回）。 */
enum class AppSubPage { GlInfo, Privacy }

/** 配置加载状态：权限门之内的内容区按它决定显示什么。 */
enum class SettingsLoadState { NotLoaded, Loading, Ready }

/** 授权流程的对话框状态机。由皮肤渲染，按钮回调进 [AppController]。 */
sealed interface AuthPrompt {
    /** 选择授权方式（Android 11+：所有文件访问 / SAF）。 */
    data object ChooseMethod : AuthPrompt

    /** 「所有文件访问」的说明，确认后跳系统设置。 */
    data object AllFilesIntro : AuthPrompt

    /** SAF 引导：提示用户新建/选择 MG 目录。 */
    data object SafGuide : AuthPrompt

    /** 旧版权限被永久拒绝后引导去应用详情页。 */
    data object LegacyDenied : AuthPrompt
}

/** 赞助弹窗的两步。 */
sealed interface SponsorPromptState {
    /** 第一步：温和地询问（报出具体启动次数）。 */
    data class Ask(val launchCount: Int) : SponsorPromptState

    /** 第二步：跳转捐赠页之后，委婉地确认是否已捐赠。 */
    data object Confirm : SponsorPromptState
}

/**
 * 一次「先问再改」的确认。倒计时结束后 [errorAccent] 让确认键变成警示色——
 * 和旧界面自定义 GL 版本 / 移除 MobileGlues 的行为一致。
 */
class ConfirmRequest(
    @param:StringRes val titleRes: Int,
    val message: String,
    /** message 是 HTML（含 @colorError 占位符），皮肤用自己的 error 色替换后解析。 */
    val messageIsHtml: Boolean = false,
    @param:StringRes val positiveRes: Int = R.string.dialog_positive,
    val countdownSeconds: Int = 0,
    val errorAccent: Boolean = false,
    private val onResult: (Boolean) -> Unit,
) {
    private val resolved = java.util.concurrent.atomic.AtomicBoolean(false)

    /** 按钮、返回键、对话框关闭可能各触发一次，只有第一次算数。 */
    fun resolve(result: Boolean) {
        if (resolved.compareAndSet(false, true)) onResult(result)
    }
}

/** 由 MainActivity 实现：只有 Activity 能发起系统授权页 / 退出应用。 */
interface AuthFlowLauncher {
    fun openAllFilesSettings()
    fun openSafPicker()
    fun requestLegacyPermission()
    fun openAppDetailsSettings()
    fun exitApp()
}

/**
 * 两套皮肤（MD3 / Miuix）共用的全部 UI 逻辑：导航、授权流、配置加载、
 * 设置动作（含全部警告与倒计时）、赞助弹窗、移除流程、GL 信息查询。
 * 皮肤只负责把这些状态画出来，并把用户操作回调进来——操作逻辑因此只有一份。
 */
class AppController(
    private val app: MGApplication,
    private val context: Context,
    private val launcher: AuthFlowLauncher,
) {

    val pluginConfig = app.pluginConfigStore
    val configStore = app.configStore
    val auth = app.authController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ---- 导航 ----

    private val mutableTab = MutableStateFlow(AppTab.Home)
    val tab: StateFlow<AppTab> = mutableTab.asStateFlow()

    private val mutableSubPage = MutableStateFlow<AppSubPage?>(null)
    val subPage: StateFlow<AppSubPage?> = mutableSubPage.asStateFlow()

    fun navigateTab(target: AppTab) {
        mutableSubPage.value = null
        mutableTab.value = target
    }

    fun openSubPage(page: AppSubPage) {
        mutableSubPage.value = page
    }

    /** 返回键：有子页面先退子页面。返回 true 表示已消费。 */
    fun navigateBack(): Boolean {
        return if (mutableSubPage.value != null) {
            mutableSubPage.value = null
            true
        } else {
            false
        }
    }

    // ---- Snackbar ----

    private val mutableSnackbar = MutableSharedFlow<CharSequence>(extraBufferCapacity = 8)
    val snackbar: MutableSharedFlow<CharSequence> = mutableSnackbar

    fun snackbar(text: CharSequence) {
        mutableSnackbar.tryEmit(text)
    }

    // ---- 确认对话框 ----

    private val mutableConfirm = MutableStateFlow<ConfirmRequest?>(null)
    val confirmRequest: StateFlow<ConfirmRequest?> = mutableConfirm.asStateFlow()

    /**
     * 挂起直到用户做出选择。取消、返回键、点遮罩都算「否」。
     * 「先问再改」的流程因此是一条直线，不需要拆 onConfirm/onCancel 两条回调。
     */
    suspend fun confirm(
        @StringRes messageRes: Int,
        countdownSeconds: Int = 0,
        messageIsHtml: Boolean = false,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        mutableConfirm.value = ConfirmRequest(
            titleRes = R.string.dialog_title_warning,
            message = context.getString(messageRes),
            messageIsHtml = messageIsHtml,
            positiveRes = if (countdownSeconds > 0) R.string.ok else R.string.dialog_positive,
            countdownSeconds = countdownSeconds,
            errorAccent = countdownSeconds > 0,
        ) { result ->
            mutableConfirm.value = null
            if (continuation.isActive) continuation.resume(result)
        }
        continuation.invokeOnCancellation { mutableConfirm.value = null }
    }

    // ---- 授权流 ----

    private val mutableAuthPrompt = MutableStateFlow<AuthPrompt?>(null)
    val authPrompt: StateFlow<AuthPrompt?> = mutableAuthPrompt.asStateFlow()

    /** 「去授权」的总入口：Android 11+ 先选方式，以下直接走旧版运行时权限。 */
    fun requestAccess() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            mutableAuthPrompt.value = AuthPrompt.ChooseMethod
        } else {
            launcher.requestLegacyPermission()
        }
    }

    fun dismissAuthPrompt() {
        mutableAuthPrompt.value = null
    }

    fun onAuthMethodSelected(method: AuthMethod) {
        mutableAuthPrompt.value = when (method) {
            AuthMethod.AllFiles -> AuthPrompt.AllFilesIntro
            AuthMethod.Saf -> AuthPrompt.SafGuide
            AuthMethod.Legacy -> {
                launcher.requestLegacyPermission()
                null
            }
        }
    }

    /** AllFilesIntro 的确认键：跳系统的所有文件访问设置页。 */
    fun proceedAllFiles() {
        mutableAuthPrompt.value = null
        launcher.openAllFilesSettings()
    }

    /** SafGuide 的确认键：打开系统目录选择器。 */
    fun proceedSaf() {
        mutableAuthPrompt.value = null
        launcher.openSafPicker()
    }

    /** LegacyDenied 的确认键：跳应用详情页。 */
    fun proceedAppDetails() {
        mutableAuthPrompt.value = null
        launcher.openAppDetailsSettings()
    }

    // ---- 由 MainActivity 回填的授权结果 ----

    fun onAllFilesResult() {
        if (AuthController.hasAllFilesAccess()) {
            auth.grantAllFiles()
        } else {
            snackbar(context.getString(R.string.permission_failed))
        }
    }

    fun onSafResult(uri: Uri?) {
        if (uri == null) return
        when (auth.grantSaf(uri)) {
            AuthController.SafGrantResult.Success -> Unit
            AuthController.SafGrantResult.WrongFolder ->
                snackbar(context.getString(R.string.auth_wrong_folder))

            AuthController.SafGrantResult.Inaccessible ->
                snackbar(context.getString(R.string.permission_failed))
        }
    }

    fun onLegacyPermissionResult(granted: Boolean, permanentlyDenied: Boolean) {
        when {
            granted -> auth.grantLegacy()
            permanentlyDenied -> mutableAuthPrompt.value = AuthPrompt.LegacyDenied
            else -> snackbar(context.getString(R.string.permission_failed))
        }
    }

    // ---- 配置加载 ----

    private val mutableLoadState = MutableStateFlow(SettingsLoadState.NotLoaded)
    val loadState: StateFlow<SettingsLoadState> = mutableLoadState.asStateFlow()

    private val mutableCorruptPrompt = MutableStateFlow<ConfigLoadResult.Corrupt?>(null)
    val corruptPrompt: StateFlow<ConfigLoadResult.Corrupt?> = mutableCorruptPrompt.asStateFlow()

    /** 正在进行的一次加载。授权状态翻转不能在它完成之前把界面打回未加载。 */
    private var loadJob: Job? = null

    init {
        // 授权状态是唯一事实来源：授权建立 → 接入存储并加载；失效 → 摘掉存储退回未加载。
        scope.launch {
            auth.state.collect { state ->
                val storage = state.storage
                if (state.granted && storage != null) {
                    configStore.attachStorage(storage)
                    ensureConfigLoaded()
                } else {
                    configStore.detachStorage()
                    mutableLoadState.value = SettingsLoadState.NotLoaded
                }
            }
        }
        scope.launch {
            configStore.events.collect { event ->
                when (event) {
                    is ConfigStoreEvent.SaveFailed -> snackbar(
                        context.getString(
                            R.string.config_save_failed,
                            event.cause.message ?: event.cause.javaClass.simpleName,
                        ),
                    )
                }
            }
        }
    }

    /** 前台恢复时调用：重新核验授权（系统设置里可能刚改完）。 */
    fun refreshAuthorization() = auth.refresh()

    private fun ensureConfigLoaded() {
        if (mutableLoadState.value == SettingsLoadState.Ready) return
        if (loadJob?.isActive == true) return
        loadJob = scope.launch {
            mutableLoadState.value = SettingsLoadState.Loading
            when (val result = configStore.load()) {
                ConfigLoadResult.Missing -> {
                    // 先建立 config.json 再亮出设置：危险区域的「移除」按授权+加载状态启用。
                    configStore.flush()
                    mutableLoadState.value = SettingsLoadState.Ready
                }

                is ConfigLoadResult.Loaded -> mutableLoadState.value = SettingsLoadState.Ready

                is ConfigLoadResult.Corrupt -> {
                    mutableLoadState.value = SettingsLoadState.NotLoaded
                    mutableCorruptPrompt.value = result
                }
            }
        }
    }

    /** 配置文件损坏 → 用户选择重置。 */
    fun resetCorruptConfig() {
        mutableCorruptPrompt.value = null
        scope.launch {
            configStore.resetToDefaults()
            mutableLoadState.value = SettingsLoadState.Ready
        }
    }

    /** 配置文件损坏 → 用户选择不重置：回首页，不碰那个文件。 */
    fun dismissCorruptConfig() {
        mutableCorruptPrompt.value = null
        navigateTab(AppTab.Home)
    }

    // ---- 设备信息 ----

    private val mutableDeviceInfo = MutableStateFlow<DeviceInfo?>(null)
    val deviceInfo: StateFlow<DeviceInfo?> = mutableDeviceInfo.asStateFlow()

    fun ensureDeviceInfo() {
        if (mutableDeviceInfo.value != null) return
        scope.launch { mutableDeviceInfo.value = DeviceInfoProvider.get(context) }
    }

    // ---- 设置动作（皮肤回调的唯一入口）----

    private fun update(transform: (MGConfig) -> MGConfig) = configStore.update(transform)

    private fun confirmThenUpdate(
        @StringRes messageRes: Int,
        countdownSeconds: Int = 0,
        messageIsHtml: Boolean = false,
        transform: (MGConfig) -> MGConfig,
    ) {
        scope.launch {
            if (confirm(messageRes, countdownSeconds, messageIsHtml)) update(transform)
            // 取消时什么都不做：单向数据流下界面从未离开当前配置，不需要「撤销」。
        }
    }

    fun selectAngle(target: AngleConfig) {
        val current = configStore.config.value ?: return
        if (target == current.angle) return
        scope.launch {
            val approved = target != AngleConfig.ForceEnable ||
                !DeviceInfoProvider.isAdreno740(context) ||
                confirm(R.string.warning_adreno_740_angle)
            if (approved) update { it.copy(angle = target) }
        }
    }

    fun selectNoError(target: NoErrorConfig) = update { it.copy(noError = target) }

    fun selectGlVersion(target: GlVersion) {
        val current = configStore.config.value ?: return
        if (target == current.glVersion) return
        if (current.glVersion == GlVersion.Default) {
            // 只在「从不启用切到某个具体版本」时才需要冷静期。
            confirmThenUpdate(
                R.string.warning_enabling_custom_gl_version,
                CUSTOM_GL_VERSION_COOLDOWN_SECONDS,
                messageIsHtml = true,
            ) { it.copy(glVersion = target) }
        } else {
            update { it.copy(glVersion = target) }
        }
    }

    fun selectDepthClearFix(target: DepthClearFixMode) {
        val current = configStore.config.value ?: return
        if (target == current.depthClearFix) return
        if (target == DepthClearFixMode.Disabled) {
            update { it.copy(depthClearFix = target) }
        } else {
            confirmThenUpdate(R.string.warning_enabling_angle_clear_workaround) {
                it.copy(depthClearFix = target)
            }
        }
    }

    fun setExtComputeShader(enabled: Boolean) {
        val current = configStore.config.value ?: return
        if (enabled == current.extComputeShader) return
        if (enabled) {
            confirmThenUpdate(R.string.warning_ext_cs_enable) { it.copy(extComputeShader = true) }
        } else {
            update { it.copy(extComputeShader = false) }
        }
    }

    /** 开关文案是「禁用 timer_query」，勾上等于磁盘上写 0。 */
    fun setExtTimerQueryDisabled(disabled: Boolean) = update { it.copy(extTimerQuery = !disabled) }

    fun setExtDirectStateAccess(enabled: Boolean) = update { it.copy(extDirectStateAccess = enabled) }

    fun setFsr1(enabled: Boolean) {
        val current = configStore.config.value ?: return
        if (enabled == current.fsr1Enabled) return
        if (enabled) {
            confirmThenUpdate(R.string.warning_fsr1_enable) { it.copy(fsr1 = Fsr1Preset.UltraQuality) }
        } else {
            update { it.copy(fsr1 = Fsr1Preset.Disabled) }
        }
    }

    /** 滑块档位 → MiB → 配置。关掉缓存只是改配置，不动已有的缓存文件。 */
    fun setGlslCacheSliderPosition(position: Int, ceiling: Int) {
        update { it.copy(glslCache = GlslCacheSize.ofMebibytes(GlslCacheScale.mebibytesAt(position, ceiling))) }
    }

    fun deleteGlslCache() {
        scope.launch {
            configStore.clearGlslCache().onFailure { cause ->
                snackbar(
                    context.getString(
                        R.string.option_glsl_cache_delete_failed,
                        cause.message ?: cause.javaClass.simpleName,
                    ),
                )
            }
        }
    }

    fun selectMultidrawBackend(entry: MultidrawEntry, backend: MultidrawBackend) {
        update { it.copy(multidraw = it.multidraw.with(entry, backend)) }
    }

    fun setMultidrawBackendDisabled(backend: MultidrawBackend, disabled: Boolean) {
        update { it.copy(multidraw = it.multidraw.withBackendDisabled(backend, disabled)) }
    }

    // ---- 首页配置摘要 ----

    /**
     * 「ANGLE 尽可能启用 · 缓存 32 MiB」式的一行只读摘要。
     *
     * 取值本身（「尽可能启用」「不启用」）离开设置页就没有意义了，所以这里带上是谁的取值；
     * GL 版本只在被自定义过的时候才出现——默认那一档说了等于没说。
     */
    fun configSummary(config: MGConfig): String = listOfNotNull(
        context.getString(R.string.home_summary_angle, config.angle.label(context)),
        config.glVersion.takeIf { it != GlVersion.Default }?.label(context)?.toString(),
        context.getString(
            R.string.home_summary_cache,
            (config.glslCache as? GlslCacheSize.Limited)?.let {
                context.getString(R.string.option_glsl_cache_value, it.mebibytes)
            } ?: context.getString(R.string.option_glsl_cache_off),
        ),
    ).joinToString(" · ")

    /** 与滑块保持同一套单位（MiB / KiB），不用 SI 的 MB。 */
    fun formatCacheSize(bytes: Long): String {
        val mebibytes = bytes / (1024.0 * 1024.0)
        return if (mebibytes >= 1.0) {
            context.getString(R.string.option_glsl_cache_size_mib, mebibytes)
        } else {
            context.getString(R.string.option_glsl_cache_size_kib, bytes / 1024.0)
        }
    }

    // ---- 外链 ----

    fun openGitHub() = openUrl(GITHUB_URL)

    fun openSponsorLink() = openUrl(SPONSOR_URL)

    private fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }

    val appVersionName: String get() = BuildConfig.VERSION_NAME

    // ---- 赞助弹窗 ----

    private val mutableSponsorPrompt = MutableStateFlow<SponsorPromptState?>(null)
    val sponsorPrompt: StateFlow<SponsorPromptState?> = mutableSponsorPrompt.asStateFlow()

    /**
     * 首页出现时调用。每个进程最多弹一次；判定见 [SponsorPrompt]。
     *
     * 启动次数要去 MG 目录里读（native 库记的），所以未授权时问不出结果——那就先不问，
     * 等用户授权之后自然会再走到这里。
     */
    fun maybeShowSponsorPrompt() {
        if (app.sponsorPromptedThisProcess) return
        if (pluginConfig.donated.value) return
        val storage = auth.state.value.storage ?: return

        scope.launch {
            val stats = withContext(Dispatchers.IO) { MgStats.parse(storage.readStats()) }
            if (app.sponsorPromptedThisProcess) return@launch
            if (!SponsorPrompt.shouldPrompt(
                    stats.launchCount,
                    pluginConfig.lastSponsorPromptAt,
                    pluginConfig.donated.value,
                )
            ) {
                return@launch
            }
            app.sponsorPromptedThisProcess = true
            // 记在弹出的这一刻，而不是等用户点某个按钮：点了「下次再说」就走人的话，
            // 下次启动会立刻又弹一遍。
            pluginConfig.markSponsorPromptedAt(stats.launchCount)
            mutableSponsorPrompt.value = SponsorPromptState.Ask(stats.launchCount)
        }
    }

    /** 「去支持一下」：跳转捐赠页，然后委婉地二次确认。 */
    fun onSponsorDonate() {
        openSponsorLink()
        mutableSponsorPrompt.value = SponsorPromptState.Confirm
    }

    fun onSponsorLater() {
        mutableSponsorPrompt.value = null
    }

    /** 「已捐赠」：记录并永不再弹。 */
    fun onSponsorDonated() {
        pluginConfig.markDonated()
        mutableSponsorPrompt.value = null
    }

    fun onSponsorNotYet() {
        mutableSponsorPrompt.value = null
    }

    // ---- GL 信息 ----

    private val mutableGlInfo = MutableStateFlow<String?>(null)
    val glInfo: StateFlow<String?> = mutableGlInfo.asStateFlow()

    private val mutableGlInfoLoading = MutableStateFlow(false)
    val glInfoLoading: StateFlow<Boolean> = mutableGlInfoLoading.asStateFlow()

    /** 每次进入 GL 信息页都重新查询（渲染器库可能刚被游戏更新过）。 */
    fun loadGlInfo() {
        if (mutableGlInfoLoading.value) return
        mutableGlInfoLoading.value = true
        mutableGlInfo.value = null
        scope.launch {
            val directory = app.cacheExporter.export().getOrElse { app.cacheExporter.directory }
            // dlopen + 创建 EGL 上下文是重活，别放在主线程上。
            val info = withContext(Dispatchers.Default) { MGInfoGetter.info(directory) }
            mutableGlInfo.value = info
            mutableGlInfoLoading.value = false
        }
    }

    // ---- 移除 MobileGlues ----

    private val mutableRemoving = MutableStateFlow(false)
    val removing: StateFlow<Boolean> = mutableRemoving.asStateFlow()

    private val mutableRemovalComplete = MutableStateFlow(false)
    val removalComplete: StateFlow<Boolean> = mutableRemovalComplete.asStateFlow()

    fun removeMobileGlues() {
        scope.launch {
            if (!confirm(
                    R.string.remove_mg_files_message,
                    REMOVE_COOLDOWN_SECONDS,
                    messageIsHtml = true,
                )
            ) {
                return@launch
            }
            mutableRemoving.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // 没有存储就等于什么都没删掉，不能报「移除完成」。
                    val storage = auth.state.value.storage
                        ?: throw java.io.IOException("MG directory is not authorized")
                    storage.deleteAll()
                }
            }
            mutableRemoving.value = false
            result
                .onSuccess {
                    // revoke 会触发 auth.state 翻转，存储随即被 detach，配置回到未加载。
                    auth.revoke()
                    mutableRemovalComplete.value = true
                }
                .onFailure {
                    snackbar(
                        context.getString(
                            R.string.remove_failed,
                            it.message ?: it.javaClass.simpleName,
                        ),
                    )
                }
        }
    }

    /** 移除完成对话框的唯一出口：退出应用。 */
    fun exitAfterRemoval() = launcher.exitApp()

    fun destroy() = scope.cancel()

    companion object {
        const val GITHUB_URL = "https://github.com/MobileGL-Dev/MobileGlues-release"
        const val SPONSOR_URL = "https://www.buymeacoffee.com/Swung0x48"

        const val CUSTOM_GL_VERSION_COOLDOWN_SECONDS = 41
        const val REMOVE_COOLDOWN_SECONDS = 10
    }
}
