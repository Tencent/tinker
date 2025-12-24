package com.tencent.tinker.test.internal.load.dex

import android.os.Build
import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.load.dex.InjectPathDexLoader
import com.tencent.tinker.internal.module.hidden.hiddenErrorTypeOfForTesting
import com.tencent.tinker.internal.patchDexDirectory
import com.tencent.tinker.test.internal.availableDexFileNamesAsSorted
import com.tencent.tinker.test.internal.configTestLogger
import com.tencent.tinker.test.internal.createTestPatchDirectoryWithMockFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class InjectPathDexLoaderTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun setUp() {
            configTestLogger()
        }
    }

    private abstract class TestClassLoader : ClassLoader() {
        abstract val elementsForTesting: List<String>
    }

    private fun buildLoaderAndLoadWithSource(source: TestClassLoader) {
        val outputDirectory = Files.createTempDirectory("tinker-test-").toFile()
        val patchDirectory = createTestPatchDirectoryWithMockFiles()
        val patch = Patch("foo", patchDirectory)
        InjectPathDexLoader.Factory(source, outputDirectory)
            .createLoaderIfNeeded(patch)
            .apply {
                dexLoadForTesting()
            }
        assertEquals(
            source.elementsForTesting,
            buildList {
                availableDexFileNamesAsSorted
                    .map(patchDirectory.patchDexDirectory::resolve)
                    .map {
                        "${outputDirectory.absolutePath}:${it.absolutePath}"
                    }
                    .let(::addAll)
                "foo".let(::add)
                "bar".let(::add)
            }
        )
    }

    @Suppress("unused")
    private class Test23ListVariantClassLoader : TestClassLoader() {

        private val pathList = DexPathList()

        override val elementsForTesting: List<String>
            get() = pathList.elementsForTesting

        private class DexPathList {

            private class Element(val key: String)

            private val dexElements: Array<Element> = arrayOf(
                Element("foo"),
                Element("bar"),
            )

            val elementsForTesting: List<String>
                get() = dexElements.map { it.key }

            companion object {
                @JvmStatic
                fun makePathElements(
                    files: List<File>,
                    optimizedDirectory: File,
                    suppressedExceptions: List<IOException>,
                ): Array<Element> {
                    return files
                        .map {
                            Element("${optimizedDirectory.absolutePath}:${it.absolutePath}")
                        }
                        .toTypedArray()
                }
            }
        }
    }

    @Test
    @Config(
        sdk = [Build.VERSION_CODES.M]
    )
    fun buildLoaderAndLoadWith23ListVariantClassLoader() {
        buildLoaderAndLoadWithSource(Test23ListVariantClassLoader())
    }

    @Suppress("unused")
    private class Test23ArrayListVariantClassLoader : TestClassLoader() {

        private val pathList = DexPathList()

        override val elementsForTesting: List<String>
            get() = pathList.elementsForTesting

        private class DexPathList {

            private class Element(val key: String)

            private val dexElements: Array<Element> = arrayOf(
                Element("foo"),
                Element("bar"),
            )

            val elementsForTesting: List<String>
                get() = dexElements.map { it.key }

            companion object {
                @JvmStatic
                fun makePathElements(
                    files: ArrayList<File>,
                    optimizedDirectory: File,
                    suppressedExceptions: ArrayList<IOException>,
                ): Array<Element> {
                    return files
                        .map {
                            Element("${optimizedDirectory.absolutePath}:${it.absolutePath}")
                        }
                        .toTypedArray()
                }
            }
        }
    }

    @Test
    @Config(
        sdk = [Build.VERSION_CODES.M]
    )
    fun buildLoaderAndLoadWith23ArrayListVariant() {
        buildLoaderAndLoadWithSource(Test23ArrayListVariantClassLoader())
    }

    @Suppress("unused")
    private class Test21ListVariantClassLoader : TestClassLoader() {

        private val pathList = DexPathList()

        override val elementsForTesting: List<String>
            get() = pathList.elementsForTesting

        private class DexPathList {

            private class Element(val key: String)

            private val dexElements: Array<Element> = arrayOf(
                Element("foo"),
                Element("bar"),
            )

            val elementsForTesting: List<String>
                get() = dexElements.map { it.key }

            companion object {
                @JvmStatic
                fun makeDexElements(
                    files: List<File>,
                    optimizedDirectory: File,
                    suppressedExceptions: List<IOException>,
                ): Array<Element> {
                    return files
                        .map {
                            Element("${optimizedDirectory.absolutePath}:${it.absolutePath}")
                        }
                        .toTypedArray()
                }
            }
        }
    }

    @Test
    @Config(
        sdk = [Build.VERSION_CODES.LOLLIPOP]
    )
    fun buildLoaderAndLoadWith21ListVariant() {
        buildLoaderAndLoadWithSource(Test21ListVariantClassLoader())
    }

    @Suppress("unused")
    private class Test21ArrayListVariantClassLoader : TestClassLoader() {

        private val pathList = DexPathList()

        override val elementsForTesting: List<String>
            get() = pathList.elementsForTesting

        private class DexPathList {

            private class Element(val key: String)

            private val dexElements: Array<Element> = arrayOf(
                Element("foo"),
                Element("bar"),
            )

            val elementsForTesting: List<String>
                get() = dexElements.map { it.key }

            companion object {
                @JvmStatic
                fun makeDexElements(
                    files: ArrayList<File>,
                    optimizedDirectory: File,
                    suppressedExceptions: ArrayList<IOException>,
                ): Array<Element> {
                    return files
                        .map {
                            Element("${optimizedDirectory.absolutePath}:${it.absolutePath}")
                        }
                        .toTypedArray()
                }
            }
        }
    }

    @Test
    @Config(
        sdk = [Build.VERSION_CODES.LOLLIPOP]
    )
    fun buildLoaderAndLoadWith21ArrayListVariant() {
        buildLoaderAndLoadWithSource(Test21ArrayListVariantClassLoader())
    }

    private object IllegalClassLoader : ClassLoader()

    /**
     * Tests if build loader with illegal source class loader can raise error expectedly.
     */
    @Test
    fun buildLoaderWithIllegalSourceClassLoader() {
        val outputDirectory = Files.createTempDirectory("tinker-test-").toFile()
        val patchDirectory = createTestPatchDirectoryWithMockFiles()
        val patch = Patch("foo", patchDirectory)
        val error = assertThrows(TinkerError::class.java) {
            InjectPathDexLoader.Factory(IllegalClassLoader, outputDirectory)
                .createLoaderIfNeeded(patch)
        }
        assertEquals(
            hiddenErrorTypeOfForTesting("NO_SUCH_ELEMENT"),
            error.type,
        )
    }
}