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
     * TODO:
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
     * TODO:
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
     * TODO:
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