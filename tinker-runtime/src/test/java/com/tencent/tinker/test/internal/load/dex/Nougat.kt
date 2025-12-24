package com.tencent.tinker.test.internal.load.dex

import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.load.dex.NewClassLoaderDexLoader
import com.tencent.tinker.internal.load.dex.V24NonHardeningDexLoader
import com.tencent.tinker.internal.load.dex.V27NonHardeningDexLoader
import com.tencent.tinker.internal.module.hidden.hiddenErrorTypeOfForTesting
import com.tencent.tinker.internal.patchDexDirectory
import com.tencent.tinker.test.internal.availableDexFileNamesAsSorted
import com.tencent.tinker.test.internal.createTestPatchDirectoryWithMockFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

@Suppress("unused")
private object TestSourceClassLoader : ClassLoader() {

    private val pathList = DexPathList()

    private class DexPathList {
        private val nativeLibraryDirectories = listOf(
            File("/foo/bar"),
            File("/baz")
        )
    }
}

private class TestClassLoaderInjector : NewClassLoaderDexLoader.ClassLoaderInjector() {
    var injected = null as ClassLoader?
    override fun inject(classLoader: ClassLoader) {
        injected = classLoader
    }
}

class NewClassLoaderDexLoaderTest {

    private object TestGeneratedClassLoader : ClassLoader()

    private class TestDexLoader(
        reference: Array<ClassLoader?>,
        classLoaderInjectors: Iterable<ClassLoaderInjector>,
        val dexPaths: String,
        val libraryDirectoryPaths: String
    ) : NewClassLoaderDexLoader(reference, classLoaderInjectors) {

        override fun createClassLoader(): ClassLoader =
            TestGeneratedClassLoader

        class Factory(
            reference: Array<ClassLoader?>,
            classLoaderInjectors: Iterable<ClassLoaderInjector>,
            source: ClassLoader,
        ) : NewClassLoaderDexLoader.Factory(
            reference,
            classLoaderInjectors,
            source,
        ) {
            override fun createLoaderByPaths(
                dexPaths: String,
                libraryDirectoryPaths: String
            ): NewClassLoaderDexLoader =
                TestDexLoader(reference, classLoaderInjectors, dexPaths, libraryDirectoryPaths)
        }
    }

    /**
     * Tests if factor loader with class loader with array type native library directories field
     * works expectedly.
     */
    @Test
    fun factorLoader() {
        val reference = arrayOfNulls<ClassLoader>(1)
        val injector = TestClassLoaderInjector()
        val directory = createTestPatchDirectoryWithMockFiles()
        val patch = Patch("foo", directory)
        val loader = TestDexLoader
            .Factory(
                reference = reference,
                classLoaderInjectors = listOf(injector),
                source = TestSourceClassLoader,
            )
            .createLoaderIfNeeded(patch)
            .let { it as? TestDexLoader }
        assertNotNull(loader)
        loader!!
        assertEquals(
            availableDexFileNamesAsSorted
                .map(directory.patchDexDirectory::resolve)
                .joinToString(File.pathSeparator) { it.absolutePath },
            loader.dexPaths,
        )
        assertEquals(
            "/foo/bar${File.pathSeparator}/baz",
            loader.libraryDirectoryPaths,
        )
        loader.dexLoadForTesting()
        assertSame(
            TestGeneratedClassLoader,
            reference[0],
        )
        assertSame(
            TestGeneratedClassLoader,
            injector.injected,
        )
    }

    @Suppress("unused")
    private object TestMissingPathListSourceClassLoader : ClassLoader()

    /**
     * Tests if factor loader with class loader missing path list can raise error expectedly.
     */
    @Test
    fun factorLoaderWithMissingPathListClassLoader() {
        val reference = arrayOfNulls<ClassLoader>(1)
        val directory = createTestPatchDirectoryWithMockFiles()
        val patch = Patch("foo", directory)
        val error = assertThrows(TinkerError::class.java) {
            TestDexLoader
                .Factory(
                    reference = reference,
                    classLoaderInjectors = emptyList(),
                    source = TestMissingPathListSourceClassLoader,
                )
                .createLoaderIfNeeded(patch)
        }
        assertEquals(
            hiddenErrorTypeOfForTesting("NO_SUCH_ELEMENT"),
            error.type,
        )
    }

    @Suppress("unused")
    private object TestMissingNativeLibraryDirectoriesSourceClassLoader : ClassLoader() {

        private val pathList = DexPathList()

        private class DexPathList
    }

    /**
     * Tests if factor loader with class loader missing native library directories can raise error
     * expectedly.
     */
    @Test
    fun factorLoaderWithMissingNativeLibraryDirectoriesClassLoader() {
        val reference = arrayOfNulls<ClassLoader>(1)
        val directory = createTestPatchDirectoryWithMockFiles()
        val patch = Patch("foo", directory)
        val error = assertThrows(TinkerError::class.java) {
            TestDexLoader
                .Factory(
                    reference = reference,
                    classLoaderInjectors = emptyList(),
                    source = TestMissingNativeLibraryDirectoriesSourceClassLoader,
                )
                .createLoaderIfNeeded(patch)
        }
        assertEquals(
            hiddenErrorTypeOfForTesting("NO_SUCH_ELEMENT"),
            error.type,
        )
    }

    @Suppress("unused")
    private object TestInvalidNativeLibraryDirectoriesSourceClassLoader : ClassLoader() {

        private val pathList = DexPathList()

        private class DexPathList {
            private val nativeLibraryDirectories = setOf(
                File("/foo/bar"),
                File("/baz")
            )
        }
    }

    /**
     * Tests if factor loader with class loader which native library directories is not array or
     * list can raise error expectedly.
     */
    @Test
    fun factorLoaderWithInvalidNativeLibraryDirectoriesClassLoader() {
        val reference = arrayOfNulls<ClassLoader>(1)
        val directory = createTestPatchDirectoryWithMockFiles()
        val patch = Patch("foo", directory)
        val error = assertThrows(TinkerError::class.java) {
            TestDexLoader
                .Factory(
                    reference = reference,
                    classLoaderInjectors = emptyList(),
                    source = TestInvalidNativeLibraryDirectoriesSourceClassLoader,
                )
                .createLoaderIfNeeded(patch)
        }
        error.printStackTrace()
        assertEquals(
            hiddenErrorTypeOfForTesting("CAST_FAILED"),
            error.type,
        )
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
    fun factorLoader() {
        val reference = arrayOfNulls<ClassLoader>(1)
        val injector = TestClassLoaderInjector()
        val directory = createTestPatchDirectoryWithMockFiles()
        val patch = Patch("foo", directory)
        val loader = V27NonHardeningDexLoader
            .Factory(
                reference = reference,
                classLoaderInjectors = listOf(injector),
                source = TestSourceClassLoader,
                classLoaderConstructor = ::TestClassLoader,
            )
            .createLoaderIfNeeded(patch)
            .let { it as? V27NonHardeningDexLoader }
        assertNotNull(loader)

        loader!!.dexLoadForTesting()

        assertTrue(reference[0] is TestClassLoader)
        val createdClassLoader = reference[0] as TestClassLoader
        assertSame(
            createdClassLoader,
            injector.injected,
        )
        assertEquals(
            availableDexFileNamesAsSorted
                .map(directory.patchDexDirectory::resolve)
                .joinToString(File.pathSeparator) { it.absolutePath },
            createdClassLoader.dexPaths,
        )
        assertEquals(
            "/foo/bar${File.pathSeparator}/baz",
            createdClassLoader.libraryDirectoryPaths,
        )
        assertSame(
            TestSourceClassLoader,
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
     * Tests if factor loader works expectedly.
     */
    @Test
    fun factorLoader() {
        val reference = arrayOfNulls<ClassLoader>(1)
        val injector = TestClassLoaderInjector()
        val directory = createTestPatchDirectoryWithMockFiles()
        val outputDirectory = Files.createTempDirectory("tinker-test-").toFile()
        val patch = Patch("foo", directory)
        val loader = V24NonHardeningDexLoader
            .Factory(
                reference = reference,
                classLoaderInjectors = listOf(injector),
                outputDirectory = outputDirectory,
                source = TestSourceClassLoader,
                classLoaderConstructor = ::TestClassLoader,
            )
            .createLoaderIfNeeded(patch)
            .let { it as? V24NonHardeningDexLoader }
        assertNotNull(loader)

        loader!!.dexLoadForTesting()

        assertTrue(reference[0] is TestClassLoader)
        val createdClassLoader = reference[0] as TestClassLoader
        assertSame(
            createdClassLoader,
            injector.injected,
        )
        assertEquals(
            availableDexFileNamesAsSorted
                .map(directory.patchDexDirectory::resolve)
                .joinToString(File.pathSeparator) { it.absolutePath },
            createdClassLoader.dexPaths,
        )
        assertEquals(
            "/foo/bar${File.pathSeparator}/baz",
            createdClassLoader.libraryDirectoryPaths,
        )
        assertEquals(
            outputDirectory,
            createdClassLoader.outputDirectory,
        )
        assertSame(
            TestSourceClassLoader,
            createdClassLoader.parentFromConstructor,
        )
    }
}