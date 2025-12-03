package com.tencent.tinker.utils

import android.content.Context
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals

val Context.isInMainProcess: Boolean
    get() = ShareTinkerInternals.isInMainProcess(this)

val Context.isInPatchProcess: Boolean
    get() = ShareTinkerInternals.isInPatchProcess(this)