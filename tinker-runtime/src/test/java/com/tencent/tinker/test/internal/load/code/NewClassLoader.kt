package com.tencent.tinker.test.internal.load.code

import android.os.Build
import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.load.code.NewClassLoaderCodeLoader
import com.tencent.tinker.internal.load.code.V24NonHardeningCodeLoader
import com.tencent.tinker.internal.load.code.V27NonHardeningCodeLoader
import com.tencent.tinker.internal.patchDexDirectory
import com.tencent.tinker.internal.patchLibraryDirectory
import com.tencent.tinker.test.internal.availableDexFileNamesAsSorted
import com.tencent.tinker.test.internal.createTestPatchDirectoryWithMockFiles
import com.tencent.tinker.test.internal.testAbiList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import kotlin.intArrayOf

private class TestClassLoaderInjector : NewClassLoaderCodeLoader.ClassLoaderInjector() {
    var injected = null as ClassLoader?
    override fun inject(classLoader: ClassLoader) {
        injected = classLoader
    }
}

class V27NonHardeningDexLoaderTest {

    private class TestClassLoader(
        val dexPaths: String,
        val libraryDirectoryPaths: String,
        val parentFromConstructor: ClassLoader,
    ) : ClassLoader()

    /**
     * Tests if factor loader works expectedly.
     */
    @Test
    @Config(
        sdk = [
            Build.VERSION_CODES.O_MR1,
            Build.VERSION_CODES.P,
            Build.VERSION_CODES.Q,
            Build.VERSION_CODES.R,
        ]
    )
    fun factorLoader() {
        val reference = arrayOfNulls<ClassLoader>(1)
        val source = TestClassLoaderV26()
        val injector = TestClassLoaderInjector()
        val patchDirectory = createTestPatchDirectoryWithMockFiles()
        val patch = Patch("foo", patchDirectory)
        V27NonHardeningCodeLoader
            .Factory(
                reference = reference,
                classLoaderInjectors = listOf(injector),
                source = source,
                abiList = testAbiList,
                classLoaderConstructor = ::TestClassLoader,
            )
            .createLoaderIfNeeded(patch)
            .loadForCodeForTesting()
        assertTrue(reference[0] is TestClassLoader)
        val createdClassLoader = reference[0] as TestClassLoader
        assertSame(
            createdClassLoader,
            injector.injected,
        )
        assertEquals(
            availableDexFileNamesAsSorted
                .map(patchDirectory.patchDexDirectory::resolve)
                .joinToString(File.pathSeparator) { it.absolutePath },
            createdClassLoader.dexPaths,
        )
        assertEquals(
            buildList {
                testAbiList.forEach {
                    patchDirectory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
            }.joinToString(File.pathSeparator),
            createdClassLoader.libraryDirectoryPaths,
        )
        assertSame(
            source,
            createdClassLoader.parentFromConstructor,
        )
    }
}

class V24NonHardeningDexLoaderTest {
    private class TestClassLoader(
        val dexPaths: String,
        val outputDirectory: File,
        val libraryDirectoryPaths: String,
        val parentFromConstructor: ClassLoader,
    ) : ClassLoader()

    /**
     * Tests if factor loader works expectedly for Android O.
     */
    @Test
    @Config(
        sdk = [
            Build.VERSION_CODES.O,
        ]
    )
    fun factorLoaderAndLoadV26() {
        val reference = arrayOfNulls<ClassLoader>(1)
        val source = TestClassLoaderV26()
        val injector = TestClassLoaderInjector()
        val patchDirectory = createTestPatchDirectoryWithMockFiles()
        val outputDirectory = Files.createTempDirectory("tinker-test-").toFile()
        val patch = Patch("foo", patchDirectory)
        V24NonHardeningCodeLoader
            .Factory(
                reference = reference,
                classLoaderInjectors = listOf(injector),
                outputDirectory = outputDirectory,
                source = source,
                abiList = testAbiList,
                classLoaderConstructor = ::TestClassLoader,
            )
            .createLoaderIfNeeded(patch)
            .loadForCodeForTesting()
        assertTrue(reference[0] is TestClassLoader)
        val createdClassLoader = reference[0] as TestClassLoader
        assertSame(
            createdClassLoader,
            injector.injected,
        )
        assertEquals(
            availableDexFileNamesAsSorted
                .map(patchDirectory.patchDexDirectory::resolve)
                .joinToString(File.pathSeparator) { it.absolutePath },
            createdClassLoader.dexPaths,
        )
        assertEquals(
            buildList {
                testAbiList.forEach {
                    patchDirectory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
            }.joinToString(File.pathSeparator),
            createdClassLoader.libraryDirectoryPaths,
        )
        assertEquals(
            outputDirectory,
            createdClassLoader.outputDirectory,
        )
        assertSame(
            source,
            createdClassLoader.parentFromConstructor,
        )
    }

    /**
     * Tests if factor loader works expectedly for Android N until Android O.
     */
    @Test
    @Config(
        sdk = [
            Build.VERSION_CODES.N,
            Build.VERSION_CODES.N_MR1,
        ]
    )
    fun factorLoaderAndLoadV24() {
        val reference = arrayOfNulls<ClassLoader>(1)
        val source = TestClassLoaderV24()
        val injector = TestClassLoaderInjector()
        val patchDirectory = createTestPatchDirectoryWithMockFiles()
        val outputDirectory = Files.createTempDirectory("tinker-test-").toFile()
        val patch = Patch("foo", patchDirectory)
        V24NonHardeningCodeLoader
            .Factory(
                reference = reference,
                classLoaderInjectors = listOf(injector),
                outputDirectory = outputDirectory,
                source = source,
                abiList = testAbiList,
                classLoaderConstructor = ::TestClassLoader,
            )
            .createLoaderIfNeeded(patch)
            .loadForCodeForTesting()
        assertTrue(reference[0] is TestClassLoader)
        val createdClassLoader = reference[0] as TestClassLoader
        assertSame(
            createdClassLoader,
            injector.injected,
        )
        assertEquals(
            availableDexFileNamesAsSorted
                .map(patchDirectory.patchDexDirectory::resolve)
                .joinToString(File.pathSeparator) { it.absolutePath },
            createdClassLoader.dexPaths,
        )
        assertEquals(
            buildList {
                testAbiList.forEach {
                    patchDirectory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
            }.joinToString(File.pathSeparator),
            createdClassLoader.libraryDirectoryPaths,
        )
        assertEquals(
            outputDirectory,
            createdClassLoader.outputDirectory,
        )
        assertSame(
            source,
            createdClassLoader.parentFromConstructor,
        )
    }
}