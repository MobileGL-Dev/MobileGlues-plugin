package com.fcl.plugin.mobileglues

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.widget.TextView
import androidx.core.net.toUri
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

@SuppressLint("InflateParams")
fun showAppInfoDialog(context: Context, onShowGlInfo: () -> Unit) {
    val view = LayoutInflater.from(context).inflate(R.layout.dialog_app_info, null, false)
    view.findViewById<TextView>(R.id.info_version).text = BuildConfig.VERSION_NAME

    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.dialog_info)
        .setView(view)
        .setNeutralButton(R.string.dialog_positive, null)
        .setNegativeButton(R.string.dialog_sponsor) { _, _ ->
            context.startActivity(
                Intent(Intent.ACTION_VIEW, "https://www.buymeacoffee.com/Swung0x48".toUri())
            )
        }
        .setPositiveButton(R.string.dialog_github) { _, _ ->
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "https://github.com/MobileGL-Dev/MobileGlues-release".toUri()
                )
            )
        }
        .show()
        .let { dialog ->
            view.findViewById<MaterialButton>(R.id.button_gl_info)?.setOnClickListener {
                onShowGlInfo()
                dialog.dismiss()
            }
        }
}
