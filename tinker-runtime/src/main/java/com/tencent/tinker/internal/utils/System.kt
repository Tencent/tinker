package com.tencent.tinker.internal.utils

import android.app.Application
import android.content.Context
import android.os.Build

private const val TAG = "Tinker.Utils.System"

@Suppress("UnusedReceiverParameter", "PrivateApi")
internal val Context.currentProcess: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Application.getProcessName()
    } else {
        Class.forName("android.app.ActivityThread")
            .getDeclaredMethod("currentProcessName")
            .apply {
                isAccessible = true
            }
            .invoke(null) as String?
            ?: ""
    }

internal val Context.isInMainProcess: Boolean
    get() = (applicationInfo.processName ?: packageName) == currentProcess

internal val Context.isInPatchProcess: Boolean
    get() = currentProcess.endsWith(":patch")

internal data class CurrentSdk(
    private val stable: Int,
    private val preview: Int
) {
    operator fun compareTo(value: Int): Int {
        if (stable == value) {
            return 0
        }
        val level = if (preview > 0) stable + 1 else stable
        return level - value
    }
}

internal val currentSdk by lazy {
    CurrentSdk(
        stable = Build.VERSION.SDK_INT,
        preview = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Build.VERSION.PREVIEW_SDK_INT
        } else {
            0
        }
    )
}

@Suppress("DEPRECATION")
internal val currentInstructionSet by lazy {
    try {
        return@lazy Class.forName("dalvik.system.VMRuntime")
            .getDeclaredMethod("getCurrentInstructionSet")
            .apply {
                isAccessible = true
            }
            .invoke(null) as String
    } catch (throwable: Throwable) {
        warnLog(
            TAG,
            "Get \"currentInstructionSet\" failed, try to read CPU ABI directly.",
            throwable,
        )
        return@lazy when (Build.CPU_ABI) {
            "armeabi", "armeabi-v7a" -> "arm"
            "arm64-v8a" -> "arm64"
            "x86" -> "x86"
            "x86_64" -> "x86_64"
            "mips" -> "mips"
            "mips64" -> "mips64"
            else -> throw IllegalStateException("Unsupported abi: " + Build.CPU_ABI)
        }
    }
}

internal val arkHotRunning by lazy {
    try {
        return@lazy ClassLoader.getSystemClassLoader()
            .parent
            .loadClass("com.huawei.ark.app.ArkApplicationInfo")
            .getDeclaredMethod("isRunningInArk")
            .apply {
                isAccessible = true
            }
            .invoke(null) as Boolean
    } catch (throwable: Throwable) {
        warnLog(
            TAG,
            "Get \"arkHotRunning\" failed.",
            throwable,
        )
        return@lazy false
    }
}