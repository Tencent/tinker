package com.tencent.tinker.test.internal.load

import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.load.Loader
import com.tencent.tinker.internal.load.loadErrorTypeOfForTesting
import com.tencent.tinker.internal.load.tryLoadForTesting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class LoadTest {

    private class SuccessLoader : Loader() {
        var loaded: Boolean = false
        override fun load() {
            loaded = true
        }
    }

    private class FailureLoader : Loader() {
        val exception = RuntimeException("Load failed.")
        override fun load() {
            throw exception
        }
    }

    private class WrappedLoaderFactory(val loader: Loader?) : Loader.Factory() {
        var factored: Boolean = false
        override fun createLoaderIfNeeded(patch: Patch): Loader? {
            factored = true
            return loader
        }
    }

    private object TestErrorType : TinkerError.Type {

        override val group: TinkerError.TypeGroup
            get() = TinkerError.TypeGroup.UNEXPECTED

        override val typeCode: Int
            get() = 0
    }

    /**
     * Tests if loaders are factored and loaded expectedly.
     */
    @Test
    fun factorLoadersAndLoad() {
        val firstLoader = SuccessLoader()
        val secondLoader = SuccessLoader()
        val patch = Patch(
            "foo",
            Files.createTempDirectory("tinker-test-").toFile()
        )
        val factories = listOf(
            WrappedLoaderFactory(firstLoader),
            WrappedLoaderFactory(secondLoader),
            WrappedLoaderFactory(null),
        )
        factories.tryLoadForTesting(patch)
        assertTrue(factories.all { it.factored })
        assertTrue(firstLoader.loaded)
        assertTrue(secondLoader.loaded)
    }

    /**
     * Tests if any loader throws throwable can be uncaught expectedly.
     */
    @Test
    fun loadWithFailureLoader() {
        val failureLoader = FailureLoader()
        val patch = Patch(
            "foo",
            Files.createTempDirectory("tinker-test-").toFile()
        )
        val factories = listOf(
            WrappedLoaderFactory(SuccessLoader()),
            WrappedLoaderFactory(failureLoader),
            WrappedLoaderFactory(null),
        )
        val error = assertThrows(TinkerError::class.java) {
            factories.tryLoadForTesting(patch)
        }
        // Make sure start loading until all factories are factored.
        assertTrue(factories.all { it.factored })
        assertEquals(
            loadErrorTypeOfForTesting("UNRECOVERABLE_LOAD_FAILED"),
            error.type,
        )
        assertSame(failureLoader.exception, error.cause)
    }

    /**
     * Tests if factory raises `TinkerError` can be uncaught expectedly.
     */
    @Test
    fun factoryRaisesTinkerError() {
        val firstLoader = SuccessLoader()
        val patch = Patch(
            "foo",
            Files.createTempDirectory("tinker-test-").toFile()
        )
        val expected = TinkerError(
            TestErrorType,
            "This is a test error."
        )
        val actual = assertThrows(TinkerError::class.java) {
            listOf(
                object : Loader.Factory() {
                    override fun createLoaderIfNeeded(patch: Patch): Loader = firstLoader
                },
                object : Loader.Factory() {
                    override fun createLoaderIfNeeded(patch: Patch): Loader {
                        throw expected
                    }
                },
            ).tryLoadForTesting(patch)
        }
        assertSame(expected, actual)
    }

    /**
     * Tests if factory raises unexpected throwable can be converted to `TinkerError` and rethrown
     * expectedly.
     */
    @Test
    fun factoryRaisesUnexpectedThrowable() {
        val firstLoader = SuccessLoader()
        val patch = Patch(
            "foo",
            Files.createTempDirectory("tinker-test-").toFile()
        )
        val expected = RuntimeException("This is an unexpected throwable")
        val actual = assertThrows(TinkerError::class.java) {
            listOf(
                object : Loader.Factory() {
                    override fun createLoaderIfNeeded(patch: Patch): Loader = firstLoader
                },
                object : Loader.Factory() {
                    override fun createLoaderIfNeeded(patch: Patch): Loader {
                        throw expected
                    }
                },
            ).tryLoadForTesting(patch)
        }
        assertNotNull(actual)
        assertEquals(TinkerError.TypeGroup.LOAD, actual!!.type.group)
        assertSame(expected, actual.cause)
    }
}