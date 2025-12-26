package com.tencent.tinker.test.internal.load.code

import java.io.File
import java.io.IOException

/**
 * Mocked class loader for Android O to latest Android version.
 */
@Suppress("unused")
internal class TestClassLoaderV26(
    withNullNativeLibraryDirectories: Boolean = false,
) : ClassLoader() {

    private val pathList = DexPathList(
        this,
        withNullNativeLibraryDirectories,
    )

    val dexElementsForTesting: List<String>
        get() = pathList.dexElementsForTesting

    val nativeLibraryPathElementsForTesting: List<String>
        get() = pathList.nativeLibraryPathElementsForTesting

    val nativeLibraryDirectoriesForTesting: List<String>
        get() = pathList.nativeLibraryDirectoriesForTesting

    private class DexPathList(
        private val definingContext: ClassLoader,
        withNullNativeLibraryDirectories: Boolean,
    ) {

        private class Element(val path: String)

        private class NativeLibraryElement(val path: String)

        private val dexElements: Array<Element> = arrayOf(
            Element("foo"),
            Element("bar"),
        )

        private val nativeLibraryPathElements = arrayOf(
            NativeLibraryElement("/foo/bar"),
            NativeLibraryElement("/baz"),
            NativeLibraryElement("/system/lib/foo/bar"),
            NativeLibraryElement("/system/lib/baz")
        )

        private val nativeLibraryDirectories: MutableList<File>? =
            if (withNullNativeLibraryDirectories) {
                null
            } else {
                mutableListOf(
                    File("/foo/bar"),
                    File("/baz"),
                )
            }

        private val systemNativeLibraryDirectories = listOf(
            File("/system/lib/foo/bar"),
            File("/system/lib/baz"),
        )

        val dexElementsForTesting: List<String>
            get() = dexElements.map { it.path }

        val nativeLibraryPathElementsForTesting: List<String>
            get() = nativeLibraryPathElements.map { it.path }

        val nativeLibraryDirectoriesForTesting: List<String>
            get() = nativeLibraryDirectories
                ?.map { it.absolutePath }
                ?: emptyList()

        companion object {
            @JvmStatic
            fun makeDexElements(
                files: List<File>,
                optimizedDirectory: File?,
                suppressedExceptions: List<IOException>,
                loader: ClassLoader,
            ): Array<Element> {
                return files
                    .map {
                        Element(it.absolutePath)
                    }
                    .toTypedArray()
            }

            @JvmStatic
            fun makePathElements(
                files: List<File>,
            ): Array<NativeLibraryElement> {
                return files
                    .map { NativeLibraryElement(it.absolutePath) }
                    .toTypedArray()
            }
        }
    }
}

/**
 * Mocked class loader for Android N until Android O.
 */
@Suppress("unused")
internal class TestClassLoaderV24(
    withNullNativeLibraryDirectories: Boolean = false,
) : ClassLoader() {

    private val pathList = DexPathList(
        this,
        withNullNativeLibraryDirectories,
    )

    val dexElementsForTesting: List<String>
        get() = pathList.dexElementsForTesting

    val nativeLibraryDirectoriesForTesting: List<String>
        get() = pathList.nativeLibraryDirectoriesForTesting

    val nativeLibraryPathElementsForTesting: List<String>
        get() = pathList.nativeLibraryPathElementsForTesting

    private class DexPathList(
        private val definingContext: ClassLoader,
        withNullNativeLibraryDirectories: Boolean,
    ) {
        private class Element(val path: String)

        private val dexElements: Array<Element> = arrayOf(
            Element("foo"),
            Element("bar"),
        )

        private val nativeLibraryPathElements = arrayOf(
            Element("/foo/bar"),
            Element("/baz"),
            Element("/system/lib/foo/bar"),
            Element("/system/lib/baz")
        )

        private val nativeLibraryDirectories: MutableList<File>? =
            if (withNullNativeLibraryDirectories) {
                null
            } else {
                mutableListOf(
                    File("/foo/bar"),
                    File("/baz"),
                )
            }

        private val systemNativeLibraryDirectories = listOf(
            File("/system/lib/foo/bar"),
            File("/system/lib/baz"),
        )

        val dexElementsForTesting: List<String>
            get() = dexElements.map { it.path }

        val nativeLibraryPathElementsForTesting: List<String>
            get() = nativeLibraryPathElements.map { it.path }

        val nativeLibraryDirectoriesForTesting: List<String>
            get() = nativeLibraryDirectories
                ?.map { it.absolutePath }
                ?: emptyList()

        companion object {
            @JvmStatic
            fun makeDexElements(
                files: List<File>,
                optimizedDirectory: File?,
                suppressedExceptions: List<IOException>,
                loader: ClassLoader,
            ): Array<Element> {
                return files
                    .map {
                        Element(it.absolutePath)
                    }
                    .toTypedArray()
            }

            @JvmStatic
            fun makePathElements(
                files: List<File>,
                suppressedExceptions: List<IOException>,
                loader: ClassLoader,
            ): Array<Element> {
                return files
                    .map {
                        Element(it.absolutePath)
                    }
                    .toTypedArray()
            }
        }
    }
}

/**
 * Mocked class loader for Android M until Android N.
 */
@Suppress("unused")
internal class TestClassLoaderV23(
    withNullNativeLibraryDirectories: Boolean = false,
) : ClassLoader() {

    private val pathList = DexPathList(
        withNullNativeLibraryDirectories,
    )

    val dexElementsForTesting: List<String>
        get() = pathList.dexElementsForTesting

    val nativeLibraryDirectoriesForTesting: List<String>
        get() = pathList.nativeLibraryDirectoriesForTesting

    val nativeLibraryPathElementsForTesting: List<String>
        get() = pathList.nativeLibraryPathElementsForTesting

    private class DexPathList(
        withNullNativeLibraryDirectories: Boolean,
    ) {

        private class Element(val path: String)

        private val dexElements: Array<Element> = arrayOf(
            Element("foo"),
            Element("bar"),
        )

        private val nativeLibraryPathElements = arrayOf(
            Element("/foo/bar"),
            Element("/baz"),
            Element("/system/lib/foo/bar"),
            Element("/system/lib/baz")
        )

        private val nativeLibraryDirectories: MutableList<File>? =
            if (withNullNativeLibraryDirectories) {
                null
            } else {
                mutableListOf(
                    File("/foo/bar"),
                    File("/baz"),
                )
            }

        private val systemNativeLibraryDirectories = listOf(
            File("/system/lib/foo/bar"),
            File("/system/lib/baz"),
        )

        val dexElementsForTesting: List<String>
            get() = dexElements.map { it.path }

        val nativeLibraryPathElementsForTesting: List<String>
            get() = nativeLibraryPathElements.map { it.path }

        val nativeLibraryDirectoriesForTesting: List<String>
            get() = nativeLibraryDirectories
                ?.map { it.absolutePath }
                ?: emptyList()

        companion object {
            @JvmStatic
            fun makePathElements(
                files: List<File>,
                optimizedDirectory: File?,
                suppressedExceptions: List<IOException>,
            ): Array<Element> {
                return files
                    .map {
                        Element(it.absolutePath)
                    }
                    .toTypedArray()
            }
        }
    }
}

/**
 * Mocked class loader for Android L until Android N.
 */
@Suppress("unused")
internal class TestClassLoaderV21(
    withNullNativeLibraryDirectories: Boolean = false,
) : ClassLoader() {

    private val pathList = DexPathList(
        this,
        withNullNativeLibraryDirectories,
    )

    val dexElementsForTesting: List<String>
        get() = pathList.dexElementsForTesting

    val nativeLibraryDirectoriesForTesting: List<String>
        get() = pathList.nativeLibraryDirectoriesForTesting

    private class DexPathList(
        private val definingContext: ClassLoader,
        withNullNativeLibraryDirectories: Boolean,
    ) {

        private class Element(val path: String)

        private val dexElements: Array<Element> = arrayOf(
            Element("foo"),
            Element("bar"),
        )

        private val nativeLibraryDirectories = arrayOf(
            File("/foo/bar"),
            File("/baz"),
        )

        val dexElementsForTesting: List<String>
            get() = dexElements.map { it.path }

        val nativeLibraryDirectoriesForTesting: List<String>
            get() = nativeLibraryDirectories.map { it.absolutePath }

        companion object {
            @JvmStatic
            fun makeDexElements(
                files: ArrayList<File>,
                optimizedDirectory: File?,
                suppressedExceptions: ArrayList<IOException>,
            ): Array<Element> {
                return files
                    .map {
                        Element(it.absolutePath)
                    }
                    .toTypedArray()
            }
        }
    }
}
