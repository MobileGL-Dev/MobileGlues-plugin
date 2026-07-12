package com.capslock800000.optimizedmg

import android.Manifest
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
import android.text.Editable
import android.text.Html
import android.text.Spanned
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.Spinner
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.capslock800000.optimizedmg.databinding.ActivityMainBinding
import com.capslock800000.optimizedmg.settings.MGConfig
import com.capslock800000.optimizedmg.utils.Constants
import com.capslock800000.optimizedmg.utils.toast
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.exitProcess
import androidx.appcompat.R as AppcompatR

class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener,
    CompoundButton.OnCheckedChangeListener {

    // ---- OpenGL 版本映射 ----
    private val glVersionMap: Map<String, Int> by lazy {
        linkedMapOf(
            getString(R.string.option_angle_disable) to 0,
            "OpenGL 4.6" to 46, "OpenGL 4.5" to 45, "OpenGL 4.4" to 44,
            "OpenGL 4.3" to 43, "OpenGL 4.2" to 42, "OpenGL 4.1" to 41,
            "OpenGL 4.0" to 40, "OpenGL 3.3" to 33, "OpenGL 3.2" to 32
        )
    }

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private var config: MGConfig? = null
    private var isSpinnerInitialized = false

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
                !shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> showGoToSettingsDialog()
                else -> handlePermissionResult(false)
            }
        }

    private fun handlePermissionResult(isGranted: Boolean) {
        if (isGranted) {
            MGConfig(this).save()
            showOptions()
        } else {
            snackbar(getString(R.string.permission_failed))
            hideOptions()
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

        binding.openOptions.setOnClickListener {
            if (hasMgDirectoryAccess()) {
                // 如果已经授权，但因没有 MG 文件夹而停留在首屏，直接触发创建并进入设置
                handlePermissionResult(true)
            } else {
                // 如果未授权，走正常的授权流程
                checkPermission()
            }
        }
        setupWindowInsets()
    }

    override fun onResume() {
        super.onResume()
        checkPermissionSilently()
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
            showAppInfoDialog(this, config); true
        }

        R.id.action_remove -> {
            showRemoveConfirmationDialog(); true
        }

        else -> super.onOptionsItemSelected(item)
    }

    // ---- UI 初始化 ----
    private fun setupWindowInsets() {
        val optionLayoutParams = binding.optionLayout.layoutParams as ViewGroup.MarginLayoutParams
        window.decorView.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            val bottomInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            } else {
                insets.systemWindowInsetBottom
            }
            optionLayoutParams.setMargins(0, 0, 0, bottomInset)
            insets
        }
    }

    private fun setupSpinners() {
        fun bindSpinner(spinner: Spinner, arrayRes: Int) {
            ArrayAdapter.createFromResource(this, arrayRes, R.layout.spinner).also { adapter ->
                adapter.setDropDownViewResource(R.layout.spinner)
                spinner.adapter = adapter
            }
        }

        bindSpinner(binding.spinnerAngle, R.array.angle_options)
        bindSpinner(binding.spinnerNoError, R.array.no_error_options)
        bindSpinner(binding.spinnerMultidrawMode, R.array.multidraw_mode_options)
        bindSpinner(binding.angleClearWorkaround, R.array.angle_clear_workaround_options)

        binding.spinnerCustomGlVersion.adapter =
            ArrayAdapter(this, R.layout.spinner, ArrayList(glVersionMap.keys))
    }

    // ---- 权限检查 ----
    private fun hasMgDirectoryAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            hasLegacyPermissions()
        }

    private fun hasLegacyPermissions(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED

    private fun checkPermissionSilently() {
        // 关键逻辑：只有在已经授权且配置文件/MG文件夹存在时，才自动进入设置
        val isConfigured = File(Constants.CONFIG_FILE_PATH).exists()

        if (hasMgDirectoryAccess() && isConfigured) {
            (MGConfig.loadConfig(this) ?: MGConfig(this)).save()
            showOptions()
        } else {
            // 否则（未授权，或已授权但未点击启用），停留在启动页并显示 openOptions 按钮
            hideOptions()
        }
    }

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            showManageAllFilesDialog()
        } else {
            if (hasLegacyPermissions()) showOptions()
            else requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    // ---- 权限相关对话框 ----
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
                    "package:$packageName".toUri()
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
                        Uri.fromParts("package", packageName, null)
                    )
                )
            }
            .setNegativeButton(R.string.dialog_negative, null)
            .show()
    }

    // ---- 选项面板显示/隐藏 ----
    private fun showOptions() {
        isSpinnerInitialized = false
        setAllListeners(null)

        config = (MGConfig.loadConfig(this) ?: MGConfig(this)).apply {
            if (enableANGLE !in 0..3) enableANGLE = 0
            if (enableNoError !in 0..3) enableNoError = 0
            if (maxGlslCacheSize <= 0 && maxGlslCacheSize != -1) maxGlslCacheSize = 32

            binding.inputMaxGlslCacheSize.setText(maxGlslCacheSize.toString())
            binding.spinnerAngle.setSelection(enableANGLE)
            binding.spinnerNoError.setSelection(enableNoError)
            binding.spinnerMultidrawMode.setSelection(multidrawMode)
            binding.angleClearWorkaround.setSelection(angleDepthClearFixMode)

            binding.switchExtTimerQuery.isChecked = enableExtTimerQuery == 0
            binding.switchExtDirectStateAccess.isChecked = enableExtDirectStateAccess == 1
            binding.switchExtCs.isChecked = enableExtComputeShader == 1
            binding.switchEnableFsr1.isChecked = fsr1Setting == 1

            binding.spinnerCustomGlVersion.setSelection(getSpinnerIndexByGLVersion(customGLVersion))
        }

        setAllListeners(this)
        setupGlslCacheSizeWatcher()

        binding.openOptions.visibility = View.GONE
        binding.scrollLayout.visibility = View.VISIBLE

        binding.root.post { isSpinnerInitialized = true }
    }

    private fun hideOptions() {
        binding.openOptions.visibility = View.VISIBLE
        binding.scrollLayout.visibility = View.GONE
        invalidateOptionsMenu()
    }

    private fun setAllListeners(listener: Any?) {
        val itemListener = listener as? AdapterView.OnItemSelectedListener
        val checkedListener = listener as? CompoundButton.OnCheckedChangeListener

        invalidateOptionsMenu()

        binding.apply {
            spinnerAngle.onItemSelectedListener = itemListener
            spinnerNoError.onItemSelectedListener = itemListener
            spinnerMultidrawMode.onItemSelectedListener = itemListener
            spinnerCustomGlVersion.onItemSelectedListener = itemListener
            angleClearWorkaround.onItemSelectedListener = itemListener

            switchExtCs.setOnCheckedChangeListener(checkedListener)
            switchExtTimerQuery.setOnCheckedChangeListener(checkedListener)
            switchExtDirectStateAccess.setOnCheckedChangeListener(checkedListener)
            switchEnableFsr1.setOnCheckedChangeListener(checkedListener)
        }
    }

    private fun setupGlslCacheSizeWatcher() {
        binding.inputMaxGlslCacheSize.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val text = s.toString().trim()
                if (text.isEmpty()) {
                    binding.inputMaxGlslCacheSizeLayout.error = null
                    config?.maxGlslCacheSize = 32
                    return
                }
                text.toIntOrNull()?.let { number ->
                    if (number < -1 || number == 0) {
                        binding.inputMaxGlslCacheSizeLayout.error =
                            getString(R.string.option_glsl_cache_error_range)
                    } else {
                        binding.inputMaxGlslCacheSizeLayout.error = null
                        config?.maxGlslCacheSize = number
                    }
                } ?: run {
                    binding.inputMaxGlslCacheSizeLayout.error =
                        getString(R.string.option_glsl_cache_error_invalid)
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    // ---- Spinner 回调 ----
    override fun onItemSelected(adapterView: AdapterView<*>, view: View?, position: Int, id: Long) {
        if (!isSpinnerInitialized || config == null) return

        when (adapterView.id) {
            R.id.spinner_angle -> handleAngleSelection(position)
            R.id.spinner_no_error -> config?.enableNoError = position
            R.id.spinner_multidraw_mode -> config?.multidrawMode = position
            R.id.spinner_custom_gl_version -> handleCustomGLVersionSelection(position)
            R.id.angle_clear_workaround -> handleAngleClearWorkaroundSelection(position)
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>) {}

    private fun handleAngleSelection(position: Int) {
        val previous = config?.enableANGLE ?: return
        if (position == previous) return

        if (position == 3 && isAdreno740()) {
            showWarningDialog(
                messageRes = R.string.warning_adreno_740_angle,
                onConfirm = { config?.enableANGLE = position },
                onCancel = { revertSpinner(binding.spinnerAngle, previous) }
            )
        } else {
            config?.enableANGLE = position
        }
    }

    private fun handleAngleClearWorkaroundSelection(position: Int) {
        val previous = config?.angleDepthClearFixMode ?: return
        if (position == previous) return

        if (position >= 1) {
            showWarningDialog(
                messageRes = R.string.warning_enabling_angle_clear_workaround,
                onConfirm = { config?.angleDepthClearFixMode = position },
                onCancel = { revertSpinner(binding.angleClearWorkaround, previous) }
            )
        } else {
            config?.angleDepthClearFixMode = position
        }
    }

    private fun handleCustomGLVersionSelection(position: Int) {
        val previous = config?.customGLVersion ?: return
        val newValue = getGLVersionBySpinnerIndex(position)
        if (newValue == previous) return

        if (previous == 0) {
            showCountdownWarningDialog(
                messageRes = R.string.warning_enabling_custom_gl_version,
                cooldownSeconds = 41,
                onConfirm = { config?.customGLVersion = newValue },
                onCancel = {
                    revertSpinner(
                        binding.spinnerCustomGlVersion,
                        getSpinnerIndexByGLVersion(previous)
                    )
                }
            )
        } else {
            config?.customGLVersion = newValue
        }
    }

    // ---- Switch 回调 ----
    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (config == null) return

        when (buttonView.id) {
            R.id.switch_ext_cs -> handleSwitchWithWarning(
                isChecked = isChecked,
                warningMsgRes = R.string.warning_ext_cs_enable,
                onConfirm = { config?.enableExtComputeShader = 1 },
                onCancel = { config?.enableExtComputeShader = 0 },
                button = buttonView
            )

            R.id.switch_enable_fsr1 -> handleSwitchWithWarning(
                isChecked = isChecked,
                warningMsgRes = R.string.warning_fsr1_enable,
                onConfirm = { config?.fsr1Setting = 1 },
                onCancel = { config?.fsr1Setting = 0 },
                button = buttonView
            )

            R.id.switch_ext_timer_query -> config?.enableExtTimerQuery = if (isChecked) 0 else 1
            R.id.switch_ext_direct_state_access -> config?.enableExtDirectStateAccess =
                if (isChecked) 1 else 0
        }
    }

    private fun handleSwitchWithWarning(
        isChecked: Boolean,
        @StringRes warningMsgRes: Int,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
        button: CompoundButton
    ) {
        if (isChecked) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_warning)
                .setMessage(warningMsgRes)
                .setCancelable(false)
                .setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK }
                .setPositiveButton(R.string.dialog_positive) { _, _ -> onConfirm() }
                .setNegativeButton(R.string.dialog_negative) { _, _ ->
                    button.isChecked = false
                    onCancel()
                }
                .show()
        } else {
            onCancel()
        }
    }

    // ---- 对话框辅助 ----
    private fun showWarningDialog(
        @StringRes messageRes: Int,
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_warning)
            .setMessage(getString(messageRes))
            .setCancelable(false)
            .setPositiveButton(R.string.dialog_positive) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.dialog_negative) { _, _ -> onCancel() }
            .show()
    }

    private fun showCountdownWarningDialog(
        @StringRes messageRes: Int,
        cooldownSeconds: Int,
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_warning)
            .setMessage(getStyledMessage(messageRes))
            .setCancelable(false)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.dialog_negative) { _, _ -> onCancel() }
            .show()

        val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        positiveButton.isEnabled = false
        positiveButton.setOnClickListener { onConfirm(); dialog.dismiss() }

        object : CountDownTimer(cooldownSeconds * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                positiveButton.text =
                    getString(R.string.ok_with_countdown, (millisUntilFinished / 1000).toInt())
            }

            override fun onFinish() {
                positiveButton.apply {
                    text = getString(R.string.ok)
                    setTextColor(
                        MaterialColors.getColor(
                            context,
                            AppcompatR.attr.colorError,
                            Color.RED
                        )
                    )
                    isEnabled = true
                }
            }
        }.start()
    }

    // ---- 删除 OptimizedMG ----
    private fun showRemoveConfirmationDialog() {
        showCountdownWarningDialog(
            R.string.remove_mg_files_message,
            10,
            { removeOptimizedMGCompletely() },
            {})
    }

    private fun removeOptimizedMGCompletely() {
        val view = LayoutInflater.from(this).inflate(R.layout.progress_dialog_md3, null)

        val progressDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.removing_optimizedmg)
            .setView(view)
            .setCancelable(false)
            .show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                File(Constants.MG_DIRECTORY).deleteRecursively()

                withContext(Dispatchers.Main) {
                    config = null
                    hideOptions()
                    progressDialog.dismiss()
                    showFinalDialog()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    toast(getString(R.string.remove_failed, e.message))
                }
            }
        }
    }

    private fun showFinalDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.remove_complete_title)
            .setMessage(R.string.remove_complete_message)
            .setCancelable(false)
            .setPositiveButton(R.string.exit) { _, _ -> finishAffinity(); exitProcess(0) }
            .show()
    }

    // ---- GPU 检测 ----
    private fun getGPUName(): String? {
        val eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(
                eglDisplay,
                IntArray(2),
                0,
                IntArray(2),
                1
            )
        ) return null

        var renderer: String? = null
        val configAttributes =
            intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE)
        val eglConfigs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)

        if (EGL14.eglChooseConfig(
                eglDisplay,
                configAttributes,
                0,
                eglConfigs,
                0,
                1,
                numConfigs,
                0
            )
        ) {
            val contextAttributes = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val eglContext = EGL14.eglCreateContext(
                eglDisplay,
                eglConfigs[0],
                EGL14.EGL_NO_CONTEXT,
                contextAttributes,
                0
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

    private fun isAdreno740(): Boolean {
        return getGPUName()?.let {
            it.contains(
                "adreno",
                ignoreCase = true
            ) && it.contains("740")
        } == true
    }

    // ---- GL Version Spinner 辅助 ----
    private fun getGLVersionBySpinnerIndex(index: Int): Int {
        val selected =
            binding.spinnerCustomGlVersion.getItemAtPosition(index) as? String ?: return 0
        return glVersionMap[selected] ?: 0
    }

    private fun getSpinnerIndexByGLVersion(glVersion: Int): Int {
        val targetDisplay = glVersionMap.entries.firstOrNull { it.value == glVersion }?.key
            ?: getString(R.string.option_angle_disable)
        return glVersionMap.keys.indexOf(targetDisplay).coerceAtLeast(0)
    }

    private fun revertSpinner(spinner: Spinner, position: Int) {
        isSpinnerInitialized = false
        spinner.setSelection(position)
        spinner.post { isSpinnerInitialized = true }
    }

    // ---- 样式化消息 ----
    private fun getStyledMessage(@StringRes id: Int): Spanned {
        val errorColorHex = String.format(
            "#%06X",
            0xFFFFFF and MaterialColors.getColor(this, AppcompatR.attr.colorError, Color.RED)
        )
        return Html.fromHtml(
            getString(id).replace("@colorError", errorColorHex),
            Html.FROM_HTML_MODE_LEGACY
        )
    }

    // ---- Snackbar ----
    fun snackbar(text: CharSequence, duration: Int = Snackbar.LENGTH_SHORT) {
        Snackbar.make(binding.root, text, duration).show()
    }
}