package com.tencent.tinker.internal.util

import android.util.Log
import com.tencent.tinker.Tinker

private object LoggerImpl : Tinker.Logger() {

    override fun log(priority: Int, tag: String, message: String) {
        Log.println(priority, tag, message)
    }
}

@Volatile
internal var globalLogger: Tinker.Logger = LoggerImpl

private inline fun log(level: Int, tag: String, throwable: Throwable?, message: () -> String) {
    if (globalLogger.filterLogLevel() <= level) {
        buildString {
            append(message())
            throwable?.let {
                append("\n")
                append(it.stackTraceToString())
            }
        }.let {
            globalLogger.log(level, tag, it)
        }
    }
}

internal inline fun debugLog(tag: String, message: () -> String) {
    log(
        level = Log.DEBUG,
        tag = tag,
        throwable = null,
        message = message,
    )
}

internal inline fun infoLog(tag: String, message: () -> String) {
    log(
        level = Log.INFO,
        tag = tag,
        throwable = null,
        message = message,
    )
}

internal fun warnLog(tag: String, throwable: Throwable? = null, message: () -> String) {
    log(
        level = Log.WARN,
        tag = tag,
        throwable = throwable,
        message = message,
    )
}

internal fun errorLog(tag: String, throwable: Throwable? = null, message: () -> String) {
    log(
        level = Log.ERROR,
        tag = tag,
        throwable = throwable,
        message = message,
    )
}