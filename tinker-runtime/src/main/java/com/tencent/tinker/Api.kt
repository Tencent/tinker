package com.tencent.tinker

import android.util.Log

abstract class TinkerLogger {
    abstract fun log(
        priority: Int,
        tag: String,
        message: String,
    )
}