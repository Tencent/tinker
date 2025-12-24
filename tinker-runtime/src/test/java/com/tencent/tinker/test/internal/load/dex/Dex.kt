package com.tencent.tinker.test.internal.load.dex

import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.load.dex.DexLoader
import com.tencent.tinker.internal.patchDexDirectory
import com.tencent.tinker.test.internal.availableDexFileNamesAsSorted
import com.tencent.tinker.test.internal.createTestPatchDirectoryWithMockFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DexLoaderTest {

    private object TestClassLoader : ClassLoader()

    private class TestLoader : DexLoader() {

        override fun dexLoad(): ClassLoader {
            return TestClassLoader
        }

        class Factory : DexLoader.Factory() {

            var calledInputs = null as List<File>?

            override fun createLoaderByDexFiles(inputs: List<File>): DexLoader {
                calledInputs = inputs
                return TestLoader()
            }
        }
    }

    private object TestExceptionFactory : DexLoader.Factory() {
        val exception = IllegalStateException("This is an unexpected exception")

        override fun createLoaderByDexFiles(inputs: List<File>): DexLoader {
            throw exception
        }
    }

    /**
     * Tests if dex loader factory sorts input dex files as expected.
     */
    @Test
    fun buildLoaderWithSortedInputs() {
        val directory = createTestPatchDirectoryWithMockFiles()
        val patch = Patch("foo", directory)
        val factory = TestLoader.Factory()
        factory.createLoaderIfNeeded(patch)
        assertEquals(
            availableDexFileNamesAsSorted
                .map(directory.patchDexDirectory::resolve),
            factory.calledInputs
        )
    }

    /**
     * Tests if build dex loader with empty inputs can raise error expectedly.
     */
    @Test
    fun buildLoaderWithEmptyInputs() {
        val patch = Patch(
            "foo",
            Files.createTempDirectory("tinker-test-").toFile(),
        )
        val factory = TestLoader.Factory()
        val error = assertThrows(TinkerError::class.java) {
            factory.createLoaderIfNeeded(patch)
        }
        assertEquals(
            DexLoader.errorTypeOfForTesting("NO_VALID_INPUTS"),
            error.type,
        )
    }

    /**
     * Tests if build dex loader with invalid inputs can raise error expectedly.
     */
    @Test
    fun buildLoaderWithInvalidInputs() {
        val patch = Patch(
            "foo",
            Files.createTempFile("tinker-test-", ".dir").toFile(),
        )
        val factory = TestLoader.Factory()
        val error = assertThrows(TinkerError::class.java) {
            factory.createLoaderIfNeeded(patch)
        }
        assertEquals(
            DexLoader.errorTypeOfForTesting("NO_VALID_INPUTS"),
            error.type,
        )
    }

    /**
     * Tests if subclass raises unexpected exception can raise error expectedly.
     */
    @Test
    fun buildLoaderWithSubclassException() {
        val directory = createTestPatchDirectoryWithMockFiles()
        val patch = Patch("foo", directory)
        val error = assertThrows(TinkerError::class.java) {
            TestExceptionFactory.createLoaderIfNeeded(patch)
        }
        assertEquals(
            DexLoader.errorTypeOfForTesting("UNEXPECTED"),
            error.type,
        )
        assertSame(
            TestExceptionFactory.exception,
            error.cause,
        )
    }
}