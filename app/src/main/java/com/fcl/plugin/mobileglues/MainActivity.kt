package com.fcl.plugin.mobileglues

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
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.text.Html
import android.text.Spanned
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.R as AppcompatR
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.fcl.plugin.mobileglues.databinding.ActivityMainBinding
import com.fcl.plugin.mobileglues.settings.FolderPermissionManager
import com.fcl.plugin.mobileglues.settings.MGConfig
import com.fcl.plugin.mobileglues.utils.Constants
import com.fcl.plugin.mobileglues.utils.FileUtils
import com.fcl.plugin.mobileglues.utils.toast
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.Types
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener,
    CompoundButton.OnCheckedChangeListener {

    private val glVersionMap: Map<String, Int> by lazy {
        linkedMapOf(
            getString(R.string.option_angle_disable) to 0,
            "OpenGL 4.6" to 46,
            "OpenGL 4.5" to 45,
            "OpenGL 4.4" to 44,
            "OpenGL 4.3" to 43,
            "OpenGL 4.2" to 42,
            "OpenGL 4.1" to 41,
            "OpenGL 4.0" to 40,
            "OpenGL 3.3" to 33,
            "OpenGL 3.2" to 32
        )
    }

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private var config: MGConfig? = null
    private lateinit var folderPermissionManager: FolderPermissionManager
    private var isSpinnerInitialized = false

    // 使用 Activity Result API 替代 onActivityResult
    private val safLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { treeUri ->
                    if (!folderPermissionManager.isUriMatchingFilePath(
                            treeUri,
                            File(Constants.MG_DIRECTORY)
                        )
                    ) {
                        MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.dialog_permission_path_error_title)
                            .setMessage(
                                getString(
                                    R.string.warning_path_selection_error,
                                    Constants.MG_DIRECTORY,
                                    folderPermissionManager.getFileByUri(treeUri)
                                )
                            )
                            .setPositiveButton(R.string.dialog_positive, null)
                            .show()
                        hideOptions()
                        return@let
                    }

                    contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )

                    MGDirectoryUri = treeUri
                    var currentConfig = MGConfig.loadConfig(this)
                    if (currentConfig == null) {
                        currentConfig = MGConfig(this)
                    }
                    currentConfig.save()
                    showOptions()
                } ?: hideOptions()
            }
        }

    private val appSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // 当从应用设置返回时，重新检查权限
            if (hasLegacyPermissions()) {
                MGConfig(this).save()
                showOptions()
            } else {
                // 如果用户仍然没有授予权限，可以再次显示请求或提示
                snackbar("授权失败")
            }
        }

    // 注册一个用于处理权限请求的 launcher
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // 情况1: 权限被授予
                // 用户同意了权限，执行你的操作
                MGConfig(this).save()
                showOptions()
            } else {
                // 情况2&3: 权限被拒绝
                // 检查用户是否勾选了“不再询问”
                if (!shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    // 情况2: 用户选择了 "不再询问"，引导他们到设置
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.dialog_permission_title)
                        .setMessage(R.string.dialog_permission_msg)
                        .setPositiveButton(R.string.dialog_positive) { _, _ ->
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", packageName, null)
                            )
                            // 使用另一个 launcher 来处理启动设置界面的结果
                            appSettingsLauncher.launch(intent)
                        }
                        .setNegativeButton(R.string.dialog_negative, null)
                        .show()

                } else {
                    // 情况3: 用户拒绝了，但没有勾选“不再询问”
                    // 你可以在这里给用户一个提示，然后再次请求权限
                    snackbar("授权失败")
                }
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.isNavigationBarContrastEnforced = false
        }

        setContentView(binding.root)
        folderPermissionManager = FolderPermissionManager(this)
        setSupportActionBar(binding.appBar)

        setupSpinners()

        binding.openOptions.setOnClickListener { checkPermission() }

        // 设置约束布局的底边距为系统导航栏的高度
        val optionLayoutParams = binding.optionLayout.layoutParams as ViewGroup.MarginLayoutParams
        window.decorView.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            optionLayoutParams.setMargins(
                0,
                0,
                0,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                ).bottom else insets.systemWindowInsetBottom
            )
            insets
        }
    }

    private fun setupSpinners() {
        // ANGLE 选项
        ArrayAdapter.createFromResource(
            this, R.array.angle_options, R.layout.spinner
        ).also { adapter ->
            adapter.setDropDownViewResource(R.layout.spinner)
            binding.spinnerAngle.adapter = adapter
        }

        // No Error 选项
        ArrayAdapter.createFromResource(
            this, R.array.no_error_options, R.layout.spinner
        ).also { adapter ->
            adapter.setDropDownViewResource(R.layout.spinner)
            binding.spinnerNoError.adapter = adapter
        }

        // Multidraw Mode 选项
        ArrayAdapter.createFromResource(
            this, R.array.multidraw_mode_options, R.layout.spinner
        ).also { adapter ->
            adapter.setDropDownViewResource(R.layout.spinner)
            binding.spinnerMultidrawMode.adapter = adapter
        }

        // GL Version 选项
        addCustomGLVersionOptions()

        // ANGLE Clear Workaround 选项
        ArrayAdapter.createFromResource(
            this, R.array.angle_clear_workaround_options, R.layout.spinner
        ).also { adapter ->
            adapter.setDropDownViewResource(R.layout.spinner)
            binding.angleClearWorkaround.adapter = adapter
        }
    }

    private fun hasMgDirectoryAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MGDirectoryUri != null
        } else {
            hasLegacyPermissions()
        }
    }

    private fun hasLegacyPermissions(): Boolean =
        ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        checkPermissionSilently()
        invalidateOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        menu.findItem(R.id.action_remove)?.isEnabled = hasMgDirectoryAccess()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                AppInfoDialogBuilder(this, config).showDialog()
                true
            }

            R.id.action_remove -> {
                showRemoveConfirmationDialog()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }


    private fun showRemoveConfirmationDialog() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.remove_mg_files_title)
            .setMessage(getStyledMessage(R.string.remove_mg_files_message))
            .setNegativeButton(R.string.dialog_negative, null)
            .setPositiveButton(getString(R.string.ok)) { _, _ -> removeMobileGluesCompletely() }
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            positiveButton.isEnabled = false
            val cooldownSeconds = 10

            object : CountDownTimer(cooldownSeconds * 1000L, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val remainingSeconds = (millisUntilFinished / 1000).toInt()
                    positiveButton.text = getString(R.string.ok_with_countdown, remainingSeconds)
                }

                override fun onFinish() {
                    positiveButton.text = getString(R.string.ok)
                    positiveButton.setTextColor(
                        MaterialColors.getColor(dialog.context, AppcompatR.attr.colorError, Color.RED)
                    )
                    positiveButton.isEnabled = true
                }
            }.start()
        }
        dialog.show()
    }

    private fun removeMobileGluesCompletely() {
        val view = LayoutInflater.from(this).inflate(R.layout.progress_dialog_md3, null)
        val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar)
        val progressText = view.findViewById<TextView>(R.id.progress_text)
        val progressDialog = MaterialAlertDialogBuilder(
            this
        )
            .setTitle(R.string.removing_mobileglues)
            .setView(view)
            .setCancelable(false)
            .create()

        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. 删除配置
                withContext(Dispatchers.Main) {
                    progressText.setText(R.string.deleting_config)
                    progressBar.progress = 20
                }
                config?.deleteConfig()

                // 2. 删除缓存
                withContext(Dispatchers.Main) {
                    progressText.setText(R.string.deleting_cache)
                    progressBar.progress = 40
                }
                deleteFileIfExists("glsl_cache.tmp")

                // 3. 删除日志
                withContext(Dispatchers.Main) {
                    progressText.setText(R.string.deleting_logs)
                    progressBar.progress = 60
                }
                deleteFileIfExists("latest.log")

                // 4. 清理目录
                withContext(Dispatchers.Main) {
                    progressText.setText(R.string.cleaning_directory)
                    progressBar.progress = 80
                }
                checkAndDeleteEmptyDirectory()

                // 5. 移除权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && MGDirectoryUri != null) {
                    withContext(Dispatchers.Main) {
                        progressText.setText(R.string.removing_permissions)
                        progressBar.progress = 100
                    }
                    releaseSafPermissions()
                }

                // 6. 操作完成
                withContext(Dispatchers.Main) {
                    resetApplicationState()
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
            .setPositiveButton(R.string.exit) { _, _ ->
                finishAffinity()
                exitProcess(0)
            }
            .show()
    }

    private fun deleteFileIfExists(fileName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MGDirectoryUri?.let { uri ->
                    DocumentFile.fromTreeUri(this, uri)?.findFile(fileName)?.let { file ->
                        if (file.exists()) DocumentsContract.deleteDocument(
                            contentResolver,
                            file.uri
                        )
                    }
                }
            } else {
                val file = File(Environment.getExternalStorageDirectory(), "MG/$fileName")
                if (file.exists()) {
                    FileUtils.deleteFile(file)
                }
            }
        } catch (e: Exception) {
            Log.w("MG", "删除文件失败: $fileName", e)
        }
    }

    private fun checkAndDeleteEmptyDirectory() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MGDirectoryUri?.let { uri ->
                    val dir = DocumentFile.fromTreeUri(this, uri)
                    if (dir != null && dir.listFiles().isEmpty()) {
                        DocumentsContract.deleteDocument(contentResolver, dir.uri)
                    }
                }
            } else {
                val mgDir = File(Environment.getExternalStorageDirectory(), "MG")
                if (mgDir.exists() && mgDir.isDirectory && mgDir.listFiles()?.isEmpty() == true) {
                    FileUtils.deleteFile(mgDir)
                }
            }
        } catch (e: Exception) {
            Log.w("MG", "删除目录失败", e)
        }
    }

    private fun releaseSafPermissions() {
        try {
            contentResolver.persistedUriPermissions
                .filter { it.uri == MGDirectoryUri }
                .forEach {
                    contentResolver.releasePersistableUriPermission(
                        it.uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
        } catch (e: Exception) {
            Log.w("MG", "移除 SAF 权限失败", e)
        }
    }

    private fun resetApplicationState() {
        MGDirectoryUri = null
        config = null
        folderPermissionManager = FolderPermissionManager(this) // Re-initialize
        hideOptions()
    }

    private fun showOptions() {
        isSpinnerInitialized = false // 禁用监听器以进行初始化
        setAllListeners(null) // 解绑所有监听器

        config = MGConfig.loadConfig(this) ?: MGConfig(this)

        config?.let { cfg ->
            // 规范化配置值
            if (cfg.enableANGLE !in 0..3) cfg.enableANGLE = 0
            if (cfg.enableNoError !in 0..3) cfg.enableNoError = 0
            if (cfg.maxGlslCacheSize == Types.NULL) cfg.maxGlslCacheSize = 32

            // 更新 UI
            binding.inputMaxGlslCacheSize.setText(cfg.maxGlslCacheSize.toString())
            binding.spinnerAngle.setSelection(cfg.enableANGLE)
            binding.spinnerNoError.setSelection(cfg.enableNoError)
            binding.spinnerMultidrawMode.setSelection(cfg.multidrawMode)
            binding.angleClearWorkaround.setSelection(cfg.angleDepthClearFixMode)
            binding.switchExtTimerQuery.isChecked = cfg.enableExtTimerQuery == 0
            binding.switchExtDirectStateAccess.isChecked = cfg.enableExtDirectStateAccess == 1
            binding.switchExtCs.isChecked = cfg.enableExtComputeShader == 1
            binding.switchEnableFsr1.isChecked = cfg.fsr1Setting == 1
            setCustomGLVersionSpinnerSelectionByGLVersion(cfg.customGLVersion)
        }

        setAllListeners(this) // 重新绑定所有监听器

        binding.inputMaxGlslCacheSize.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val text = s.toString().trim()
                if (text.isNotEmpty()) {
                    try {
                        val number = text.toInt()
                        if (number < -1 || number == 0) {
                            binding.inputMaxGlslCacheSizeLayout.error =
                                getString(R.string.option_glsl_cache_error_range)
                        } else {
                            binding.inputMaxGlslCacheSizeLayout.error = null
                            config?.maxGlslCacheSize = number
                        }
                    } catch (_: NumberFormatException) {
                        binding.inputMaxGlslCacheSizeLayout.error =
                            getString(R.string.option_glsl_cache_error_invalid)
                    }
                } else {
                    binding.inputMaxGlslCacheSizeLayout.error = null
                    config?.maxGlslCacheSize = 32 // 默认值
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.openOptions.visibility = View.GONE
        binding.scrollLayout.visibility = View.VISIBLE
        isSpinnerInitialized = true
    }

    private fun setAllListeners(listener: Any?) {
        val itemListener = listener as? AdapterView.OnItemSelectedListener
        val checkedListener = listener as? CompoundButton.OnCheckedChangeListener

        binding.spinnerAngle.onItemSelectedListener = itemListener
        binding.spinnerNoError.onItemSelectedListener = itemListener
        binding.spinnerMultidrawMode.onItemSelectedListener = itemListener
        binding.spinnerCustomGlVersion.onItemSelectedListener = itemListener
        binding.angleClearWorkaround.onItemSelectedListener = itemListener

        binding.switchExtCs.setOnCheckedChangeListener(checkedListener)
        binding.switchExtTimerQuery.setOnCheckedChangeListener(checkedListener)
        binding.switchExtDirectStateAccess.setOnCheckedChangeListener(checkedListener)
        binding.switchEnableFsr1.setOnCheckedChangeListener(checkedListener)
    }


    private fun hideOptions() {
        binding.openOptions.visibility = View.VISIBLE
        binding.scrollLayout.visibility = View.GONE
    }

    private fun checkPermissionSilently() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MGDirectoryUri = folderPermissionManager.getMGFolderUri()
            try {
                val documentFile = DocumentFile.fromTreeUri(this, MGDirectoryUri!!)
                if (documentFile?.exists() != true) {
                    MGDirectoryUri = null
                    hideOptions()
                    return
                }
                (MGConfig.loadConfig(this) ?: MGConfig(this)).save()
                showOptions()
            } catch (_: Exception) {
                // 文件夹被删除或权限失效
                MGDirectoryUri = null
                hideOptions()
            }
        } else {
            if (hasLegacyPermissions()) {
                showOptions()
            } else {
                hideOptions()
            }
        }
    }

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dialog_permission_title))
                .setMessage(
                    getString(
                        R.string.dialog_permission_msg_android_Q,
                        Constants.MG_DIRECTORY
                    )
                )
                .setPositiveButton(R.string.dialog_positive) { _, _ ->
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    // 1. 获取外部存储文档提供者的根 URI
                    val rootUri = DocumentsContract.buildTreeDocumentUri(
                        "com.android.externalstorage.documents",
                        "primary" // "primary" 通常代表内部共享存储
                    )

                    // 2. 在根 URI 的基础上构建指向 "MG" 文件夹的 Document-URI
                    // Document ID 的格式是 "root:path"
                    val folderDocumentId = "primary:MG"
                    val initialUri =
                        DocumentsContract.buildDocumentUriUsingTree(rootUri, folderDocumentId)

                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
                    safLauncher.launch(intent)
                }
                .setNegativeButton(R.string.dialog_negative) { dialog, _ -> dialog.dismiss() }
                .show()
        } else {
            if (hasLegacyPermissions()) {
                showOptions()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    override fun onItemSelected(adapterView: AdapterView<*>, view: View?, position: Int, id: Long) {
        if (!isSpinnerInitialized || config == null) return

        when (adapterView.id) {
            R.id.spinner_angle -> {
                val previous = config?.enableANGLE
                when {
                    position == previous -> return
                    position == 3 && isAdreno740() -> {
                        MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.dialog_title_warning))
                            .setMessage(getString(R.string.warning_adreno_740_angle))
                            .setPositiveButton(getString(R.string.dialog_positive)) { _, _ ->
                                config?.enableANGLE = position
                            }
                            .setNegativeButton(getString(R.string.dialog_negative)) { _, _ ->
                                isSpinnerInitialized = false
                                binding.spinnerAngle.setSelection(config!!.enableANGLE)
                                isSpinnerInitialized = true
                            }
                            .setCancelable(false)
                            .show()
                    }

                    else -> {
                        config?.enableANGLE = position
                    }
                }
            }

            R.id.spinner_no_error -> config?.enableNoError = position
            R.id.spinner_multidraw_mode -> config?.multidrawMode = position
            R.id.spinner_custom_gl_version -> handleCustomGLVersionSelection(position)
            R.id.angle_clear_workaround -> {
                val previous = config!!.angleDepthClearFixMode
                if (position == previous) return
                if (position >= 1) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.dialog_title_warning))
                        .setMessage(getString(R.string.warning_enabling_angle_clear_workaround))
                        .setPositiveButton(getString(R.string.dialog_positive)) { _, _ ->
                            config?.angleDepthClearFixMode = position
                        }
                        .setNegativeButton(getString(R.string.dialog_negative)) { _, _ ->
                            isSpinnerInitialized = false
                            binding.angleClearWorkaround.setSelection(config!!.angleDepthClearFixMode)
                            isSpinnerInitialized = true
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    config?.angleDepthClearFixMode = position
                }
            }
        }
    }

    private fun handleCustomGLVersionSelection(position: Int) {
        val previous = config!!.customGLVersion
        val newValue = getGLVersionBySpinnerIndex(position)
        if (newValue == previous) return

        if (previous == 0) {
            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dialog_title_warning))
                .setMessage(getStyledMessage(R.string.warning_enabling_custom_gl_version))
                .setNegativeButton(getString(R.string.dialog_negative)) { _, _ ->
                    isSpinnerInitialized = false
                    binding.spinnerCustomGlVersion.setSelection(getSpinnerIndexByGLVersion(previous))
                    isSpinnerInitialized = true
                }
                .setPositiveButton(getString(R.string.ok)) { _, _ ->
                    config?.customGLVersion = newValue
                }
                .setCancelable(false)
                .create()

            dialog.setOnShowListener {
                val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                positiveButton.isEnabled = false
                val cooldownSeconds = 41

                object : CountDownTimer(cooldownSeconds * 1000L, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val remainingSeconds = (millisUntilFinished / 1000).toInt()
                        positiveButton.text =
                            getString(R.string.ok_with_countdown, remainingSeconds)
                    }

                    override fun onFinish() {
                        positiveButton.text = getString(R.string.ok)
                        positiveButton.setTextColor(
                            MaterialColors.getColor(dialog.context, AppcompatR.attr.colorError, Color.RED)
                        )
                        positiveButton.isEnabled = true
                    }
                }.start()
            }
            dialog.show()
        } else {
            config?.customGLVersion = newValue
        }
    }


    override fun onNothingSelected(parent: AdapterView<*>) {}

    override fun onCheckedChanged(compoundButton: CompoundButton, isChecked: Boolean) {
        if (config == null) return

        when (compoundButton.id) {
            R.id.switch_ext_cs -> handleSwitchWithWarning(
                isChecked,
                R.string.warning_ext_cs_enable,
                { config?.enableExtComputeShader = 1 },
                { config?.enableExtComputeShader = 0 },
                compoundButton
            )

            R.id.switch_enable_fsr1 -> handleSwitchWithWarning(
                isChecked,
                R.string.warning_fsr1_enable,
                { config?.fsr1Setting = 1 },
                { config?.fsr1Setting = 0 },
                compoundButton
            )

            R.id.switch_ext_timer_query -> config?.enableExtTimerQuery =
                if (isChecked) 0 else 1 // UI (disable) -> JSON (enable)
            R.id.switch_ext_direct_state_access -> config?.enableExtDirectStateAccess =
                if (isChecked) 1 else 0
        }
    }

    private fun handleSwitchWithWarning(
        isChecked: Boolean,
        warningMsgRes: Int,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
        button: CompoundButton
    ) {
        if (isChecked) {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dialog_title_warning))
                .setMessage(getString(warningMsgRes))
                .setCancelable(false)
                .setOnKeyListener { dialog, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK }
                .setPositiveButton(getString(R.string.dialog_positive)) { _, _ -> onConfirm() }
                .setNegativeButton(getString(R.string.dialog_negative)) { _, _ ->
                    button.isChecked = false
                }
                .show()
        } else {
            onCancel()
        }
    }

    private fun getGPUName(): String? {
        val eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) return null

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) return null

        // 此处省略了 EGL 上下文创建的完整代码，因为它很长且与问题核心无关
        // 假设它能正确返回渲染器名称
        // ... (完整的 EGL 上下文创建和销毁代码)

        // 模拟一个简单的实现
        var renderer: String? = null
        val configAttributes =
            intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE)
        val eglConfigs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(
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
            EGL14.eglTerminate(eglDisplay)
            return null
        }
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
        EGL14.eglTerminate(eglDisplay)
        return renderer
    }

    private fun isAdreno740(): Boolean {
        val renderer = getGPUName()
        return renderer?.lowercase()?.contains("adreno") == true && renderer.contains("740")
    }

    private fun addCustomGLVersionOptions() {
        val glVersionOptions = ArrayList(glVersionMap.keys)
        val adapter = ArrayAdapter(this, R.layout.spinner, glVersionOptions)
        binding.spinnerCustomGlVersion.adapter = adapter
    }

    private fun setCustomGLVersionSpinnerSelectionByGLVersion(glVersion: Int) {
        val targetDisplay = glVersionMap.entries
            .firstOrNull { it.value == glVersion }
            ?.key ?: getString(R.string.option_angle_disable)

        val items = glVersionMap.keys.toList()
        val position = items.indexOf(targetDisplay).coerceAtLeast(0)
        binding.spinnerCustomGlVersion.setSelection(position)
    }

    private fun getGLVersionBySpinnerIndex(index: Int): Int {
        val selected = binding.spinnerCustomGlVersion.getItemAtPosition(index) as String
        return glVersionMap[selected] ?: 0
    }

    private fun getSpinnerIndexByGLVersion(glVersion: Int): Int {
        val targetDisplay = glVersionMap.entries
            .firstOrNull { it.value == glVersion }
            ?.key ?: getString(R.string.option_angle_disable)
        return glVersionMap.keys.indexOf(targetDisplay)
    }

    private fun getStyledMessage(@StringRes id: Int): Spanned {
        val errorColor = MaterialColors.getColor(this, AppcompatR.attr.colorError, Color.RED)
        val messageText = getString(id)
    
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(messageText.replace(
                "@colorError",
                String.format("#%06X", 0xFFFFFF and errorColor)
            ), Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(messageText.replace(
                "@colorError",
                String.format("#%06X", 0xFFFFFF and errorColor)
            ))
        }
    }

    fun snackbar(text: CharSequence, duration: Int = Snackbar.LENGTH_SHORT) {
        Snackbar.make(binding.root, text, duration).show()
    }

    companion object {
        var MGDirectoryUri: Uri? = null
    }
}