package com.fcl.plugin.mobileglues

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri
import com.fcl.plugin.mobileglues.settings.MGConfig
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

@SuppressLint("InflateParams")
class MGGLInfoDialogBuilder(context: Context, private val config: MGConfig?) : MaterialAlertDialogBuilder(context) {
    private val view: View =
        LayoutInflater.from(context).inflate(R.layout.dialog_mg_gl_info, null, false)

    init {
        setTitle(R.string.dialog_mg_gl_info_title)
        setView(view)
        setNegativeButton(R.string.dismiss, null)
    }

    fun fillMobileGluesInfo() {
        System.loadLibrary("mobileglues_info_getter")
        view.findViewById<TextView>(R.id.info_mg_gl_info_all).text =
            context.getString(R.string.loading)

        prepareEnv()
        val mgGLInfo = try {
            getMobileGluesGLInfo()
        } catch (e: Throwable) {
            "Error: ${e.message}"
        }
        view.findViewById<TextView>(R.id.info_mg_gl_info_all).text = mgGLInfo
    }

    fun prepareEnv() {
        config?.saveToCachePath()
        setenv("MG_PLUGIN_STATUS", 1.toString(), 1)
        setenv("MG_DIR_PATH", MGConfig.cacheMGDir.path, 1)
    }

    external fun setenv(key: String, value: String, overwrite: Int): Int

    external fun getMobileGluesGLInfo(): String
}
