package com.tencent.tinker.internal

import android.content.Context
import android.os.Build
import java.io.File

internal val Context.rootDirectory: File
    get() {
        val name =
            if ("oppo" == Build.MANUFACTURER.lowercase() && Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP_MR1) {
                "wc_tinker_dir"
            } else {
                "tinker"
            }
        return applicationInfo.dataDir.let(::File).resolve(name)
    }

internal const val TEST_ASSETS_DIRECTORY_NAME = "tinker"

internal const val TEST_DEX_FILE_NAME = "test.dex"

internal const val TEST_JNI_LIBRARY_FILE_NAME = "libtinker.test.jni.so"

internal const val TEST_DEPENDENCY_LIBRARY_FILE_NAME = "libtinker.test.dep.so"

internal const val TEST_ADDED_ASSET_FILE_NAME = "test_added_asset.txt"

internal const val TEST_MODIFIED_ASSET_FILE_NAME = "test_modified_asset.txt"

internal const val TEST_REMOVED_ASSET_FILE_NAME = "test_removed_asset.txt"

internal val File.patchDexApkFile: File
    get() = resolve("dex.apk")

internal val File.patchDexDirectory: File
    get() = resolve("dex")

internal val File.patchLibraryDirectory: File
    get() = resolve("lib")

internal val File.patchResourceApkFile: File
    get() = resolve("resources.apk")

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
    val dexApkFile: File
        get() = directory.patchDexApkFile

    val dexDirectory: File
        get() = directory.patchDexDirectory

    val libraryDirectory: File
        get() = directory.patchLibraryDirectory

    val resourceApkFile: File
        get() = directory.patchResourceApkFile

    val oatDirectory: File
        get() = directory.patchOatDirectory
}