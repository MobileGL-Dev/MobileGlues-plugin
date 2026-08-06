package com.fcl.plugin.mobileglues

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import com.fcl.plugin.mobileglues.ui.AppController
import com.fcl.plugin.mobileglues.ui.AuthFlowLauncher
import com.fcl.plugin.mobileglues.ui.MobileGluesApp
import kotlin.system.exitProcess

/**
 * 壳：导航、页面容器、授权流程。全部界面逻辑在 [AppController]，全部绘制在两套皮肤里。
 *
 * Activity 只做两件别处做不了的事：持有 ActivityResult 启动器（系统授权页 /
 * SAF 选择器 / 运行时权限），以及在移除完成后退出进程。
 */
class MainActivity : ComponentActivity(), AuthFlowLauncher {

    private lateinit var controller: AppController

    private val manageAllFilesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            controller.onAllFilesResult()
        }

    private val safPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            controller.onSafResult(uri)
        }

    private val appSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // 从应用详情页回来：用户可能手动开了权限，重新核验一遍。
            controller.refreshAuthorization()
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            val permanentlyDenied = !isGranted &&
                !shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            controller.onLegacyPermissionResult(isGranted, permanentlyDenied)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.isNavigationBarContrastEnforced = false
        }

        controller = AppController(application as MGApplication, this, this)
        setContent { MobileGluesApp(controller) }
    }

    override fun onResume() {
        super.onResume()
        controller.refreshAuthorization()
    }

    override fun onStop() {
        super.onStop()
        // 用 store 自己的作用域，不能用界面层的作用域：旋转屏幕时后者会在 onDestroy 被取消，
        // 这次保存可能还没轮到 IO 线程就被砍掉。
        (application as MGApplication).configStore.flushAsync()
    }

    override fun onDestroy() {
        super.onDestroy()
        controller.destroy()
    }

    // ---- AuthFlowLauncher ----

    override fun openAllFilesSettings() {
        try {
            manageAllFilesLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    "package:$packageName".toUri(),
                ),
            )
        } catch (_: Exception) {
            manageAllFilesLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    override fun openSafPicker() {
        safPickerLauncher.launch(null)
    }

    override fun requestLegacyPermission() {
        requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    override fun openAppDetailsSettings() {
        appSettingsLauncher.launch(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
        )
    }

    override fun exitApp() {
        finishAffinity()
        exitProcess(0)
    }
}
