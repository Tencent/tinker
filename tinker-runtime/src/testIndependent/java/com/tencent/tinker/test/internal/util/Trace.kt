package com.tencent.tinker.test.internal.util

import com.tencent.tinker.internal.util.async
import com.tencent.tinker.internal.util.traceE
import com.tencent.tinker.internal.util.traceS
import com.tencent.tinker.internal.util.traceTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TraceTest {

    /**
     * Tests trace task procedures in different scenarios.
     */
    @Test
    fun trace() {
        val expected = Any()
        val (actual, events) = traceTask("0") {
            traceS("1") {
            }
            traceE("2") {
            }
            traceS("3") {
                traceE("4") {
                }
            }
            traceE("5") {
                traceS("6") {
                }
            }
            async {
                launch {
                    traceS("7") {
                    }
                }
                launch {
                    traceE("8") {
                    }
                }
            }
            expected
        }
        assertSame(
            expected,
            actual,
        )
        assertEquals(
            (0..8).map { "Tinker $it" },
            events.map { it.name }.sorted(),
        )
    }

    /**
     * Tests if task procedure tracing can handle throwable.
     */
    @Test
    fun withException() {
        val caught = mutableListOf<Throwable>()
        val (_, events) = traceTask("0") {
            try {
                traceS("1") {
                    throw Exception()
                }
            } catch (throwable: Throwable) {
                caught.add(throwable)
            }
            try {
                traceE("2") {
                    throw Exception()
                }
            } catch (throwable: Throwable) {
                caught.add(throwable)
            }
        }
        assertEquals(2, caught.size)
        assertEquals(
            (0..2).map { "Tinker $it" },
            events.map { it.name }.sorted(),
        )
    }
}