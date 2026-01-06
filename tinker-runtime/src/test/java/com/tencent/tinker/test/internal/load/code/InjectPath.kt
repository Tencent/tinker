package com.tencent.tinker.test.internal.load.code

import android.os.Build
import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.load.code.InjectPathCodeLoader
import com.tencent.tinker.internal.patchDexApkFile
import com.tencent.tinker.internal.patchDexDirectory
import com.tencent.tinker.internal.patchLibraryDirectory
import com.tencent.tinker.test.internal.DexMockMode
import com.tencent.tinker.test.internal.availableDexFileNamesAsSorted
import com.tencent.tinker.test.internal.configTestLogger
import com.tencent.tinker.test.internal.createTestPatchDirectoryWithMockFiles
import com.tencent.tinker.test.internal.testAbiList
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class InjectPathDexLoaderTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun setUp() {
            configTestLogger()
        }
    }

    /**
     * Tests if factor loader works expectedly with dex files as input for Android O to latest Android version.
     */
    @Test
    @Config(
        sdk = [
            Build.VERSION_CODES.O,
            Build.VERSION_CODES.O_MR1,
            Build.VERSION_CODES.P,
            Build.VERSION_CODES.Q,
            Build.VERSION_CODES.R,
            Build.VERSION_CODES.S,
            Build.VERSION_CODES.S_V2,
        ]
    )
    fun factorLoaderAndLoadWithDexFilesV26() {
        val outputDirectory = Files.createTempDirectory("tinker-test-").toFile()
        val patchDirectory = createTestPatchDirectoryWithMockFiles(
            dexMockMode = DexMockMode.DEX,
        )
        val patch = Patch("foo", patchDirectory)
        val source = TestClassLoaderV26()
        InjectPathCodeLoader
            .Factory(
                source = source,
                outputDirectory = outputDirectory,
                abiList = testAbiList,
            )
            .createLoaderIfNeeded(patch)
            .apply {
                doLoadForTesting()
            }
        assertEquals(
            buildList {
                availableDexFileNamesAsSorted
                    .map(patchDirectory.patchDexDirectory::resolve)
                    .map { it.absolutePath }
                    .let(::addAll)
                "foo".let(::add)
                "bar".let(::add)
            },
            source.dexElementsForTesting,
        )
        assertEquals(
            buildList {
                testAbiList.forEach {
                    patchDirectory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
            },
            source.nativeLibraryDirectoriesForTesting,
        )
        assertEquals(
            buildList {
                testAbiList.forEach {
                    patchDirectory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
                "/system/lib/foo/bar".let(::add)
                "/system/lib/baz".let(::add)
            },
            source.nativeLibraryPathElementsForTesting,
        )
    }

    /**
     * Tests if factor loader works expectedly with apk file as input for Android O to latest Android version.
     */
    @Test
    @Config(
        sdk = [
            Build.VERSION_CODES.O,
            Build.VERSION_CODES.O_MR1,
            Build.VERSION_CODES.P,
            Build.VERSION_CODES.Q,
            Build.VERSION_CODES.R,
            Build.VERSION_CODES.S,
            Build.VERSION_CODES.S_V2,
        ]
    )
    fun factorLoaderAndLoadWithApkFileV26() {
        val outputDirectory = Files.createTempDirectory("tinker-test-").toFile()
        val patchDirectory = createTestPatchDirectoryWithMockFiles(
            dexMockMode = DexMockMode.APK,
        )
        val patch = Patch("foo", patchDirectory)
        val source = TestClassLoaderV26()
        InjectPathCodeLoader
            .Factory(
                source = source,
                outputDirectory = outputDirectory,
                abiList = testAbiList,
            )
            .createLoaderIfNeeded(patch)
            .apply {
                doLoadForTesting()
            }
        assertEquals(
            buildList {
                patchDirectory.patchDexApkFile.absolutePath.let(::add)
                "foo".let(::add)
                "bar".let(::add)
            },
            source.dexElementsForTesting,
        )
        assertEquals(
            buildList {
                testAbiList.forEach {
                    patchDirectory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
            },
            source.nativeLibraryDirectoriesForTesting,
        )
        assertEquals(
            buildList {
                testAbiList.forEach {
                    patchDirectory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
                "/system/lib/foo/bar".let(::add)
                "/system/lib/baz".let(::add)
            },
            source.nativeLibraryPathElementsForTesting,
        )
    }

    /**
     * Tests if factor loader works expectedly with dex files as input for Android N until Android O.
     */
    @Test
    @Config(
        sdk = [
            Build.VERSION_CODES.N,
            Build.VERSION_CODES.N_MR1,
        ]
    )
    fun factorLoaderAndLoadWithDexFilesV24() {
        val outputDirectory = Files.createTempDirectory("tinker-test-").toFile()
        val patchDirectory = createTestPatchDirectoryWithMockFiles(
            dexMockMode = DexMockMode.DEX,
        )
        val patch = Patch("foo", patchDirectory)
        val source = TestClassLoaderV24()
        InjectPathCodeLoader
            .Factory(
                source = source,
                outputDirectory = outputDirectory,
                abiList = testAbiList,
            )
            .createLoaderIfNeeded(patch)
            .apply {
                doLoadForTesting()
            }
        assertEquals(
            buildList {
                availableDexFileNamesAsSorted
                    .map(patchDirectory.patchDexDirectory::resolve)
                    .map { it.absolutePath }
                    .let(::addAll)
                "foo".let(::add)
                "bar".let(::add)
            },
            source.dexElementsForTesting,
        )
        assertEquals(
            buildList {
                testAbiList.forEach {
                    patchDirectory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
            },
            source.nativeLibraryDirectoriesForTesting,
        )
        assertEquals(
            buildList {
                testAbiList.forEach {
                    patchDirectory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
                "/system/lib/foo/bar".let(::add)
                "/system/lib/baz".let(::add)
            },
            source.nativeLibraryPathElementsForTesting,
        )
    }

    /**
     * Tests if factor loader works expectedly with apk file as input for Android N until Android O.
     */
    @Test
    @Config(
        sdk = [
            Build.VERSION_CODES.N,
            Build.VERSION_CODES.N_MR1,
        ]
    )
    fun factorLoaderAndLoadWithApkFileV24() {
        val outputDirectory = Files.createTempDirectory("tinker-test-").toFile()
        val patchDirectory = createTestPatchDirectoryWithMockFiles(
            dexMockMode = DexMockMode.APK,
        )
        val patch = Patch("foo", patchDirectory)
        val source = TestClassLoaderV24()
        InjectPathCodeLoader
            .Factory(
                source = source,
                outputDirectory = outputDirectory,
                abiList = testAbiList,
            )
            .createLoaderIfNeeded(patch)
            .apply {
                doLoadForTesting()
            }
        assertEquals(
            buildList {
                patchDirectory.patchDexApkFile.absolutePath.let(::add)
                "foo".let(::add)
                "bar".let(::add)
            },
            source.dexElementsForTesting,
        )
        assertEquals(
            buildList {
                testAbiList.forEach {
                    patchDirectory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
            },
            source.nativeLibraryDirectoriesForTesting,
        )
        assertEquals(
            buildList {
                testAbiList.forEach {
                    patchDirectory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
                "/system/lib/foo/bar".let(::add)
                "/system/lib/baz".let(::add)
            },
            source.nativeLibraryPathElementsForTesting,
        )
    }

    /**
     * Tests if factor loader works expectedly with dex files as input for Android M until Android N.
     */
    @Test
    @Config(
        sdk = [
            Build.VERSION_CODES.M,
        ]
    )
    fun factorLoaderAndLoadWithDexFilesV23() {
        val outputDirectory = Files.createTempDirectory("tinker-test-").toFile()
        val patchDirectory = createTestPatchDirectoryWithMockFiles(
            dexMockMode = DexMockMode.DEX,
        )
        val patch = Patch("foo", patchDirectory)
        val source = TestClassLoaderV23()
        InjectPathCodeLoader
            .Factory(
                source = source,
                outputDirectory = outputDirectory,
                abiList = testAbiList,
            )
            .createLoaderIfNeeded(patch)
            .apply {
                doLoadForTesting()
            }
        assertEquals(
            buildList {
                availableDexFileNamesAsSorted
                    .map(patchDirectory.patchDexDirectory::resolve)
                    .map { it.absolutePath }
                    .let(::addAll)
                "foo".let(::add)
                "bar".let(::add)
            },
            source.dexElementsForTesting,
        )
        assertEquals(
            buildList {
                testAbiList.forEach {
                    patchDirectory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
            },
            source.nativeLibraryDirectoriesForTesting,
        )
        assertEquals(
            buildList {
                testAbiList.forEach {
                    patchDirectory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
                "/system/lib/foo/bar".let(::add)
                "/system/lib/baz".let(::add)
            },
            source.nativeLibraryPathElementsForTesting,
        )
    }

    /**
     * Tests if factor loader works expectedly with apk file as input for Android M until Android N.
     */
    @Test
    @Config(
        sdk = [
            Build.VERSION_CODES.M,
        ]
    )
    fun factorLoaderAndLoadWithApkFileV23() {
        val outputDirectory = Files.createTempDirectory("tinker-test-").toFile()
        val patchDirectory = createTestPatchDirectoryWithMockFiles(
            dexMockMode = DexMockMode.APK,
        )
        val patch = Patch("foo", patchDirectory)
        val source = TestClassLoaderV23()
        InjectPathCodeLoader
            .Factory(
                source = source,
                outputDirectory = outputDirectory,
                abiList = testAbiList,
            )
            .createLoaderIfNeeded(patch)
            .apply {
                doLoadForTesting()
            }
        assertEquals(
            buildList {
                patchDirectory.patchDexApkFile.absolutePath.let(::add)
                "foo".let(::add)
                "bar".let(::add)
            },
            source.dexElementsForTesting,
        )
        assertEquals(
            buildList {
                testAbiList.forEach {
                    patchDirectory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
            },
            source.nativeLibraryDirectoriesForTesting,
        )
        assertEquals(
            buildList {
                testAbiList.forEach {
                    patchDirectory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
                "/system/lib/foo/bar".let(::add)
                "/system/lib/baz".let(::add)
            },
            source.nativeLibraryPathElementsForTesting,
        )
    }

    /**
     * Tests if factor loader works expectedly with dex files as input for Android L until Android M.
     */
    @Test
    @Config(
        sdk = [
            Build.VERSION_CODES.LOLLIPOP,
            Build.VERSION_CODES.LOLLIPOP_MR1,
        ]
    )
    fun factorLoaderAndLoadWithDexFilesV21() {
        val outputDirectory = Files.createTempDirectory("tinker-test-").toFile()
        val patchDirectory = createTestPatchDirectoryWithMockFiles(
            dexMockMode = DexMockMode.DEX,
        )
        val patch = Patch("foo", patchDirectory)
        val source = TestClassLoaderV21()
        InjectPathCodeLoader
            .Factory(
                source = source,
                outputDirectory = outputDirectory,
                abiList = testAbiList,
            )
            .createLoaderIfNeeded(patch)
            .apply {
                doLoadForTesting()
            }
        assertEquals(
            buildList {
                availableDexFileNamesAsSorted
                    .map(patchDirectory.patchDexDirectory::resolve)
                    .map { it.absolutePath }
                    .let(::addAll)
                "foo".let(::add)
                "bar".let(::add)
            },
            source.dexElementsForTesting,
        )
        assertEquals(
            buildList {
                testAbiList.forEach {
                    patchDirectory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
            },
            source.nativeLibraryDirectoriesForTesting,
        )
    }
    /**
     * Tests if factor loader works expectedly with apk file as input for Android L until Android M.
     */
    @Test
    @Config(
        sdk = [
            Build.VERSION_CODES.LOLLIPOP,
            Build.VERSION_CODES.LOLLIPOP_MR1,
        ]
    )
    fun factorLoaderAndLoadWithApkFileV21() {
        val outputDirectory = Files.createTempDirectory("tinker-test-").toFile()
        val patchDirectory = createTestPatchDirectoryWithMockFiles(
            dexMockMode = DexMockMode.APK,
        )
        val patch = Patch("foo", patchDirectory)
        val source = TestClassLoaderV21()
        InjectPathCodeLoader
            .Factory(
                source = source,
                outputDirectory = outputDirectory,
                abiList = testAbiList,
            )
            .createLoaderIfNeeded(patch)
            .apply {
                doLoadForTesting()
            }
        assertEquals(
            buildList {
                patchDirectory.patchDexApkFile.absolutePath.let(::add)
                "foo".let(::add)
                "bar".let(::add)
            },
            source.dexElementsForTesting,
        )
        assertEquals(
            buildList {
                testAbiList.forEach {
                    patchDirectory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
            },
            source.nativeLibraryDirectoriesForTesting,
        )
    }
}