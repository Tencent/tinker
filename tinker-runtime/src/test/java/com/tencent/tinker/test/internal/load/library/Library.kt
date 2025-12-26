package com.tencent.tinker.test.internal.load.library

import android.os.Build
import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.load.library.LibraryLoader
import com.tencent.tinker.internal.patchLibraryDirectory
import com.tencent.tinker.internal.util.currentAbi
import com.tencent.tinker.test.internal.createTestPatchDirectoryWithMockFiles
import com.tencent.tinker.test.internal.testAbiList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import kotlin.collections.plus

@RunWith(RobolectricTestRunner::class)
class LibraryLoaderTest {

    private class TestClassLoaderV26(
        withNullNativeLibraryDirectories: Boolean = false,
    ) : ClassLoader() {
        private val pathList = DexPathList(withNullNativeLibraryDirectories)

        val nativeLibraryDirectoriesForTesting: List<String>
            get() = pathList.nativeLibraryDirectoriesForTesting

        val nativeLibraryPathElementsForTesting: List<String>
            get() = pathList.nativeLibraryPathElementsForTesting

        private class DexPathList(
            withNullNativeLibraryDirectories: Boolean,
        ) {

            private class NativeLibraryElement(val path: String)

            companion object {
                @JvmStatic
                fun makePathElements(
                    files: List<File>,
                ): Array<NativeLibraryElement> {
                    return files
                        .map { NativeLibraryElement(it.absolutePath) }
                        .toTypedArray()
                }
            }

            private val nativeLibraryDirectories: MutableList<File>? =
                if (withNullNativeLibraryDirectories) {
                    null
                } else {
                    mutableListOf(
                        File("/foo/bar"),
                        File("/baz"),
                    )
                }

            private val systemNativeLibraryDirectories = listOf(
                File("/system/lib/foo/bar"),
                File("/system/lib/baz"),
            )

            private val nativeLibraryPathElements = makePathElements(
                (nativeLibraryDirectories ?: emptyList()) + systemNativeLibraryDirectories
            )

            val nativeLibraryDirectoriesForTesting: List<String>
                get() = nativeLibraryDirectories
                    ?.map { it.absolutePath }
                    ?: emptyList()

            val nativeLibraryPathElementsForTesting: List<String>
                get() = nativeLibraryPathElements.map { it.path }
        }
    }

    /**
     * Tests if factor loader works expectedly on Android O.
     */
    @Test
    @Config(
        sdk = [Build.VERSION_CODES.O]
    )
    fun mockFactorLoaderAndLoadV26() {
        val directory = createTestPatchDirectoryWithMockFiles()
        val patch = Patch("foo", directory)
        val sourceClassLoader = TestClassLoaderV26()
        LibraryLoader
            .Factory(
                applicationClassLoader = sourceClassLoader,
                abiList = testAbiList,
            )
            .createLoaderIfNeeded(patch)
            .libraryLoadForTesting()
        assertEquals(
            buildList {
                testAbiList.forEach {
                    directory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
            },
            sourceClassLoader.nativeLibraryDirectoriesForTesting,
        )
        assertEquals(
            buildList {
                testAbiList.forEach {
                    directory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
                "/system/lib/foo/bar".let(::add)
                "/system/lib/baz".let(::add)
            },
            sourceClassLoader.nativeLibraryPathElementsForTesting,
        )
    }

    /**
     * Tests if factor loader works expectedly with null-source class loader on Android O.
     *
     * Null-source class loader means the source native library directories from class loader is
     * null.
     */
    @Test
    @Config(
        sdk = [Build.VERSION_CODES.O]
    )
    fun mockFactorLoaderAndLoadV26WithNullSource() {
        val directory = createTestPatchDirectoryWithMockFiles()
        val patch = Patch("foo", directory)
        val sourceClassLoader = TestClassLoaderV26(
            withNullNativeLibraryDirectories = true,
        )
        LibraryLoader
            .Factory(
                applicationClassLoader = sourceClassLoader,
                abiList = testAbiList,
            )
            .createLoaderIfNeeded(patch)
            .libraryLoadForTesting()
        assertEquals(
            testAbiList.map {
                directory.patchLibraryDirectory.resolve(it).absolutePath
            },
            sourceClassLoader.nativeLibraryDirectoriesForTesting,
        )
        assertEquals(
            buildList {
                testAbiList.forEach {
                    directory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/system/lib/foo/bar".let(::add)
                "/system/lib/baz".let(::add)
            },
            sourceClassLoader.nativeLibraryPathElementsForTesting,
        )
    }

    @Suppress("unused")
    private class TestClassLoaderV23(
        withNullNativeLibraryDirectories: Boolean = false,
    ) : ClassLoader() {
        private val pathList = DexPathList(withNullNativeLibraryDirectories)

        val nativeLibraryDirectoriesForTesting: List<String>
            get() = pathList.nativeLibraryDirectoriesForTesting

        val nativeLibraryPathElementsForTesting: List<String>
            get() = pathList.nativeLibraryPathElementsForTesting

        private class DexPathList(
            withNullNativeLibraryDirectories: Boolean
        ) {

            private class Element(val path: String)

            companion object {
                @JvmStatic
                fun makePathElements(
                    files: List<File>,
                    optimizedDirectory: File?,
                    suppressedExceptions: List<IOException>,
                ): Array<Element> {
                    return files
                        .map { Element(it.absolutePath) }
                        .toTypedArray()
                }
            }

            private val nativeLibraryDirectories: MutableList<File>? =
                if (withNullNativeLibraryDirectories) {
                    null
                } else {
                    mutableListOf(
                        File("/foo/bar"),
                        File("/baz")
                    )
                }

            private val systemNativeLibraryDirectories = listOf(
                File("/system/lib/foo/bar"),
                File("/system/lib/baz"),
            )

            private val nativeLibraryPathElements = makePathElements(
                (nativeLibraryDirectories ?: emptyList()) + systemNativeLibraryDirectories,
                null,
                mutableListOf(),
            )

            val nativeLibraryDirectoriesForTesting: List<String>
                get() = nativeLibraryDirectories
                    ?.map { it.absolutePath }
                    ?: emptyList()

            val nativeLibraryPathElementsForTesting: List<String>
                get() = nativeLibraryPathElements.map { it.path }
        }
    }

    /**
     * Tests if factor loader works expectedly on Android M.
     */
    @Test
    @Config(
        sdk = [Build.VERSION_CODES.M]
    )
    fun mockFactorLoaderAndLoadV23() {
        val directory = createTestPatchDirectoryWithMockFiles()
        val patch = Patch("foo", directory)
        val sourceClassLoader = TestClassLoaderV23()
        LibraryLoader
            .Factory(
                applicationClassLoader = sourceClassLoader,
                abiList = testAbiList,
            )
            .createLoaderIfNeeded(patch)
            .libraryLoadForTesting()
        assertEquals(
            buildList {
                testAbiList.forEach {
                    directory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
            },
            sourceClassLoader.nativeLibraryDirectoriesForTesting,
        )
        assertEquals(
            buildList {
                testAbiList.forEach {
                    directory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
                "/system/lib/foo/bar".let(::add)
                "/system/lib/baz".let(::add)
            },
            sourceClassLoader.nativeLibraryPathElementsForTesting,
        )
    }

    /**
     * Tests if factor loader works expectedly with null-source class loader on Android M.
     *
     * Null-source class loader means the source native library directories from class loader is
     * null.
     */
    @Test
    @Config(
        sdk = [Build.VERSION_CODES.M]
    )
    fun mockFactorLoaderAndLoadV23WithNullSource() {
        val directory = createTestPatchDirectoryWithMockFiles()
        val patch = Patch("foo", directory)
        val sourceClassLoader = TestClassLoaderV23(
            withNullNativeLibraryDirectories = true,
        )
        LibraryLoader
            .Factory(
                applicationClassLoader = sourceClassLoader,
                abiList = testAbiList,
            )
            .createLoaderIfNeeded(patch)
            .libraryLoadForTesting()
        assertEquals(
            testAbiList.map {
                directory.patchLibraryDirectory.resolve(it).absolutePath
            },
            sourceClassLoader.nativeLibraryDirectoriesForTesting,
        )
        assertEquals(
            buildList {
                testAbiList.forEach {
                    directory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/system/lib/foo/bar".let(::add)
                "/system/lib/baz".let(::add)
            },
            sourceClassLoader.nativeLibraryPathElementsForTesting,
        )
    }

    /**
     * Tests if factor loader works expectedly on Android O but using Android M class loader.
     *
     * Some manufacturers may still use Android M class loader.
     */
    @Test
    @Config(
        sdk = [Build.VERSION_CODES.O]
    )
    fun mockFactorLoaderAndLoadV26WithV23ClassLoader() {
        val directory = createTestPatchDirectoryWithMockFiles()
        val patch = Patch("foo", directory)
        val sourceClassLoader = TestClassLoaderV23()
        LibraryLoader
            .Factory(
                applicationClassLoader = sourceClassLoader,
                abiList = testAbiList,
            )
            .createLoaderIfNeeded(patch)
            .libraryLoadForTesting()
        assertEquals(
            buildList {
                testAbiList.forEach {
                    directory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
            },
            sourceClassLoader.nativeLibraryDirectoriesForTesting,
        )
        assertEquals(
            buildList {
                testAbiList.forEach {
                    directory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
                "/system/lib/foo/bar".let(::add)
                "/system/lib/baz".let(::add)
            },
            sourceClassLoader.nativeLibraryPathElementsForTesting,
        )
    }

    private class TestClassLoaderOld : ClassLoader() {
        private val pathList = DexPathList()

        val nativeLibraryDirectoriesForTesting: List<String>
            get() = pathList.nativeLibraryDirectoriesForTesting

        private class DexPathList {

            private val nativeLibraryDirectories = arrayOf(
                File("/foo/bar"),
                File("/baz")
            )

            val nativeLibraryDirectoriesForTesting: List<String>
                get() = nativeLibraryDirectories.map { it.absolutePath }
        }
    }

    /**
     * Tests if factor loader works expectedly on old Android versions.
     */
    @Test
    @Config(
        sdk = [Build.VERSION_CODES.LOLLIPOP]
    )
    fun mockFactorLoaderAndLoadOld() {
        val directory = createTestPatchDirectoryWithMockFiles()
        val patch = Patch("foo", directory)
        val sourceClassLoader = TestClassLoaderOld()
        LibraryLoader
            .Factory(
                applicationClassLoader = sourceClassLoader,
                abiList = testAbiList,
            )
            .createLoaderIfNeeded(patch)
            .libraryLoadForTesting()
        assertEquals(
            buildList {
                testAbiList.forEach {
                    directory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
            },
            sourceClassLoader.nativeLibraryDirectoriesForTesting,
        )
    }

    /**
     * Tests if factor loader without valid library directories can raise error expectedly.
     */
    @Test
    @Config(
        sdk = [Build.VERSION_CODES.O]
    )
    fun factorLoaderWithoutValidInputs() {
        val directory = createTestPatchDirectoryWithMockFiles()
            .apply {
                testAbiList.forEach {
                    patchLibraryDirectory.resolve(it).apply {
                        deleteRecursively()
                    }
                }
            }
        val patch = Patch("foo", directory)
        val sourceClassLoader = TestClassLoaderV26()
        val error = assertThrows(TinkerError::class.java) {
            LibraryLoader
                .Factory(
                    applicationClassLoader = sourceClassLoader,
                    abiList = emptyArray(),
                )
                .createLoaderIfNeeded(patch)
        }
        assertEquals(
            LibraryLoader.errorTypeOfForTesting("NO_VALID_INPUTS"),
            error.type,
        )
    }

    /**
     * Tests if factor loader with broken library directory can raise error expectedly.
     */
    @Test
    @Config(
        sdk = [Build.VERSION_CODES.O]
    )
    fun factorLoaderWithBrokenLibraryDirectory() {
        val directory = createTestPatchDirectoryWithMockFiles()
            .apply {
                patchLibraryDirectory.resolve(testAbiList[0]).apply {
                    deleteRecursively()
                    createNewFile()
                }
            }
        val patch = Patch("foo", directory)
        val sourceClassLoader = TestClassLoaderV26()
        val error = assertThrows(TinkerError::class.java) {
            LibraryLoader
                .Factory(
                    applicationClassLoader = sourceClassLoader,
                    abiList = testAbiList,
                )
                .createLoaderIfNeeded(patch)
        }
        assertEquals(
            LibraryLoader.errorTypeOfForTesting("INVALID_LIBRARY_DIRECTORY"),
            error.type,
        )
    }
}