package com.fcl.plugin.mobileglues

import android.Manifest
import android.app.ActivityManager
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.provider.Settings
import android.text.Html
import android.text.Spanned
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.PathInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.Transition
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.fcl.plugin.mobileglues.databinding.ActivityMainBinding
import com.fcl.plugin.mobileglues.settings.AngleConfig
import com.fcl.plugin.mobileglues.settings.ConfigLoadResult
import com.fcl.plugin.mobileglues.settings.ConfigStoreEvent
import com.fcl.plugin.mobileglues.settings.DepthClearFixMode
import com.fcl.plugin.mobileglues.settings.Fsr1Preset
import com.fcl.plugin.mobileglues.settings.GlVersion
import com.fcl.plugin.mobileglues.settings.GlslCacheSize
import com.fcl.plugin.mobileglues.settings.MGConfig
import com.fcl.plugin.mobileglues.settings.MultidrawBackend
import com.fcl.plugin.mobileglues.settings.MultidrawEntry
import com.fcl.plugin.mobileglues.settings.MultidrawSettings
import com.fcl.plugin.mobileglues.settings.NoErrorConfig
import com.fcl.plugin.mobileglues.settings.SpinnerOption
import com.fcl.plugin.mobileglues.utils.Constants
import com.fcl.plugin.mobileglues.utils.toast
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import androidx.appcompat.R as AppcompatR

class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener,
    CompoundButton.OnCheckedChangeListener {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val store by lazy { (application as MGApplication).configStore }
    private val cacheExporter by lazy { (application as MGApplication).cacheExporter }

    /** 查询 GPU 名字要创建一次 EGL 上下文，结果在进程内不会变，查一次就够。 */
    private var cachedIsAdreno740: Boolean? = null

    /** 正在进行的一次加载。onResume 不能在它完成之前把界面打回启动页。 */
    private var enterOptionsJob: Job? = null

    /** 滑块当前的 MiB 量程上限，见 [renderGlslCache]。 */
    private var glslCacheCeilingMebibytes: Int = 0

    /** GLSL 缓存滑块的上限：设备总内存的 1/[GLSL_CACHE_RAM_DIVISOR]。 */
    private val maxGlslCacheMebibytes: Int by lazy {
        val memoryInfo = ActivityManager.MemoryInfo()
        getSystemService(ActivityManager::class.java).getMemoryInfo(memoryInfo)
        val shareOfRam = memoryInfo.totalMem / (GLSL_CACHE_RAM_DIVISOR * 1024 * 1024)
        shareOfRam.coerceIn(MIN_GLSL_CACHE_UPPER_BOUND_MIB, MAX_GLSL_CACHE_UPPER_BOUND_MIB).toInt()
    }

    // ---- Activity Result Launchers ----
    private val manageAllFilesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            handlePermissionResult(hasMgDirectoryAccess())
        }

    private val appSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            handlePermissionResult(hasLegacyPermissions())
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            when {
                isGranted -> handlePermissionResult(true)
                !shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) ->
                    showGoToSettingsDialog()

                else -> handlePermissionResult(false)
            }
        }

    // ---- 生命周期 ----
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.isNavigationBarContrastEnforced = false
        }

        setContentView(binding.root)
        setSupportActionBar(binding.appBar)

        setupSpinners()
        setupMultidraw()
        attachListeners()
        setupWindowInsets()

        binding.openOptions.setOnClickListener {
            if (hasMgDirectoryAccess()) enterOptions() else requestPermission()
        }

        observeStore()
    }

    override fun onResume() {
        super.onResume()
        // 已授权且配置文件存在才自动进入设置；否则停在启动页。
        if (hasMgDirectoryAccess() && File(Constants.CONFIG_FILE_PATH).isFile) {
            enterOptions()
        } else if (enterOptionsJob?.isActive != true) {
            // 有加载正在进行时不能打断它：刚授权完的那条路径上 ActivityResult 回调先于 onResume
            // 送达，此刻 config.json 还没来得及建出来，文件「不存在」并不代表用户没有配置。
            leaveOptions()
        }
    }

    override fun onStop() {
        super.onStop()
        // 用 store 自己的作用域，不能用 lifecycleScope：旋转屏幕时后者会在 onDestroy 被取消，
        // 这次保存可能还没轮到 IO 线程就被砍掉。
        store.flushAsync()
    }

    // ---- 菜单 ----
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        menu.findItem(R.id.action_remove)?.isEnabled =
            hasMgDirectoryAccess() && File(Constants.CONFIG_FILE_PATH).exists()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_about -> {
            showAppInfoDialog(this) { showGlInfoDialog() }; true
        }

        R.id.action_remove -> {
            confirmRemoval(); true
        }

        else -> super.onOptionsItemSelected(item)
    }

    // ---- 配置 ⇄ 界面 ----

    private fun observeStore() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { store.config.collect { config -> config?.let(::render) } }
                launch { store.events.collect(::onStoreEvent) }
                launch {
                    combine(store.config, store.glslCacheBytes) { config, cacheBytes ->
                        cacheBytes.takeIf { config?.glslCache == GlslCacheSize.Disabled }
                    }.distinctUntilChanged().collect(::renderGlslCacheDeleteButton)
                }
            }
        }
    }

    private fun onStoreEvent(event: ConfigStoreEvent) {
        when (event) {
            is ConfigStoreEvent.SaveFailed -> snackbar(
                getString(
                    R.string.config_save_failed,
                    event.cause.message ?: event.cause.javaClass.simpleName,
                ),
                Snackbar.LENGTH_LONG,
            )
        }
    }

    /**
     * 把配置渲染到界面上。唯一的写界面入口。
     *
     * 这里不做「先摘掉监听器再赋值」的保护：Spinner 的选中回调是异步投递的（AbsSpinner 在布局
     * 阶段 post 出去），摘监听器根本挡不住。真正的保护在回调侧——取值与当前配置相同就直接返回，
     * 见 [onItemSelected] / [onCheckedChanged]。这是个与时序无关的判据，因此不需要
     * `isSpinnerInitialized` 之类的标志位。
     *
     * 输入框不参与常规回灌（[includeCacheSize] 默认为 false），否则用户输入 "032" 会
     * 被规范化成 "32" 打断光标。
     */
    private fun render(config: MGConfig, includeCacheSize: Boolean = false) {
        binding.spinnerAngle.setSelection(config.angle.ordinal)
        binding.spinnerNoError.setSelection(config.noError.ordinal)
        binding.angleClearWorkaround.setSelection(config.depthClearFix.ordinal)
        binding.spinnerCustomGlVersion.setSelection(config.glVersion.ordinal)

        binding.switchExtCs.isChecked = config.extComputeShader
        binding.switchExtDirectStateAccess.isChecked = config.extDirectStateAccess
        binding.switchEnableFsr1.isChecked = config.fsr1Enabled
        // 开关文案是「禁用 timer_query」，磁盘上 1 表示启用扩展：全 App 只有这一处取反。
        binding.switchExtTimerQuery.isChecked = !config.extTimerQuery

        renderMultidraw(config.multidraw)

        if (includeCacheSize) renderGlslCache(config.glslCache)
    }

    /**
     * 把缓存大小渲染到滑块上。
     *
     * 滑块的取值是「档位」（0..[GLSL_CACHE_SLIDER_STEPS]），MB 由 [mebibytesAtPosition] 换算，
     * 所以滑块自身的量程是常量——不会出现「上限低于当前值」这种 Slider 会抛异常的中间状态。
     *
     * 数字标签直接来自配置而不是回算档位：档位是离散的，回算会有 ±1MB 的误差，
     * 界面上显示的值必须和真正写进配置的值一致。
     */
    private fun renderGlslCache(size: GlslCacheSize) {
        val mebibytes = size.mebibytesOrZero
        // 配置里可能存着比设备内存 1/5 更大的值（旧配置或手工编辑）：把量程抬到它，
        // 宁可让滑块变长，也不能悄悄把用户的设置调低。
        glslCacheCeilingMebibytes = maxOf(maxGlslCacheMebibytes, mebibytes)

        binding.sliderGlslCache.value = positionForMebibytes(mebibytes).toFloat()
        renderGlslCacheLabel(mebibytes)
        binding.textGlslCacheMax.text =
            getString(R.string.option_glsl_cache_value, glslCacheCeilingMebibytes)
    }

    private fun renderGlslCacheLabel(mebibytes: Int) {
        binding.textGlslCacheValue.text = if (mebibytes <= 0) {
            getString(R.string.option_glsl_cache_off)
        } else {
            getString(R.string.option_glsl_cache_value, mebibytes)
        }
    }

    /**
     * 档位 → MiB，幂曲线刻度。
     *
     * 档位 0 是「关闭」；1..N 在 1 MiB 和上限之间按 [GLSL_CACHE_SLIDER_CURVE] 次幂分布：
     * 靠近 0 的一段一格只差一点点（16/32/64 这些真正要调的档位有足够行程），靠近上限一格跨几十 MiB。
     *
     * 弯度就是这一个指数：1 = 线性，越大越向低端倾斜（对数刻度相当于无穷大，低端过于夸张）。
     */
    private fun mebibytesAtPosition(position: Int): Int {
        if (position <= 0) return 0
        val ceiling = glslCacheCeilingMebibytes
        if (ceiling <= 1) return ceiling
        val ratio = (position - 1).toDouble() / (GLSL_CACHE_SLIDER_STEPS - 1)
        return (1 + (ceiling - 1) * ratio.pow(GLSL_CACHE_SLIDER_CURVE))
            .roundToInt()
            .coerceIn(1, ceiling)
    }

    /** [mebibytesAtPosition] 的逆运算。 */
    private fun positionForMebibytes(mebibytes: Int): Int {
        if (mebibytes <= 0) return 0
        val ceiling = glslCacheCeilingMebibytes
        if (ceiling <= 1) return GLSL_CACHE_SLIDER_STEPS
        val ratio = ((mebibytes - 1).toDouble() / (ceiling - 1)).coerceIn(0.0, 1.0)
        val position = ratio.pow(1.0 / GLSL_CACHE_SLIDER_CURVE) * (GLSL_CACHE_SLIDER_STEPS - 1)
        return (1 + position.roundToInt()).coerceIn(1, GLSL_CACHE_SLIDER_STEPS)
    }

    private fun enterOptions() {
        if (enterOptionsJob?.isActive == true) return
        enterOptionsJob = lifecycleScope.launch {
            when (val result = store.load()) {
                ConfigLoadResult.Missing -> {
                    // 先建立 config.json 再显示界面：菜单里的「移除 MobileGlues」是按
                    // 「配置文件是否存在」来启用的，顺序反了它会一直是灰的。
                    // 文件本来就不存在，写一份默认值不可能覆盖任何已有设置。
                    store.flush()
                    showOptions(MGConfig.Default)
                }

                is ConfigLoadResult.Loaded -> showOptions(result.config)

                is ConfigLoadResult.Corrupt -> promptCorruptConfig(result)
            }
        }
    }

    private fun showOptions(config: MGConfig) {
        render(config, includeCacheSize = true)
        binding.openOptions.visibility = View.GONE
        binding.scrollLayout.visibility = View.VISIBLE
        invalidateOptionsMenu()
    }

    /**
     * 回到启动页，并让 store 回到未加载状态。
     *
     * 后半句是必须的：用户在外部删掉 MG 目录后再切回本 App，如果 store 还攥着上一次读到的配置，
     * 退到后台时的那次保存就会把目录连同配置一起重建出来。
     */
    private fun leaveOptions() {
        store.forget()
        hideOptions()
    }

    private fun hideOptions() {
        binding.openOptions.visibility = View.VISIBLE
        binding.scrollLayout.visibility = View.GONE
        invalidateOptionsMenu()
    }

    /** 配置文件解析不了时问用户，绝不静默用默认值覆盖。 */
    private fun promptCorruptConfig(result: ConfigLoadResult.Corrupt) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_config_corrupt_title)
            .setMessage(
                getString(
                    R.string.dialog_config_corrupt_message,
                    result.backup?.name ?: Constants.CONFIG_FILE_NAME,
                    result.cause.message ?: result.cause.javaClass.simpleName,
                )
            )
            .setCancelable(false)
            .setPositiveButton(R.string.dialog_config_corrupt_reset) { _, _ ->
                lifecycleScope.launch {
                    store.resetToDefaults()
                    showOptions(MGConfig.Default)
                }
            }
            .setNegativeButton(R.string.dialog_negative) { _, _ -> hideOptions() }
            .show()
    }

    // ---- 界面初始化 ----

    private fun setupWindowInsets() {
        val optionLayoutParams = binding.optionLayout.layoutParams as ViewGroup.MarginLayoutParams
        window.decorView.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            val bottomInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // insets 是平台的 android.view.WindowInsets，这里必须用平台的 Type 常量，
                // 不能混用 WindowInsetsCompat.Type（数值恰好相同，但类型契约不同）。
                insets.getInsets(WindowInsets.Type.systemBars()).bottom
            } else {
                insets.systemWindowInsetBottom
            }
            optionLayoutParams.setMargins(0, 0, 0, bottomInset)
            insets
        }
    }

    /** 选项列表直接由枚举生成，因此不存在选项和取值对不上的可能。 */
    private fun setupSpinners() {
        bindSpinner(binding.spinnerAngle, AngleConfig.entries)
        bindSpinner(binding.spinnerNoError, NoErrorConfig.entries)
        bindSpinner(binding.angleClearWorkaround, DepthClearFixMode.entries)
        bindSpinner(binding.spinnerCustomGlVersion, GlVersion.entries)
    }

    // ---- MultiDraw ----

    private val multidrawSpinners = LinkedHashMap<MultidrawEntry, Spinner>()
    private val multidrawChips = LinkedHashMap<MultidrawBackend, Chip>()

    /**
     * 每个入口点一行、每个后端一枚筹码，全部由枚举生成。
     *
     * 和别处的 Spinner 同理：列表就是枚举本身，所以「选项和取值对不上」在结构上不可能发生。
     * 每个入口点可选的后端还各不相同（native 的 `k_md_entries::allowed`），写死在布局里必然会错。
     */
    private fun setupMultidraw() {
        MultidrawEntry.entries.forEach { entry ->
            val row = layoutInflater.inflate(R.layout.item_multidraw_entry, binding.multidrawEntries, false)
            row.findViewById<TextView>(R.id.text_entry_point).text = entry.glFunction

            val spinner = row.findViewById<Spinner>(R.id.spinner_entry_backend)
            spinner.adapter = ArrayAdapter(
                this, R.layout.spinner, entry.allowed.map { it.label(this) },
            ).apply { setDropDownViewResource(R.layout.spinner) }
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) =
                    onMultidrawBackendSelected(entry, position)

                override fun onNothingSelected(parent: AdapterView<*>) = Unit
            }

            multidrawSpinners[entry] = spinner
            binding.multidrawEntries.addView(row)
        }

        MultidrawBackend.entries.filter { it != MultidrawBackend.Auto }.forEach { backend ->
            val chip = Chip(this).apply {
                text = backend.label(this@MainActivity)
                isCheckable = true
                setOnCheckedChangeListener { _, isChecked ->
                    onMultidrawBackendDisabledChanged(backend, isChecked)
                }
            }
            multidrawChips[backend] = chip
            binding.chipsMultidrawDisabled.addView(chip)
        }

        binding.multidrawHeader.setOnClickListener { toggleMultidrawSection() }
    }

    private fun toggleMultidrawSection() {
        val expanded = binding.multidrawContent.visibility != View.VISIBLE
        TransitionManager.beginDelayedTransition(binding.optionLayout, revealTransition)
        binding.multidrawContent.visibility = if (expanded) View.VISIBLE else View.GONE
        binding.iconMultidrawExpand.animate()
            .rotation(if (expanded) 180f else 0f)
            .setDuration(REVEAL_DURATION_MS)
            .start()
    }

    private fun onMultidrawBackendSelected(entry: MultidrawEntry, position: Int) {
        val current = store.config.value ?: return
        val backend = entry.allowed.getOrNull(position) ?: return
        if (backend == current.multidraw.backendOf(entry)) return
        store.update { it.copy(multidraw = it.multidraw.with(entry, backend)) }
    }

    private fun onMultidrawBackendDisabledChanged(backend: MultidrawBackend, disabled: Boolean) {
        val current = store.config.value ?: return
        if (disabled == (backend in current.multidraw.disabledBackends)) return
        store.update { it.copy(multidraw = it.multidraw.withBackendDisabled(backend, disabled)) }
    }

    private fun renderMultidraw(settings: MultidrawSettings) {
        multidrawSpinners.forEach { (entry, spinner) ->
            val position = entry.allowed.indexOf(settings.backendOf(entry)).coerceAtLeast(0)
            spinner.setSelection(position)
        }
        multidrawChips.forEach { (backend, chip) ->
            chip.isChecked = backend in settings.disabledBackends
        }

        val customized = settings.customizedCount
        binding.textMultidrawSummary.text = if (customized == 0) {
            getString(R.string.option_multidraw_summary_auto)
        } else {
            getString(R.string.option_multidraw_summary_custom, customized)
        }
    }

    private fun bindSpinner(spinner: Spinner, options: List<SpinnerOption>) {
        spinner.adapter =
            ArrayAdapter(this, R.layout.spinner, options.map { it.label(this) }).apply {
                setDropDownViewResource(R.layout.spinner)
            }
    }

    private fun attachListeners() {
        binding.spinnerAngle.onItemSelectedListener = this
        binding.spinnerNoError.onItemSelectedListener = this
        binding.spinnerCustomGlVersion.onItemSelectedListener = this
        binding.angleClearWorkaround.onItemSelectedListener = this

        binding.switchExtCs.setOnCheckedChangeListener(this)
        binding.switchExtTimerQuery.setOnCheckedChangeListener(this)
        binding.switchExtDirectStateAccess.setOnCheckedChangeListener(this)
        binding.switchEnableFsr1.setOnCheckedChangeListener(this)

        glslCacheCeilingMebibytes = maxGlslCacheMebibytes
        binding.sliderGlslCache.valueTo = GLSL_CACHE_SLIDER_STEPS.toFloat()
        binding.sliderGlslCache.addOnChangeListener { _, position, fromUser ->
            onGlslCacheSliderChanged(position.toInt(), fromUser)
        }
        binding.buttonClearGlslCache.setOnClickListener { deleteGlslCache() }
    }

    // ---- Spinner 回调 ----

    override fun onItemSelected(adapterView: AdapterView<*>, view: View?, position: Int, id: Long) {
        // 配置尚未加载：这是 Spinner 装上适配器时投递的初始化回调，不是用户操作。
        val current = store.config.value ?: return

        when (adapterView.id) {
            R.id.spinner_angle -> {
                val target = AngleConfig.entries.getOrNull(position) ?: return
                if (target == current.angle) return
                lifecycleScope.launch {
                    val approved = target != AngleConfig.ForceEnable ||
                            !isAdreno740() ||
                            confirm(R.string.warning_adreno_740_angle)
                    if (approved) store.update { it.copy(angle = target) } else revert()
                }
            }

            R.id.spinner_no_error -> {
                val target = NoErrorConfig.entries.getOrNull(position) ?: return
                store.update { it.copy(noError = target) }
            }

            R.id.spinner_custom_gl_version -> {
                val target = GlVersion.entries.getOrNull(position) ?: return
                if (target == current.glVersion) return
                if (current.glVersion == GlVersion.Default) {
                    // 只在「从不启用切到某个具体版本」时才需要冷静期。
                    confirmThenUpdate(
                        R.string.warning_enabling_custom_gl_version,
                        CUSTOM_GL_VERSION_COOLDOWN_SECONDS,
                    ) { it.copy(glVersion = target) }
                } else {
                    store.update { it.copy(glVersion = target) }
                }
            }

            R.id.angle_clear_workaround -> {
                val target = DepthClearFixMode.entries.getOrNull(position) ?: return
                if (target == current.depthClearFix) return
                if (target == DepthClearFixMode.Disabled) {
                    store.update { it.copy(depthClearFix = target) }
                } else {
                    confirmThenUpdate(R.string.warning_enabling_angle_clear_workaround) {
                        it.copy(depthClearFix = target)
                    }
                }
            }
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>) = Unit

    // ---- Switch 回调 ----

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        val current = store.config.value ?: return

        when (buttonView.id) {
            R.id.switch_ext_cs -> {
                if (isChecked == current.extComputeShader) return
                if (isChecked) {
                    confirmThenUpdate(R.string.warning_ext_cs_enable) {
                        it.copy(extComputeShader = true)
                    }
                } else {
                    store.update { it.copy(extComputeShader = false) }
                }
            }

            R.id.switch_enable_fsr1 -> {
                if (isChecked == current.fsr1Enabled) return
                if (isChecked) {
                    confirmThenUpdate(R.string.warning_fsr1_enable) {
                        it.copy(fsr1 = Fsr1Preset.UltraQuality)
                    }
                } else {
                    store.update { it.copy(fsr1 = Fsr1Preset.Disabled) }
                }
            }

            // 开关文案是「禁用 timer_query」，勾上等于磁盘上写 0。
            R.id.switch_ext_timer_query -> store.update { it.copy(extTimerQuery = !isChecked) }

            R.id.switch_ext_direct_state_access ->
                store.update { it.copy(extDirectStateAccess = isChecked) }
        }
    }

    // ---- GLSL 缓存输入框 ----

    private fun onGlslCacheSliderChanged(position: Int, fromUser: Boolean) {
        // 程序化回灌不算用户操作；数字标签由 renderGlslCache 直接按配置值设置。
        if (!fromUser) return

        val mebibytes = mebibytesAtPosition(position)
        renderGlslCacheLabel(mebibytes)
        // 关掉缓存只是改配置，不动已有的缓存文件——要不要删由用户按那个按钮决定。
        store.update { it.copy(glslCache = GlslCacheSize.ofMebibytes(mebibytes)) }
    }

    /**
     * 「删除已缓存的着色器」按钮只在「缓存已关闭 **且** 缓存文件确实还在」时出现，
     * [cacheBytes] 为 `null` 即表示不该出现。
     *
     * 用 [TransitionManager] 在整个选项面板上开一次延迟过渡：按钮淡入的同时，
     * ChangeBounds 会把它下面的所有控件平滑地推下去让位，收起时同理。
     */
    private fun renderGlslCacheDeleteButton(cacheBytes: Long?) {
        val button = binding.buttonClearGlslCache
        cacheBytes?.let {
            button.text = getString(R.string.option_glsl_cache_delete, formatCacheSize(it))
        }

        val target = if (cacheBytes != null) View.VISIBLE else View.GONE
        if (button.visibility == target) return

        // 面板本身还没显示时不做动画，免得和「进入设置」那一下叠在一起。
        if (binding.scrollLayout.visibility == View.VISIBLE) {
            TransitionManager.beginDelayedTransition(binding.optionLayout, revealTransition)
        }
        button.visibility = target
    }

    private val revealTransition: Transition by lazy {
        TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            addTransition(ChangeBounds())
            addTransition(Fade())
            duration = REVEAL_DURATION_MS
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
        }
    }

    /** 与滑块保持同一套单位（MiB / KiB），不用 SI 的 MB。 */
    private fun formatCacheSize(bytes: Long): String {
        val mebibytes = bytes / (1024.0 * 1024.0)
        return if (mebibytes >= 1.0) {
            getString(R.string.option_glsl_cache_size_mib, mebibytes)
        } else {
            getString(R.string.option_glsl_cache_size_kib, bytes / 1024.0)
        }
    }

    private fun deleteGlslCache() {
        lifecycleScope.launch {
            store.clearGlslCache().onFailure { cause ->
                snackbar(
                    getString(
                        R.string.option_glsl_cache_delete_failed,
                        cause.message ?: cause.javaClass.simpleName,
                    )
                )
            }
        }
    }

    // ---- 权限 ----

    private fun handlePermissionResult(isGranted: Boolean) {
        if (isGranted) {
            enterOptions()
        } else {
            snackbar(getString(R.string.permission_failed))
            leaveOptions()
        }
    }

    private fun hasMgDirectoryAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            hasLegacyPermissions()
        }

    private fun hasLegacyPermissions(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            showManageAllFilesDialog()
        } else if (hasLegacyPermissions()) {
            enterOptions()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun showManageAllFilesDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_permission_title)
            .setMessage(getString(R.string.dialog_permission_msg_android_Q, Constants.MG_DIRECTORY))
            .setPositiveButton(R.string.dialog_positive) { _, _ -> launchManageAllFilesSettings() }
            .setNegativeButton(R.string.dialog_negative, null)
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun launchManageAllFilesSettings() {
        try {
            manageAllFilesLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    "package:$packageName".toUri(),
                )
            )
        } catch (_: Exception) {
            manageAllFilesLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    private fun showGoToSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_permission_title)
            .setMessage(R.string.dialog_permission_msg)
            .setPositiveButton(R.string.dialog_positive) { _, _ ->
                appSettingsLauncher.launch(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null),
                    )
                )
            }
            .setNegativeButton(R.string.dialog_negative, null)
            .show()
    }

    // ---- 确认对话框 ----

    /**
     * 挂起直到用户做出选择。取消、返回键、对话框被系统关掉都算「否」。
     *
     * 把对话框写成挂起函数之后，「先问再改」的流程就是一条直线，不再需要把 onConfirm /
     * onCancel 两条回调拆开传。
     */
    private suspend fun confirm(@StringRes messageRes: Int, countdownSeconds: Int = 0): Boolean =
        suspendCancellableCoroutine { continuation ->
            var timer: CountDownTimer? = null
            val hasCountdown = countdownSeconds > 0

            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_warning)
                .setMessage(
                    if (hasCountdown) styledMessage(messageRes) else getString(messageRes)
                )
                .setCancelable(false)
                .setPositiveButton(if (hasCountdown) R.string.ok else R.string.dialog_positive, null)
                .setNegativeButton(R.string.dialog_negative, null)
                .setOnDismissListener {
                    timer?.cancel()
                    if (continuation.isActive) continuation.resume(false)
                }
                .show()

            val positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            positive.setOnClickListener {
                if (continuation.isActive) continuation.resume(true)
                dialog.dismiss()
            }
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
                .setOnClickListener { dialog.dismiss() }

            if (hasCountdown) {
                positive.isEnabled = false
                timer = object : CountDownTimer(countdownSeconds * 1000L, 1000L) {
                    override fun onTick(millisUntilFinished: Long) {
                        positive.text = getString(
                            R.string.ok_with_countdown,
                            (millisUntilFinished / 1000).toInt(),
                        )
                    }

                    override fun onFinish() {
                        positive.text = getString(R.string.ok)
                        positive.setTextColor(
                            MaterialColors.getColor(
                                positive.context,
                                AppcompatR.attr.colorError,
                                Color.RED,
                            )
                        )
                        positive.isEnabled = true
                    }
                }.also { it.start() }
            }

            continuation.invokeOnCancellation {
                timer?.cancel()
                dialog.dismiss()
            }
        }

    private fun confirmThenUpdate(
        @StringRes messageRes: Int,
        countdownSeconds: Int = 0,
        transform: (MGConfig) -> MGConfig,
    ) {
        lifecycleScope.launch {
            if (confirm(messageRes, countdownSeconds)) store.update(transform) else revert()
        }
    }

    /** 撤销 = 用当前生效的配置重新渲染一次。单向数据流下不需要记住「之前选的是哪一项」。 */
    private fun revert() {
        store.config.value?.let { render(it) }
    }

    private fun styledMessage(@StringRes id: Int): Spanned {
        val errorColorHex = String.format(
            "#%06X",
            0xFFFFFF and MaterialColors.getColor(this, AppcompatR.attr.colorError, Color.RED),
        )
        return Html.fromHtml(
            getString(id).replace("@colorError", errorColorHex),
            Html.FROM_HTML_MODE_LEGACY,
        )
    }

    // ---- MobileGlues 信息 ----

    private fun showGlInfoDialog() {
        lifecycleScope.launch {
            val directory = cacheExporter.export().getOrElse { cacheExporter.directory }
            // dlopen + 创建 EGL 上下文是重活，别放在主线程上。
            val info = withContext(Dispatchers.Default) { MGInfoGetter.info(directory) }
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.dialog_mg_gl_info_title)
                .setMessage(info)
                .setNegativeButton(R.string.dismiss, null)
                .show()
                .findViewById<TextView>(android.R.id.message)
                ?.setTextIsSelectable(true)
        }
    }

    // ---- 移除 MobileGlues ----

    private fun confirmRemoval() {
        lifecycleScope.launch {
            if (!confirm(R.string.remove_mg_files_message, REMOVE_COOLDOWN_SECONDS)) return@launch
            removeMobileGluesCompletely()
        }
    }

    private suspend fun removeMobileGluesCompletely() {
        val progressDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.removing_mobileglues)
            .setView(LayoutInflater.from(this).inflate(R.layout.progress_dialog_md3, null))
            .setCancelable(false)
            .show()

        val result = withContext(Dispatchers.IO) {
            runCatching { File(Constants.MG_DIRECTORY).deleteRecursively() }
        }
        progressDialog.dismiss()

        result
            .onSuccess {
                leaveOptions()
                showRemovalCompleteDialog()
            }
            .onFailure { toast(getString(R.string.remove_failed, it.message)) }
    }

    private fun showRemovalCompleteDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.remove_complete_title)
            .setMessage(R.string.remove_complete_message)
            .setCancelable(false)
            .setPositiveButton(R.string.exit) { _, _ -> finishAffinity(); exitProcess(0) }
            .show()
    }

    // ---- GPU 检测 ----

    private suspend fun isAdreno740(): Boolean {
        cachedIsAdreno740?.let { return it }
        val name = withContext(Dispatchers.Default) { queryGpuName() }
        val result = name != null &&
                name.contains("adreno", ignoreCase = true) &&
                name.contains("740")
        cachedIsAdreno740 = result
        return result
    }

    private fun queryGpuName(): String? {
        val eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY ||
            !EGL14.eglInitialize(eglDisplay, IntArray(2), 0, IntArray(2), 1)
        ) return null

        var renderer: String? = null
        val configAttributes =
            intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE)
        val eglConfigs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)

        if (EGL14.eglChooseConfig(
                eglDisplay, configAttributes, 0, eglConfigs, 0, 1, numConfigs, 0
            )
        ) {
            val contextAttributes = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val eglContext = EGL14.eglCreateContext(
                eglDisplay, eglConfigs[0], EGL14.EGL_NO_CONTEXT, contextAttributes, 0
            )

            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                val pbufferAttributes =
                    intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
                val eglSurface =
                    EGL14.eglCreatePbufferSurface(eglDisplay, eglConfigs[0], pbufferAttributes, 0)
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    if (EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                        renderer = GLES20.glGetString(GLES20.GL_RENDERER)
                    }
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                }
                EGL14.eglDestroyContext(eglDisplay, eglContext)
            }
        }
        EGL14.eglTerminate(eglDisplay)
        return renderer
    }

    // ---- Snackbar ----

    fun snackbar(text: CharSequence, duration: Int = Snackbar.LENGTH_SHORT) {
        Snackbar.make(binding.root, text, duration).show()
    }

    private companion object {
        const val CUSTOM_GL_VERSION_COOLDOWN_SECONDS = 41
        const val REMOVE_COOLDOWN_SECONDS = 10

        /** 滑块上限取总内存的几分之一。 */
        const val GLSL_CACHE_RAM_DIVISOR = 16L

        /** 内存特别小的设备上也要留出可用的调节范围。 */
        const val MIN_GLSL_CACHE_UPPER_BOUND_MIB = 64L
        const val MAX_GLSL_CACHE_UPPER_BOUND_MIB = 8192L

        /** 滑块的档位数（不含 0 号「关闭」档）。 */
        const val GLSL_CACHE_SLIDER_STEPS = 200

        /** 滑块刻度的弯度：1 = 线性，越大越向低端倾斜。 */
        const val GLSL_CACHE_SLIDER_CURVE = 2.0

        /** 展开 / 收起的动画时长。 */
        const val REVEAL_DURATION_MS = 240L
    }
}
