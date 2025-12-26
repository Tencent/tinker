package com.tencent.tinker.test

import android.content.Context
import android.os.Build
import com.tencent.tinker.internal.TEST_DEPENDENCY_LIBRARY_FILE_NAME
import com.tencent.tinker.internal.TEST_DEX_FILE_NAME
import com.tencent.tinker.internal.TEST_JNI_LIBRARY_FILE_NAME
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.patchDexDirectory
import com.tencent.tinker.internal.patchLibraryDirectory
import com.tencent.tinker.internal.util.currentInstructionSet
import com.tencent.tinker.internal.util.errorCode
import java.io.File
import java.nio.file.Files

internal fun createTestDirectory(): File =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Files.createTempDirectory("tinker-test-").toFile()
    } else {
        File.createTempFile("tinker-test-", "").apply {
            delete()
            mkdirs()
        }
    }

internal inline fun <T> rethrowAsIllegalState(action: () -> T) =
    try {
        action()
    } catch (error: TinkerError) {
        throw IllegalStateException("error#${error.type.errorCode}", error)
    }

private val rethrowMessagePattern = "error#(\\d+)".toRegex()

internal val IllegalStateException.tinkerErrorCode: Int
    get() = message
        ?.let(rethrowMessagePattern::matchEntire)
        ?.let { match ->
            match.groupValues[1].toInt()
        }
        ?: throw AssertionError("Exception is not tinker error as expected", this)

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

internal fun createMockTestPatchDirectory(): File =
    createTestDirectory()
        .apply {
            patchDexDirectory.apply {
                mkdirs()
                availableDexFileNamesAsSorted.forEach {
                    resolve(it).createNewFile()
                }
                resolve("not-dex.txt").createNewFile()
                resolve("fake-is-directory.dex").mkdirs()
            }
        }

internal fun Context.createLoadableTestPatchDirectory(): File =
    createTestDirectory()
        .apply {
            patchDexDirectory.apply {
                mkdirs()
                resolve(TEST_DEX_FILE_NAME).apply {
                    outputStream().use { outputStream ->
                        assets.open("tinker/${TEST_DEX_FILE_NAME}").use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            }
            patchLibraryDirectory.apply {
                mkdirs()
                Build.SUPPORTED_ABIS.forEach { abi ->
                    resolve(abi).apply {
                        mkdirs()
                        resolve(TEST_JNI_LIBRARY_FILE_NAME).apply {
                            outputStream().use { outputStream ->
                                assets.open("tinker/lib/${abi}/${TEST_JNI_LIBRARY_FILE_NAME}")
                                    .use { inputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                            }
                        }
                        resolve(TEST_DEPENDENCY_LIBRARY_FILE_NAME).apply {
                            outputStream().use { outputStream ->
                                assets.open("tinker/lib/${abi}/${TEST_DEPENDENCY_LIBRARY_FILE_NAME}")
                                    .use { inputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                            }
                        }
                    }
                }
            }
        }