package com.tencent.tinker.internal.utils

import com.tencent.tinker.loader.shareutil.ShareTinkerLog

/**
 * TODO:
 *   Migrate implementation from `ShareTinkerLog`.
 *   @aurorani
 */

internal fun infoLog(tag: String, message: String) {
    ShareTinkerLog.i(tag, message)
}

internal fun warnLog(tag: String, message: String, throwable: Throwable? = null) {
    buildString {
        append(message)
        throwable?.let {
            append("\n")
            append(it.stackTraceToString())
        }
    }.let { ShareTinkerLog.w(tag, it) }
}

internal fun errorLog(tag: String, message: String, throwable: Throwable? = null) {
    buildString {
        append(message)
        throwable?.let {
            append("\n")
            append(it.stackTraceToString())
        }
    }.let { ShareTinkerLog.e(tag, it) }
}