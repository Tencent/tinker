package com.tencent.tinker.test.internal

import android.util.Log
import com.tencent.tinker.TinkerLogger
import com.tencent.tinker.internal.TEST_DEPENDENCY_LIBRARY_FILE_NAME
import com.tencent.tinker.internal.TEST_DEX_FILE_NAME
import com.tencent.tinker.internal.TEST_JNI_LIBRARY_FILE_NAME
import com.tencent.tinker.internal.patchDexDirectory
import com.tencent.tinker.internal.patchLibraryDirectory
import com.tencent.tinker.internal.util.logger
import java.io.File
import java.nio.file.Files

internal val availableDexFileNamesAsSorted: List<String> =
    listOf(
        "classes.dex",
        "classes2.dex",
        "classes4.dex",
        "classes10.dex",
        "alpha.dex",
        "beta.dex",
        TEST_DEX_FILE_NAME,
    )

internal val testAbiList = arrayOf("arm64-v8a", "mock-new-abi")

internal fun createTestPatchDirectoryWithMockFiles(): File =
    Files.createTempDirectory("tinker-test-").toFile()
        .apply {
            patchDexDirectory.apply {
                mkdirs()
                availableDexFileNamesAsSorted.forEach {
                    resolve(it).createNewFile()
                }
                resolve("not-dex.txt").createNewFile()
                resolve("fake-is-directory.dex").mkdirs()
            }
            patchLibraryDirectory.apply {
                mkdirs()
                testAbiList.forEach {
                    resolve(it).apply {
                        mkdirs()
                    }
                }
            }
        }

private object TestLogger : TinkerLogger() {
    override fun log(priority: Int, tag: String, message: String) {
        if (priority >= Log.WARN) {
            System.err.println("[$tag] $message")
        } else {
            println("[$tag] $message")
        }
    }
}

internal fun configTestLogger() {
    logger = TestLogger
}