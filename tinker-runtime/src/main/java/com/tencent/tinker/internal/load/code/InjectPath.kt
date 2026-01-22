package com.tencent.tinker.internal.load.code

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import com.tencent.tinker.Tinker
import com.tencent.tinker.internal.load.ClassLoaderDelegate.Companion.delegated
import com.tencent.tinker.internal.load.DexPathListDelegate
import com.tencent.tinker.internal.load.JavaMutableList
import com.tencent.tinker.internal.util.className
import com.tencent.tinker.internal.util.debugLog
import com.tencent.tinker.internal.util.expected
import com.tencent.tinker.internal.util.traceS
import java.io.File
import java.io.IOException
import java.lang.reflect.Array as JvmReflectArray

private const val TAG = "Tinker.Load.Code.IP"

/**
 * Code loader which loads by injecting path into system class loader.
 */
internal class InjectPathCodeLoader(
    private val source: ClassLoader,
    private val actions: Iterable<() -> Unit>,
) : CodeLoader() {

    override val verifyDependencyLibraryLoading: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.N

    override fun doLoad(): ClassLoader {
        actions.forEach {
            traceS("load.load.code.inject.action(action = ${it.javaClass.className}@${it.hashCode().toString(16)})") {
                it.invoke()
            }
        }
        return source
    }

    class Factory(
        private val source: ClassLoader,
        private val outputDirectory: File,
        abiList: Array<String> = Build.SUPPORTED_ABIS,
    ) : CodeLoader.Factory(abiList) {

        constructor(
            application: Application,
            outputDirectory: File,
        ) : this(
            source = application.classLoader,
            outputDirectory = outputDirectory,
        )

        private fun createLoadActionsForJvmCodeFiles(
            dexPathList: DexPathListDelegate,
            jvmCodeFiles: List<File>,
            outputDirectory: File
        ): List<() -> Unit> =
            buildList {
                val inputsAsArrayList = ArrayList<File>().apply {
                    addAll(jvmCodeFiles)
                }
                val suppressedExceptions = ArrayList<IOException>()
                val expandedElements = dexPathList.makeDexElements(
                    files = inputsAsArrayList,
                    optimizedDirectory = outputDirectory,
                    suppressedExceptions = suppressedExceptions
                )
                suppressedExceptions.forEach { throw it }
                val originalDexElements = dexPathList.dexElements
                debugLog(TAG) {
                    buildList {
                        add("Found original dex elements:")
                        originalDexElements.forEach {
                            add("  $it")
                        }
                    }.joinToString("\n")
                }
                val updatedDexElements = JvmReflectArray
                    .newInstance(
                        originalDexElements.javaClass.componentType!!,
                        expandedElements.size + originalDexElements.size,
                    )
                    .also { array ->
                        System.arraycopy(
                            expandedElements,
                            0,
                            array,
                            0,
                            expandedElements.size
                        )
                        System.arraycopy(
                            originalDexElements,
                            0,
                            array,
                            expandedElements.size,
                            originalDexElements.size
                        )
                    }
                    .let {
                        @Suppress("UNCHECKED_CAST")
                        it as Array<Any>
                    }
                debugLog(TAG) {
                    buildList {
                        add("Created updated dex elements:")
                        updatedDexElements.forEach {
                            add("  $it")
                        }
                    }.joinToString("\n")
                }
                dexPathList
                    .lazySelfSetDexElements(updatedDexElements)
                    .let(::add)
            }

        private class ListUpdater<T>(
            private val source: JavaMutableList<T>,
            private val target: List<T>,
        ) : () -> Unit {
            override fun invoke() {
                source.apply {
                    clear()
                    addAll(target)
                }
            }
        }

        @RequiresApi(Build.VERSION_CODES.M)
        private fun createLoadActionsForLibraryV23(
            dexPathList: DexPathListDelegate,
            libraryDirectories: List<File>,
        ): List<() -> Unit> =
            buildList {
                val originalNativeLibraryDirectories =
                    dexPathList.nativeLibraryDirectoriesV23
                debugLog(TAG) {
                    if (originalNativeLibraryDirectories != null) {
                        buildList {
                            add("Found original native library directories:")
                            originalNativeLibraryDirectories.forEach {
                                add("  ${it.absolutePath}")
                            }
                        }.joinToString("\n")
                    } else {
                        "Found none of original native library directories."
                    }
                }
                val updatedNativeLibraryDirectories =
                    if (originalNativeLibraryDirectories != null) {
                        val inputPathsSet =
                            libraryDirectories.map { it.absolutePath }.toSet()
                        val result =
                            libraryDirectories + originalNativeLibraryDirectories.filter { it.absolutePath !in inputPathsSet }
                        ListUpdater(originalNativeLibraryDirectories, result)
                            .let(::add)
                        result
                    } else {
                        dexPathList
                            .lazySelfSetNativeLibraryDirectoriesV23(ArrayList(libraryDirectories))
                            .let(::add)
                        libraryDirectories
                    }
                debugLog(TAG) {
                    buildList {
                        add("Created updated native library directories:")
                        updatedNativeLibraryDirectories.forEach {
                            add("  ${it.absolutePath}")
                        }
                    }.joinToString("\n")
                }
                val suppressedExceptions =
                    ArrayList<IOException>()
                val elements =
                    dexPathList.makeLibraryElements(
                        files = ArrayList(updatedNativeLibraryDirectories + dexPathList.systemNativeLibraryDirectories),
                        suppressedExceptions = suppressedExceptions,
                    )
                suppressedExceptions.forEach { throw it }
                debugLog(TAG) {
                    buildList {
                        add("Created updated library elements:")
                        elements.forEach {
                            add("  $it")
                        }
                    }.joinToString("\n")
                }
                elements.let(dexPathList::lazySelfSetNativeLibraryPathElements)
                    .let(::add)
            }

        private fun createLoadActionsForLibraryOld(
            dexPathList: DexPathListDelegate,
            libraryDirectories: List<File>,
        ): List<() -> Unit> =
            buildList {
                val originalNativeLibraryDirectories =
                    dexPathList.nativeLibraryDirectoriesOld
                debugLog(TAG) {
                    buildList {
                        add("Found original native library directories:")
                        originalNativeLibraryDirectories.forEach {
                            add("  ${it.absolutePath}")
                        }
                    }.joinToString("\n")
                }
                val updatedNativeLibraryDirectories =
                    libraryDirectories.toTypedArray() + originalNativeLibraryDirectories
                debugLog(TAG) {
                    buildList {
                        add("Created updated native library directories:")
                        updatedNativeLibraryDirectories.forEach {
                            add("  ${it.absolutePath}")
                        }
                    }.joinToString("\n")
                }
                dexPathList
                    .lazySelfSetNativeLibraryDirectoriesOld(updatedNativeLibraryDirectories)
                    .let(::add)
            }

        private fun createLoadActionsForLibrary(
            dexPathList: DexPathListDelegate,
            libraryDirectories: List<File>,
        ): List<() -> Unit> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                debugLog(TAG) {
                    "Creating load actions for library with v23 strategy."
                }
                return createLoadActionsForLibraryV23(dexPathList, libraryDirectories)
            }
            debugLog(TAG) {
                "Creating load actions for library with old strategy."
            }
            return createLoadActionsForLibraryOld(dexPathList, libraryDirectories)
        }

        @Suppress("UNCHECKED_CAST")
        override fun createLoader(
            jvmCodeFiles: List<File>,
            libraryDirectories: List<File>
        ): CodeLoader {
            expected<Tinker.Error.Load.Code.InjectPath>("create code loader") {
                val actions = mutableListOf<() -> Unit>()
                val dexPathList = source.delegated.pathList
                createLoadActionsForJvmCodeFiles(
                    dexPathList,
                    jvmCodeFiles,
                    outputDirectory,
                ).let(actions::addAll)
                createLoadActionsForLibrary(
                    dexPathList,
                    libraryDirectories,
                ).let(actions::addAll)
                return InjectPathCodeLoader(
                    source = source,
                    actions = actions,
                )
            }
        }
    }
}