package com.tencent.tinker.test.internal.load.code

import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.load.code.CodeLoader
import com.tencent.tinker.internal.load.code.codeLoaderErrorTypeOfForTesting
import com.tencent.tinker.internal.patchDexDirectory
import com.tencent.tinker.test.internal.availableDexFileNamesAsSorted
import com.tencent.tinker.test.internal.createTestPatchDirectoryWithMockFiles
import com.tencent.tinker.test.internal.testAbiList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DexLoaderTest {

    private object TestClassLoader : ClassLoader()

    private class TestLoader : CodeLoader() {

        override fun doLoad(): ClassLoader {
            return TestClassLoader
        }

        class Factory : CodeLoader.Factory(testAbiList) {

            var calledInputs = null as List<File>?

            override fun createLoader(dexFiles: List<File>, libraryDirectories: List<File>): CodeLoader {
                calledInputs = dexFiles
                return TestLoader()
            }
        }
    }

    private object TestExceptionFactory : CodeLoader.Factory(testAbiList) {
        val exception = IllegalStateException("This is an unexpected exception")

        override fun createLoader(dexFiles: List<File>, libraryDirectories: List<File>): CodeLoader {
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
            codeLoaderErrorTypeOfForTesting("NO_VALID_INPUTS"),
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
            codeLoaderErrorTypeOfForTesting("NO_VALID_INPUTS"),
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
            codeLoaderErrorTypeOfForTesting("UNEXPECTED"),
            error.type,
        )
        assertSame(
            TestExceptionFactory.exception,
            error.cause,
        )
    }
}