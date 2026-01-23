package com.tencent.tinker.internal.legacy.loader.shareutil

import com.tencent.tinker.internal.util.errorLog
import com.tencent.tinker.internal.util.warnLog

internal object ShareTinkerLog {
    @JvmStatic
    @JvmOverloads
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        warnLog(tag, throwable = throwable) { message }
    }

    @JvmStatic
    fun e(tag: String, message: String) {
        errorLog(tag) { message }
    }
}