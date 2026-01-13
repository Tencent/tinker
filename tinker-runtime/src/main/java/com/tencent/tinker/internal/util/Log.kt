package com.tencent.tinker.internal.util

import android.util.Log
import com.tencent.tinker.Tinker

private object LoggerImpl : Tinker.Logger() {
    override fun log(priority: Int, tag: String, message: String) {
        Log.println(priority, tag, message)
    }
}

internal var logger: Tinker.Logger = LoggerImpl

internal fun infoLog(tag: String, message: String) {
    logger.log(Log.INFO, tag, message)
}

internal fun warnLog(tag: String, message: String, throwable: Throwable? = null) {
    buildString {
        append(message)
        throwable?.let {
            append("\n")
            append(it.stackTraceToString())
        }
    }.let {
        logger.log(Log.WARN, tag, it)
    }
}

internal fun errorLog(tag: String, message: String, throwable: Throwable? = null) {
    buildString {
        append(message)
        throwable?.let {
            append("\n")
            append(it.stackTraceToString())
        }
    }.let {
        logger.log(Log.ERROR, tag, it)
    }
}