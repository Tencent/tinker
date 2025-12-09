package com.tencent.tinker.test

import android.os.Build
import com.tencent.tinker.internal.module.patch.RawPatch
import com.tencent.tinker.test.internal.module.patch.ParcelableRawPatch
import java.io.File
import java.nio.file.Files

internal val RawPatch.casted: ParcelableRawPatch
    get() = ParcelableRawPatch(version, directory)

internal fun createTestDirectory(): File =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Files.createTempDirectory("tinker-test-").toFile()
    } else {
        File.createTempFile("tinker-test-", "").apply {
            delete()
            mkdirs()
        }
    }