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

internal val File.patchApkFile: File
    get() = resolve("patch.apk")

internal val File.patchDexDirectory: File
    get() = resolve("dex")

internal val File.patchTestDexFile: File
    get() = resolve("test.dex")

internal val File.patchLibraryDirectory: File
    get() = resolve("lib")

internal val File.patchResourceDirectory: File
    get() = resolve("res")

internal val File.patchOatDirectory: File
    get() = resolve("oat")

/**
 * Information about a patch.
 *
 * Attention: all content should be treated as unavailable if current process already requested it
 * as unavailable.
 */
internal class Patch(
    /**
     * Version of this patch.
     */
    val version: String,

    /**
     * Base directory of this patch. This directory and all its children are non-writable.
     *
     * The directory may be cleaned if current process already requested patch as unavailable.
     */
    private val directory: File,
) {
    val apkFile: File
        get() = directory.patchApkFile

    val testDexFile: File
        get() = directory.patchTestDexFile

    val dexDirectory: File
        get() = directory.patchDexDirectory


    val libraryDirectory: File
        get() = directory.patchLibraryDirectory


    val resourceDirectory: File
        get() = directory.patchResourceDirectory


    val oatDirectory: File
        get() = directory.patchOatDirectory
}