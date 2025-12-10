package com.tencent.tinker.test.internal.module.layout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tencent.tinker.internal.module.layout.PatchLayoutConstructor
import com.tencent.tinker.internal.module.layout.PatchLayoutConstructorImpl
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PatchLayoutConstructorTest {

    /**
     * Tests if factory function creates the correct singleton.
     */
    @Test
    fun factory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val constructor = PatchLayoutConstructor.with(context)
        assertTrue(constructor is PatchLayoutConstructorImpl)
        assertSame(
            context.applicationContext,
            (constructor as? PatchLayoutConstructorImpl)?.contextForTesting()
        )
        // Make sure it is singleton.
        val anotherManager = PatchLayoutConstructor.with(context)
        assertSame(constructor, anotherManager)
    }
}