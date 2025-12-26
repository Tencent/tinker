package com.tencent.tinker.test.internal.load.library

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.load.library.LibraryLoader
import com.tencent.tinker.test.createLoadableTestPatchDirectory
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryLoaderTest {

    /**
     * Tests if factor loader works expectedly on Android O.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)

    fun factorLoaderAndLoadV26() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = context.createLoadableTestPatchDirectory()
        val patch = Patch("foo", directory)
        LibraryLoader
            .Factory(context)
            .createLoaderIfNeeded(patch)
            .load()
    }

    /**
     * Tests if factor loader works expectedly on Android M.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.M, maxSdkVersion = Build.VERSION_CODES.N_MR1)
    fun factorLoaderAndLoadV23() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = context.createLoadableTestPatchDirectory()
        val patch = Patch("foo", directory)
        LibraryLoader
            .Factory(context)
            .createLoaderIfNeeded(patch)
            .load()
    }

    /**
     * Tests if factor loader works expectedly on old Android versions.
     */
    @Test
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.LOLLIPOP_MR1)
    fun mockFactorLoaderAndLoadOld() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = context.createLoadableTestPatchDirectory()
        val patch = Patch("foo", directory)
        LibraryLoader
            .Factory(context)
            .createLoaderIfNeeded(patch)
            .load()
    }
}