package com.tencent.tinker.test

import android.os.Build
import com.tencent.tinker.internal.TinkerPatch
import com.tencent.tinker.test.internal.ParcelableTinkerPatch
import java.io.File
import java.nio.file.Files

internal val TinkerPatch.casted: ParcelableTinkerPatch
    get() = ParcelableTinkerPatch(version, directory)

internal fun createTestDirectory(): File =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Files.createTempDirectory("tinker-test-").toFile()
    } else {
        File.createTempFile("tinker-test-", "").apply {
            delete()
            mkdirs()
        }
    }