package com.tencent.tinker.internal.load.code

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.module.hidden.JavaMutableList
import com.tencent.tinker.internal.module.hidden.dexElements
import com.tencent.tinker.internal.module.hidden.lazySetDexElements
import com.tencent.tinker.internal.module.hidden.lazySetNativeLibraryDirectoriesOld
import com.tencent.tinker.internal.module.hidden.lazySetNativeLibraryDirectoriesV23
import com.tencent.tinker.internal.module.hidden.lazySetNativeLibraryPathElements
import com.tencent.tinker.internal.module.hidden.makeDexElements
import com.tencent.tinker.internal.module.hidden.makeLibraryElements
import com.tencent.tinker.internal.module.hidden.nativeLibraryDirectoriesOld
import com.tencent.tinker.internal.module.hidden.nativeLibraryDirectoriesV23
import com.tencent.tinker.internal.module.hidden.pathList
import com.tencent.tinker.internal.module.hidden.systemNativeLibraryDirectories
import com.tencent.tinker.internal.util.expected
import java.io.File
import java.io.IOException
import kotlin.collections.filter
import kotlin.collections.plus
import java.lang.reflect.Array as JvmReflectArray

private typealias DexPathList = Any

/**
 * Code loader which loads by injecting path into system class loader.
 */
internal class InjectPathCodeLoader(
    private val source: ClassLoader,
    private val actions: Iterable<Runnable>,
) : CodeLoader() {

    private object ErrorType : TinkerError.Type {
        override val group: TinkerError.TypeGroup
            get() = TinkerError.TypeGroup.LOAD_CODE_OLD

        override val typeCode: Int
            get() = 0
    }

    override fun loadForCode(): ClassLoader {
        actions.forEach {
            it.run()
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

        private fun createLoadActionsForDex(
            dexPathList: DexPathList,
            dexFiles: List<File>,
            outputDirectory: File
        ): List<Runnable> =
            buildList {
                val inputsAsArrayList = ArrayList<File>().apply {
                    addAll(dexFiles)
                }
                val suppressedExceptions = ArrayList<IOException>()
                val expandedElements = dexPathList.makeDexElements(
                    files = inputsAsArrayList,
                    optimizedDirectory = outputDirectory,
                    suppressedExceptions = suppressedExceptions
                )
                suppressedExceptions.forEach { throw it }
                val originalDexElements = dexPathList.dexElements
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
                dexPathList
                    .lazySetDexElements(updatedDexElements)
                    .let(::add)
            }

        private class ListUpdater<T>(
            private val source: JavaMutableList<T>,
            private val target: List<T>,
        ) : Runnable {
            override fun run() {
                source.apply {
                    clear()
                    addAll(target)
                }
            }
        }

        @RequiresApi(Build.VERSION_CODES.M)
        private fun createLoadActionsForLibraryV23(
            dexPathList: DexPathList,
            libraryDirectories: List<File>,
        ): List<Runnable> =
            buildList {
                val originalNativeLibraryDirectories =
                    dexPathList.nativeLibraryDirectoriesV23
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
                            .lazySetNativeLibraryDirectoriesV23(ArrayList(libraryDirectories))
                            .let(::add)
                        libraryDirectories
                    }
                val suppressedExceptions =
                    ArrayList<IOException>()
                val elements =
                    dexPathList.makeLibraryElements(
                        files = ArrayList(updatedNativeLibraryDirectories + dexPathList.systemNativeLibraryDirectories),
                        suppressedExceptions = suppressedExceptions,
                    )
                suppressedExceptions.forEach { throw it }
                elements.let(dexPathList::lazySetNativeLibraryPathElements)
                    .let(::add)
            }

        private fun createLoadActionsForLibraryOld(
            dexPathList: DexPathList,
            libraryDirectories: List<File>,
        ): List<Runnable> =
            buildList {
                val originalNativeLibraryDirectories =
                    dexPathList.nativeLibraryDirectoriesOld
                val updatedNativeLibraryDirectories =
                    libraryDirectories.toTypedArray() + originalNativeLibraryDirectories
                dexPathList
                    .lazySetNativeLibraryDirectoriesOld(updatedNativeLibraryDirectories)
                    .let(::add)
            }

        private fun createLoadActionsForLibrary(
            dexPathList: DexPathList,
            libraryDirectories: List<File>,
        ): List<Runnable> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return createLoadActionsForLibraryV23(dexPathList, libraryDirectories)
            }
            return createLoadActionsForLibraryOld(dexPathList, libraryDirectories)
        }

        @Suppress("UNCHECKED_CAST")
        override fun createLoader(
            dexFiles: List<File>,
            libraryDirectories: List<File>
        ): CodeLoader {
            expected("create code loader", ErrorType) {
                val actions = mutableListOf<Runnable>()
                val dexPathList = source.pathList
                createLoadActionsForDex(
                    dexPathList,
                    dexFiles,
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