package com.tencent.tinker.internal

import android.content.Context
import android.os.Build
import java.io.File

internal val Context.rootDirectory: File
    get() {
        val name = if ("oppo" == Build.MANUFACTURER.lowercase() && Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP_MR1) {
            "wc_tinker_dir"
        } else {
            "tinker"
        }
        return applicationInfo.dataDir.let(::File).resolve(name)
    }