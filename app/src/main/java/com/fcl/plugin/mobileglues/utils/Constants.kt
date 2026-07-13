package com.capslock800000.optimizedmg.utils

import android.os.Environment

object Constants {
    val MG_DIRECTORY: String = "${Environment.getExternalStorageDirectory().absolutePath}/OMG"

    val CONFIG_FILE_PATH: String = "$MG_DIRECTORY/config.json"

    val GLSL_CACHE_FILE_PATH: String = "$MG_DIRECTORY/glsl_cache.tmp"
}

