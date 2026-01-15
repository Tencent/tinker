package com.tencent.tinker.test.internal.module.oat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tencent.tinker.internal.module.oat.OatManager
import com.tencent.tinker.internal.module.oat.OatManagerImpl
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OatManagerTest {

    /**
     * Tests if factory function creates the correct singleton.
     */
    @Test
    fun factory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = OatManager.with(context)
        assertTrue(manager is OatManagerImpl)
        assertSame(
            context.applicationContext,
            (manager as? OatManagerImpl)?.contextForTesting()
        )
        // Make sure it is singleton.
        val anotherManager = OatManager.with(context)
        assertSame(manager, anotherManager)
    }
}