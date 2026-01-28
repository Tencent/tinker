package com.tencent.tinker.test.internal.load.code

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.load.code.NewClassLoaderCodeLoader
import com.tencent.tinker.internal.load.code.V24NonHardeningCodeLoader
import com.tencent.tinker.internal.load.code.V27NonHardeningCodeLoader
import com.tencent.tinker.internal.load.code.V31NonHardeningCodeLoader
import com.tencent.tinker.internal.patchDexApkFile
import com.tencent.tinker.internal.patchDexDirectory
import com.tencent.tinker.internal.patchLibraryDirectory
import com.tencent.tinker.test.DexMockMode
import com.tencent.tinker.test.availableDexFileNamesAsSorted
import com.tencent.tinker.test.createLoadableTestPatchDirectory
import com.tencent.tinker.test.createMockTestPatchDirectory
import com.tencent.tinker.test.createTestDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class V31NonHardeningDexLoaderTest {

    private class TestClassLoader(
        val dexPaths: String,
        val libraryDirectoryPaths: String,
        val parentFromConstructor: ClassLoader,
    ) : ClassLoader()

    @Suppress("unused")
    private class TestSourceClassLoader(
        withNullNativeLibraryDirectories: Boolean = false,
    ) : ClassLoader() {

        private val pathList = DexPathList(
            this,
            withNullNativeLibraryDirectories,
        )

        val dexElementsForTesting: List<String>
            get() = pathList.dexElementsForTesting

        val nativeLibraryPathElementsForTesting: List<String>
            get() = pathList.nativeLibraryPathElementsForTesting

        val nativeLibraryDirectoriesForTesting: List<String>
            get() = pathList.nativeLibraryDirectoriesForTesting

        private class DexPathList(
            private val definingContext: ClassLoader,
            withNullNativeLibraryDirectories: Boolean,
        ) {

            private class Element(val path: String)

            private class NativeLibraryElement(val path: String)

            private val dexElements: Array<Element> = arrayOf(
                Element("foo"),
                Element("bar"),
            )

            private val nativeLibraryPathElements = arrayOf(
                NativeLibraryElement("/foo/bar"),
                NativeLibraryElement("/baz"),
                NativeLibraryElement("/system/lib/foo/bar"),
                NativeLibraryElement("/system/lib/baz")
            )

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

            val dexElementsForTesting: List<String>
                get() = dexElements.map { it.path }

            val nativeLibraryPathElementsForTesting: List<String>
                get() = nativeLibraryPathElements.map { it.path }

            val nativeLibraryDirectoriesForTesting: List<String>
                get() = nativeLibraryDirectories
                    ?.map { it.absolutePath }
                    ?: emptyList()

            companion object {
                @JvmStatic
                fun makeDexElements(
                    files: List<File>,
                    optimizedDirectory: File?,
                    suppressedExceptions: List<IOException>,
                    loader: ClassLoader,
                ): Array<Element> {
                    return files
                        .map {
                            Element(it.absolutePath)
                        }
                        .toTypedArray()
                }

                @JvmStatic
                fun makePathElements(
                    files: List<File>,
                ): Array<NativeLibraryElement> {
                    return files
                        .map { NativeLibraryElement(it.absolutePath) }
                        .toTypedArray()
                }
            }
        }
    }

    private class TestClassLoaderInjector : NewClassLoaderCodeLoader.ClassLoaderInjector() {
        var injected = null as ClassLoader?
        override fun inject(classLoader: ClassLoader) {
            injected = classLoader
        }
    }

    /**
     * Tests if factor loader works expectedly with dex files as input.
     *
     * While factoring `V31NoHardeningDexLoader` needs to access private field `parent` of
     * `ClassLoader`, which is inaccessible in OpenJDK, the test is moved to here as instrumentation
     * test.
     */
    @Test
    fun mockFactorLoaderWithDexFiles() {
        val reference = arrayOfNulls<ClassLoader>(1)
        val source = TestSourceClassLoader()
        val injector = TestClassLoaderInjector()
        val patchDirectory = createMockTestPatchDirectory(
            dexMockMode = DexMockMode.DEX,
        )
        val patch = Patch("foo", patchDirectory)
        V31NonHardeningCodeLoader
            .Factory(
                reference = reference,
                classLoaderInjectors = listOf(injector),
                source = source,
                classLoaderConstructor = ::TestClassLoader,
            )
            .createLoaderIfNeeded(patch)
            .doLoadForTesting()
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
                Build.SUPPORTED_ABIS.forEach {
                    patchDirectory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
            }.joinToString(File.pathSeparator),
            createdClassLoader.libraryDirectoryPaths,
        )
        assertSame(
            ClassLoader.getSystemClassLoader(),
            createdClassLoader.parentFromConstructor,
        )
        assertSame(
            source,
            createdClassLoader.parent,
        )
    }

    /**
     * Tests if factor loader works expectedly with apk file as input.
     *
     * While factoring `V31NoHardeningDexLoader` needs to access private field `parent` of
     * `ClassLoader`, which is inaccessible in OpenJDK, the test is moved to here as instrumentation
     * test.
     */
    @Test
    fun mockFactorLoaderWithApkFile() {
        val reference = arrayOfNulls<ClassLoader>(1)
        val source = TestSourceClassLoader()
        val injector = TestClassLoaderInjector()
        val patchDirectory = createMockTestPatchDirectory(
            dexMockMode = DexMockMode.APK,
        )
        val patch = Patch("foo", patchDirectory)
        V31NonHardeningCodeLoader
            .Factory(
                reference = reference,
                classLoaderInjectors = listOf(injector),
                source = source,
                classLoaderConstructor = ::TestClassLoader,
            )
            .createLoaderIfNeeded(patch)
            .doLoadForTesting()
        assertTrue(reference[0] is TestClassLoader)
        val createdClassLoader = reference[0] as TestClassLoader
        assertSame(
            createdClassLoader,
            injector.injected,
        )
        assertEquals(
            patchDirectory.patchDexApkFile.absolutePath,
            createdClassLoader.dexPaths,
        )
        assertEquals(
            buildList {
                Build.SUPPORTED_ABIS.forEach {
                    patchDirectory.patchLibraryDirectory.resolve(it).absolutePath.let(::add)
                }
                "/foo/bar".let(::add)
                "/baz".let(::add)
            }.joinToString(File.pathSeparator),
            createdClassLoader.libraryDirectoryPaths,
        )
        assertSame(
            ClassLoader.getSystemClassLoader(),
            createdClassLoader.parentFromConstructor,
        )
        assertSame(
            source,
            createdClassLoader.parent,
        )
    }

    /**
     * Tests if loader can load dex files and libraries and pass verification.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    fun factorLoaderAndLoadWithDexFiles() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val directory = application.createLoadableTestPatchDirectory(
            dexMockMode = DexMockMode.DEX,
        )
        val patch = Patch("foo", directory)
        val reference = arrayOfNulls<ClassLoader>(1)
        // Make sure verification is passed and no exception is thrown.
        V31NonHardeningCodeLoader
            .Factory(reference, application)
            .createLoaderIfNeeded(patch)
            .load()
        // Make sure constructed class loader is returned.
        assertNotNull(reference[0])
    }

    /**
     * Tests if loader can load apk file and libraries and pass verification.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    fun factorLoaderAndLoadWithApkFile() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val directory = application.createLoadableTestPatchDirectory(
            dexMockMode = DexMockMode.APK,
        )
        val patch = Patch("foo", directory)
        val reference = arrayOfNulls<ClassLoader>(1)
        // Make sure verification is passed and no exception is thrown.
        V31NonHardeningCodeLoader
            .Factory(reference, application)
            .createLoaderIfNeeded(patch)
            .load()
        // Make sure constructed class loader is returned.
        assertNotNull(reference[0])
    }
}

class V27NonHardeningDexLoaderTest {

    /**
     * Tests if loader can load dex files and libraries and pass verification.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O_MR1, maxSdkVersion = Build.VERSION_CODES.R)
    fun loadWithDexFiles() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val directory = application.createLoadableTestPatchDirectory(
            dexMockMode = DexMockMode.DEX,
        )
        val patch = Patch("foo", directory)
        val reference = arrayOfNulls<ClassLoader>(1)
        // Make sure verification is passed and no exception is thrown.
        V27NonHardeningCodeLoader
            .Factory(reference, application)
            .createLoaderIfNeeded(patch)
            .load()
        // Make sure constructed class loader is returned.
        assertNotNull(reference[0])
    }

    /**
     * Tests if loader can load apk file and libraries and pass verification.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O_MR1, maxSdkVersion = Build.VERSION_CODES.R)
    fun loadWithApkFile() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val directory = application.createLoadableTestPatchDirectory(
            dexMockMode = DexMockMode.APK,
        )
        val patch = Patch("foo", directory)
        val reference = arrayOfNulls<ClassLoader>(1)
        // Make sure verification is passed and no exception is thrown.
        V27NonHardeningCodeLoader
            .Factory(reference, application)
            .createLoaderIfNeeded(patch)
            .load()
        // Make sure constructed class loader is returned.
        assertNotNull(reference[0])
    }
}

class V24NonHardeningDexLoaderTest {

    /**
     * Tests if loader can load dex files and libraries and pass verification.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N, maxSdkVersion = Build.VERSION_CODES.O)
    fun loadWithDexFiles() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val directory = application.createLoadableTestPatchDirectory(
            dexMockMode = DexMockMode.DEX,
        )
        val outputDirectory = createTestDirectory()
        val patch = Patch("foo", directory)
        val reference = arrayOfNulls<ClassLoader>(1)
        // Make sure verification is passed and no exception is thrown.
        V24NonHardeningCodeLoader
            .Factory(reference, application, outputDirectory)
            .createLoaderIfNeeded(patch)
            .load()
        // Make sure constructed class loader is returned.
        assertNotNull(reference[0])
    }

    /**
     * Tests if loader can load apk file and libraries and pass verification.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N, maxSdkVersion = Build.VERSION_CODES.O)
    fun loadWithApkFile() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val directory = application.createLoadableTestPatchDirectory(
            dexMockMode = DexMockMode.APK,
        )
        val outputDirectory = createTestDirectory()
        val patch = Patch("foo", directory)
        val reference = arrayOfNulls<ClassLoader>(1)
        // Make sure verification is passed and no exception is thrown.
        V24NonHardeningCodeLoader
            .Factory(reference, application, outputDirectory)
            .createLoaderIfNeeded(patch)
            .load()
        // Make sure constructed class loader is returned.
        assertNotNull(reference[0])
    }
}