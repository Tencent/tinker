package com.tencent.tinker.test.internal.load.code

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.load.code.InjectPathCodeLoader
import com.tencent.tinker.test.DexMockMode
import com.tencent.tinker.test.createLoadableTestPatchDirectory
import com.tencent.tinker.test.createTestDirectory
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InjectPathDexLoaderTest {

    /**
     * Tests if loader can load dex files and libraries and pass verification.
     */
    @Test
    fun factorLoaderAndLoadWithDexFiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = context.createLoadableTestPatchDirectory(
            dexMockMode = DexMockMode.DEX,
        )
        val outputDirectory = createTestDirectory()
        val patch = Patch("foo", directory)
        // Make sure verification is passed and no exception is thrown.
        InjectPathCodeLoader
            .Factory(context.applicationContext.classLoader, outputDirectory)
            .createLoaderIfNeeded(patch)
            .load()
    }

    /**
     * Tests if loader can load apk file and libraries and pass verification.
     */
    @Test
    fun factorLoaderAndLoadWithApkFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = context.createLoadableTestPatchDirectory(
            dexMockMode = DexMockMode.APK,
        )
        val outputDirectory = createTestDirectory()
        val patch = Patch("foo", directory)
        // Make sure verification is passed and no exception is thrown.
        InjectPathCodeLoader
            .Factory(context.applicationContext.classLoader, outputDirectory)
            .createLoaderIfNeeded(patch)
            .load()
    }
}