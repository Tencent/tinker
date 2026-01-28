package com.tencent.tinker.test.internal.util

import com.tencent.tinker.internal.util.async
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConcurrentTest {

    /**
     * Test if running async tasks works as expected.
     */
    @Test
    fun runAsyncTasks() {
        val result = async {
            launch { 1 }
            launch { 2 }
            launch { 3 }
        }
        assertEquals(
            listOf(1, 2, 3),
            result.sorted()
        )
    }

    /**
     * Test if exception is thrown in async tasks can be rethrown.
     */
    @Test
    fun rethrowThrowable() {
        assertThrows(Throwable::class.java) {
            async {
                launch { 1 }
                launch { throw RuntimeException() }
            }
        }
    }
}