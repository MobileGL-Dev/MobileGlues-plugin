package com.capslock800000.optimizedmg

import android.content.Context
import android.widget.TextView
import com.capslock800000.optimizedmg.settings.MGConfig
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object MGInfoGetter {
    init {
        System.loadLibrary("optimizedmg_info_getter")
    }

    external fun setenv(key: String, value: String, overwrite: Int): Int

    external fun getOptimizedMGGLInfo(): String

    val mgGLInfo: String
        get() = try {
            setenv("MG_PLUGIN_STATUS", 1.toString(), 1)
            setenv("MG_DIR_PATH", MGConfig.cacheMGDir.path, 1)
            getOptimizedMGGLInfo()
        } catch (e: Throwable) {
            "Error: ${e.message}"
        }
}

fun showMGGLInfoDialog(context: Context, config: MGConfig?) {
    config?.saveToCachePath()
    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.dialog_mg_gl_info_title)
        .setMessage(MGInfoGetter.mgGLInfo)
        .setNegativeButton(R.string.dismiss, null)
        .show()
        .let { dialog ->
            dialog.findViewById<TextView>(android.R.id.message)?.setTextIsSelectable(true)
        }
}