package com.fcl.plugin.mobileglues.utils

import android.os.Environment

object Constants {
    val MG_DIRECTORY: String = "${Environment.getExternalStorageDirectory().absolutePath}/MG"

    val CONFIG_FILE_PATH: String = "$MG_DIRECTORY/config.json"

    val GLSL_CACHE_FILE_PATH: String = "$MG_DIRECTORY/glsl_cache.tmp"
}

