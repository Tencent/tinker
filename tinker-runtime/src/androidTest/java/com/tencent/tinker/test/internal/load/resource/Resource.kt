package com.tencent.tinker.test.internal.load.resource

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.TEST_ADDED_ASSET_FILE_NAME
import com.tencent.tinker.internal.TEST_MODIFIED_ASSET_FILE_NAME
import com.tencent.tinker.internal.load.resource.ResourceLoader
import com.tencent.tinker.internal.patchResourceApkFile
import com.tencent.tinker.test.DexMockMode
import com.tencent.tinker.test.createLoadableTestPatchDirectory
import com.tencent.tinker.test.internal.loader.resource.TestResourceActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResourceLoaderTest {

    /**
     * Tests if loader can load asset files and pass verification.
     */
    @Test
    fun factorLoaderAndLoad() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = context.createLoadableTestPatchDirectory(
            dexMockMode = DexMockMode.DEX,
        )
        val patch = Patch("foo", directory)
        ResourceLoader.Factory(context)
            .createLoaderIfNeeded(patch)
            .load()
    }

    /**
     * Tests if patched resources are still available after activity is relaunched.
     */
    @Test
    fun reload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = context.createLoadableTestPatchDirectory(
            dexMockMode = DexMockMode.DEX,
        )
        val patch = Patch("foo", directory)
        ResourceLoader.Factory(context)
            .createLoaderIfNeeded(patch)
            .load()
        launchActivity<TestResourceActivity>().use { scenario ->
            scenario.onActivity { activity ->
                val added = activity.assets.open(TEST_ADDED_ASSET_FILE_NAME)
                    .use { it.readBytes() }
                    .toString(Charsets.UTF_8)
                assertEquals("patched", added)
                val modified = activity.assets.open(TEST_MODIFIED_ASSET_FILE_NAME)
                    .use { it.readBytes() }
                    .toString(Charsets.UTF_8)
                assertEquals("patched", modified)
            }

            directory.patchResourceApkFile.setLastModified(System.currentTimeMillis())
            scenario.recreate()

            scenario.onActivity { activity ->
                val added = activity.assets.open(TEST_ADDED_ASSET_FILE_NAME)
                    .use { it.readBytes() }
                    .toString(Charsets.UTF_8)
                assertEquals("patched", added)
                val modified = activity.assets.open(TEST_MODIFIED_ASSET_FILE_NAME)
                    .use { it.readBytes() }
                    .toString(Charsets.UTF_8)
                assertEquals("patched", modified)
            }
        }
    }
}