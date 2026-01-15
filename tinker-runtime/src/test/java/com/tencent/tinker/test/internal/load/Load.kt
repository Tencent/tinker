package com.tencent.tinker.test.internal.load

import com.tencent.tinker.Tinker
import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.load.Loader
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
        val error = assertThrows(Tinker.Error::class.java) {
            factories.tryLoadForTesting(patch)
        }
        // Make sure start loading until all factories are factored.
        assertTrue(factories.all { it.factored })
        assertEquals(
            Tinker.Error.Load.UNRECOVERABLE_LOAD_FAILED,
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
        val expected = Tinker.Error(
            Tinker.Error.Load.UNEXPECTED,
            "This is a test error."
        )
        val actual = assertThrows(Tinker.Error::class.java) {
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
        val actual = assertThrows(Tinker.Error::class.java) {
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
        assertEquals(
            Tinker.Error.Load.UNEXPECTED,
            actual!!.type,
        )
        assertSame(expected, actual.cause)
    }
}