package com.fcl.plugin.mobileglues.utils

import android.os.Environment

object Constants {
    const val CONFIG_FILE_NAME: String = "config.json"

    val MG_DIRECTORY: String = "${Environment.getExternalStorageDirectory().absolutePath}/MG"

    val CONFIG_FILE_PATH: String = "$MG_DIRECTORY/$CONFIG_FILE_NAME"

    val GLSL_CACHE_FILE_PATH: String = "$MG_DIRECTORY/glsl_cache.tmp"
}
