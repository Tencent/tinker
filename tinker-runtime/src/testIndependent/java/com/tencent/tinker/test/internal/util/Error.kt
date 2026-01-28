package com.tencent.tinker.test.internal.util

import com.tencent.tinker.Tinker
import com.tencent.tinker.internal.util.expected
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorTest {

    /**
     * Tests if [expected] works as expected if no throwable is raised, and clanger is not called.
     */
    @Test
    fun withoutThrowable() {
        var cleaned = false
        expected<Tinker.Error.Load>(
            "test action",
            cleaner = { cleaned = true },
        ) {
            // do nothing
        }
        assertFalse(cleaned)
    }

    /**
     * Tests if unexpected exception being raised in [expected] can be converted to [Tinker.Error] with first error type.
     */
    @Test
    fun convertToUnexpected() {
        val cause = IllegalStateException("This is an unexpected exception")
        var cleaned = false
        val caught = assertThrows(Tinker.Error::class.java) {
            expected<Tinker.Error.Load>(
                "test action",
                cleaner = { cleaned = true }
            ) {
                throw cause
            }
        }
        assertTrue(cleaned)
        assertSame(cause, caught.cause)
        assertSame(
            Tinker.Error.Load.UNEXPECTED,
            caught.type,
        )
    }

    /**
     * Tests if [Tinker.Error] being raised in [expected] can be raised as is.
     */
    @Test
    fun skipConvert() {
        val cause = Tinker.Error(
            Tinker.Error.Load.UNRECOVERABLE_LOAD_FAILED,
            "This is an expected exception.",
        )
        var cleaned = false
        val caught = assertThrows(Tinker.Error::class.java) {
            expected<Tinker.Error.Load>(
                "test action",
                cleaner = { cleaned = true },
            ) {
                throw cause
            }
        }
        assertTrue(cleaned)
        assertSame(cause, caught)
        assertSame(
            Tinker.Error.Load.UNRECOVERABLE_LOAD_FAILED,
            caught.type,
        )
    }
}