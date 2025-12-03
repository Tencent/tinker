@file:JvmName("TinkerPatchFileSystem")

package com.tencent.tinker.modules

import com.tencent.tinker.base.TinkerPatch
import java.io.File

internal val File.patchApkFile: File
    get() = resolve("patch.apk")

internal val TinkerPatch.apkFile: File
    get() = directory.patchApkFile

internal val File.patchDexDirectory: File
    get() = resolve("dex")

internal val TinkerPatch.dexDirectory: File
    get() = directory.patchDexDirectory

internal val File.patchLibraryDirectory: File
    get() = resolve("lib")

internal val TinkerPatch.libraryDirectory: File
    get() = directory.patchLibraryDirectory

internal val File.patchResourcesDirectory: File
    get() = resolve("res")

internal val TinkerPatch.resourcesDirectory: File
    get() = directory.patchResourcesDirectory