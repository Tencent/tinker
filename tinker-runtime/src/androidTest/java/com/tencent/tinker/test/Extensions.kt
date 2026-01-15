package com.tencent.tinker.test

import android.content.Context
import android.os.Build
import android.system.Os
import android.system.OsConstants
import com.tencent.tinker.Tinker
import com.tencent.tinker.Tinker.code
import com.tencent.tinker.internal.TEST_ADDED_ASSET_FILE_NAME
import com.tencent.tinker.internal.TEST_DEPENDENCY_LIBRARY_FILE_NAME
import com.tencent.tinker.internal.TEST_DEX_FILE_NAME
import com.tencent.tinker.internal.TEST_JNI_LIBRARY_FILE_NAME
import com.tencent.tinker.internal.TEST_MODIFIED_ASSET_FILE_NAME
import com.tencent.tinker.internal.patchDexApkFile
import com.tencent.tinker.internal.patchDexDirectory
import com.tencent.tinker.internal.patchLibraryDirectory
import com.tencent.tinker.internal.patchResourceApkFile
import com.tencent.tinker.ziputils.ziputil.AlignedZipOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
    } catch (error: Tinker.Error) {
        throw IllegalStateException("error#${error.type.code.toString(16)}#${error.message}", error)
    }

private val rethrowMessagePattern = "error#([0-9a-f]+)#.*".toRegex()

internal val IllegalStateException.tinkerErrorCode: Int
    get() = message
        ?.let(rethrowMessagePattern::matchEntire)
        ?.let { match ->
            match.groupValues[1].toInt(16)
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

internal enum class DexMockMode {
    DEX,
    APK,
}

internal fun createMockTestPatchDirectory(dexMockMode: DexMockMode): File =
    createTestDirectory()
        .apply {
            if (dexMockMode == DexMockMode.DEX) {
                patchDexDirectory.apply {
                    mkdirs()
                    availableDexFileNamesAsSorted.forEach {
                        resolve(it).createNewFile()
                    }
                    resolve("not-dex.txt").createNewFile()
                    resolve("fake-is-directory.dex").mkdirs()
                }
            } else if (dexMockMode == DexMockMode.APK) {
                patchDexApkFile.createNewFile()
            }
            patchLibraryDirectory.apply {
                mkdirs()
                Build.SUPPORTED_ABIS.forEach {
                    resolve(it).mkdirs()
                }
            }
        }

internal fun Context.createLoadableTestPatchDirectory(dexMockMode: DexMockMode): File =
    createTestDirectory()
        .apply {
            if (dexMockMode == DexMockMode.DEX) {
                patchDexDirectory.apply {
                    mkdirs()
                    resolve(TEST_DEX_FILE_NAME).apply {
                        outputStream().use { output ->
                            assets.open("tinker/${TEST_DEX_FILE_NAME}").use { input ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            } else if (dexMockMode == DexMockMode.APK) {
                patchDexApkFile.apply {
                    outputStream().let(::AlignedZipOutputStream).use { zip ->
                        val payload = assets.open("tinker/${TEST_DEX_FILE_NAME}").use { input ->
                            input.readBytes()
                        }
                        val entry = ZipEntry("classes.dex")
                            .apply {
                                method = ZipEntry.STORED
                                size = payload.size.toLong()
                                crc = CRC32().apply { update(payload) }.value
                            }
                        zip.putNextEntry(entry)
                        zip.write(payload)
                        zip.closeEntry()
                    }
                }
            }
            patchLibraryDirectory.apply {
                mkdirs()
                Build.SUPPORTED_ABIS.forEach { abi ->
                    resolve(abi).apply {
                        mkdirs()
                        resolve(TEST_JNI_LIBRARY_FILE_NAME).apply {
                            outputStream().use { output ->
                                assets.open("tinker/lib/${abi}/${TEST_JNI_LIBRARY_FILE_NAME}")
                                    .use { input ->
                                        input.copyTo(output)
                                    }
                            }
                        }
                        resolve(TEST_DEPENDENCY_LIBRARY_FILE_NAME).apply {
                            outputStream().use { output ->
                                assets.open("tinker/lib/${abi}/${TEST_DEPENDENCY_LIBRARY_FILE_NAME}")
                                    .use { input ->
                                        input.copyTo(output)
                                    }
                            }
                        }
                    }
                }
            }
            patchResourceApkFile.apply {
                ZipOutputStream(outputStream()).use { zip ->
                    zip.putNextEntry(ZipEntry("assets/${TEST_ADDED_ASSET_FILE_NAME}"))
                    zip.write("patched".toByteArray())
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("assets/${TEST_MODIFIED_ASSET_FILE_NAME}"))
                    zip.write("patched".toByteArray())
                    zip.closeEntry()
                }
            }
        }

internal val File.isSymbolicLink: Boolean
    get() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Files.isSymbolicLink(toPath())
        } else {
            val state = Os.lstat(absolutePath)
            return state.st_mode and OsConstants.S_IFMT == OsConstants.S_IFLNK
        }
    }