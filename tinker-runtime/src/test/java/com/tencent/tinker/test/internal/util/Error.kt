package com.tencent.tinker.test.internal.util

import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.util.expected
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorTest {

    private enum class TestErrorType : TinkerError.Type {
        UNEXPECTED,
        EXPECTED;

        override val group: TinkerError.TypeGroup
            get() = TinkerError.TypeGroup.UNEXPECTED

        override val typeCode: Int
            get() = ordinal
    }

    /**
     * Tests if [expected] works as expected if no throwable is raised, and clanger is not called.
     */
    @Test
    fun withoutThrowable() {
        var cleaned = false
        expected<TestErrorType>(
            "test action",
            cleaner = { cleaned = true },
        ) {
            // do nothing
        }
        assertFalse(cleaned)
    }

    /**
     * Tests if unexpected exception being raised in [expected] can be converted to [TinkerError] with first error type.
     */
    @Test
    fun convertToUnexpected() {
        val cause = IllegalStateException("This is an unexpected exception")
        var cleaned = false
        val caught = assertThrows(TinkerError::class.java) {
            expected<TestErrorType>(
                "test action",
                cleaner = { cleaned = true }
            ) {
                throw cause
            }
        }
        assertTrue(cleaned)
        assertSame(cause, caught.cause)
        assertSame(TestErrorType.UNEXPECTED, caught.type)
    }

    /**
     * Tests if [TinkerError] being raised in [expected] can be raised as is.
     */
    @Test
    fun skipConvert() {
        val cause = TinkerError(
            TestErrorType.EXPECTED,
            "This is an expected exception.",
        )
        var cleaned = false
        val caught = assertThrows(TinkerError::class.java) {
            expected<TestErrorType>(
                "test action",
                cleaner = { cleaned = true },
            ) {
                throw cause
            }
        }
        assertTrue(cleaned)
        assertSame(cause, caught)
        assertSame(TestErrorType.EXPECTED, caught.type)
    }
}