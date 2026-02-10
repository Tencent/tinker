package com.tencent.tinker.internal.util

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import java.util.zip.ZipEntry

/**
 * Returns a new [ZipEntry] with the same name as this entry.
 */
internal val ZipEntry.forked: ZipEntry
    get() = ZipEntry(name).apply {
        method = this@forked.method
        size = this@forked.size
        crc = this@forked.crc
    }

/**
 * Returns a new [ZipEntry] with the same name as this entry, but with the stored method.
 */
internal val ZipEntry.forkedStored: ZipEntry
    get() = ZipEntry(name).apply {
        method = ZipEntry.STORED
        size = this@forkedStored.size
        crc = this@forkedStored.crc
    }

@Suppress("UnusedReceiverParameter")
internal val Context.currentProcess: String
    @SuppressLint("PrivateApi")
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Application.getProcessName()
    } else {
        @Suppress("DiscouragedPrivateApi")
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

internal val Context.isInDeployProcess: Boolean
    // For compatible with legacy version of Tinker, the suffix of deploy process is still ":patch".
    // TODO: Move suffix to ":tinker.deploy".
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

internal val currentInstructionSet by lazy {
    try {
        @Suppress("DiscouragedPrivateApi")
        return@lazy Class.forName("dalvik.system.VMRuntime")
            .getDeclaredMethod("getCurrentInstructionSet")
            .apply {
                isAccessible = true
            }
            .invoke(null) as String
    } catch (throwable: Throwable) {
        warnLog("Tinker", throwable = throwable) {
            "Get \"currentInstructionSet\" failed, try to read CPU ABI directly."
        }
        @Suppress("DEPRECATION")
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
        warnLog("Tinker", throwable = throwable) {
            "Get \"arkHotRunning\" failed."
        }
        return@lazy false
    }
}

internal val Class<*>.className: String
    get() = name.substringAfterLast('.')