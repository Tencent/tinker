package com.tencent.tinker.test.internal.module.patch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tencent.tinker.internal.module.patch.RawPatchManager
import com.tencent.tinker.internal.module.patch.RawPatchManagerImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class RawPatchManagerTest {

    /**
     * Tests if factory function creates the correct singleton.
     */
    @Test
    fun factory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = RawPatchManager.with(context)
        assertTrue(manager is RawPatchManagerImpl)
        assertSame(
            context.applicationContext,
            (manager as? RawPatchManagerImpl)?.contextForTesting()
        )
        // Make sure it is singleton.
        val anotherManager = RawPatchManager.with(context)
        assertSame(manager, anotherManager)
    }
}