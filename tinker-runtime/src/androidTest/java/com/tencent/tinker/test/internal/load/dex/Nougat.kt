package com.tencent.tinker.test.internal.load.dex

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.load.dex.NewClassLoaderDexLoader
import com.tencent.tinker.internal.load.dex.V24NonHardeningDexLoader
import com.tencent.tinker.internal.load.dex.V27NonHardeningDexLoader
import com.tencent.tinker.internal.load.dex.V31NonHardeningDexLoader
import com.tencent.tinker.internal.patchDexDirectory
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

@RunWith(AndroidJUnit4::class)
class V31NonHardeningDexLoaderTest {

    private class TestClassLoader(
        val dexPaths: String,
        val libraryDirectoryPaths: String,
        val parentFromConstructor: ClassLoader,
    ) : ClassLoader()

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

    /**
     * Tests if factor loader works expectedly.
     *
     * While factoring `V31NoHardeningDexLoader` needs to access private field `parent` of
     * `ClassLoader`, which is inaccessible in OpenJDK, the test is moved to here as instrumentation
     * test.
     */
    @Test
    fun factorLoader() {
        val reference = arrayOfNulls<ClassLoader>(1)
        val injector = TestClassLoaderInjector()
        val directory = createMockTestPatchDirectory()
        val patch = Patch("foo", directory)
        val loader = V31NonHardeningDexLoader
            .Factory(
                reference = reference,
                classLoaderInjectors = listOf(injector),
                source = TestSourceClassLoader,
                classLoaderConstructor = ::TestClassLoader,
            )
            .createLoaderIfNeeded(patch)
            .let { it as? V31NonHardeningDexLoader }
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
            ClassLoader.getSystemClassLoader(),
            createdClassLoader.parentFromConstructor,
        )
        assertSame(
            TestSourceClassLoader,
            createdClassLoader.parent,
        )
    }

    /**
     * Tests if loader can load dex files and pass verification.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    fun load() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val directory = application.createLoadableTestPatchDirectory()
        val patch = Patch("foo", directory)
        val reference = arrayOfNulls<ClassLoader>(1)
        val loader = V31NonHardeningDexLoader
            .Factory(reference, application)
            .createLoaderIfNeeded(patch)
            .let { it as? V31NonHardeningDexLoader }
        assertNotNull(loader)
        // Make sure verification is passed and no exception is thrown.
        loader!!.load()
        // Make sure constructed class loader is returned.
        assertNotNull(reference[0])
    }
}

class V27NonHardeningDexLoaderTest {

    /**
     * Tests if loader can load dex files and pass verification.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O_MR1, maxSdkVersion = Build.VERSION_CODES.R)
    fun load() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val directory = application.createLoadableTestPatchDirectory()
        val patch = Patch("foo", directory)
        val reference = arrayOfNulls<ClassLoader>(1)
        val loader = V27NonHardeningDexLoader
            .Factory(reference, application)
            .createLoaderIfNeeded(patch)
            .let { it as? V27NonHardeningDexLoader }
        assertNotNull(loader)
        // Make sure verification is passed and no exception is thrown.
        loader!!.load()
        // Make sure constructed class loader is returned.
        assertNotNull(reference[0])
    }
}

class V24NonHardeningDexLoaderTest {

    /**
     * Tests if loader can load dex files and pass verification.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N, maxSdkVersion = Build.VERSION_CODES.O)
    fun load() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val directory = application.createLoadableTestPatchDirectory()
        val outputDirectory = createTestDirectory()
        val patch = Patch("foo", directory)
        val reference = arrayOfNulls<ClassLoader>(1)
        val loader = V24NonHardeningDexLoader
            .Factory(reference, application, outputDirectory)
            .createLoaderIfNeeded(patch)
            .let { it as? V24NonHardeningDexLoader }
        assertNotNull(loader)
        // Make sure verification is passed and no exception is thrown.
        loader!!.load()
        // Make sure constructed class loader is returned.
        assertNotNull(reference[0])
    }
}