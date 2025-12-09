@file:JvmName("TinkerPatchFileSystem")

package com.tencent.tinker.internal.module.fs

import com.tencent.tinker.internal.TinkerPatch
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

internal val File.patchOatDirectory: File
    get() = resolve("oat")

internal val TinkerPatch.oatDirectory: File
    get() = directory.patchOatDirectory