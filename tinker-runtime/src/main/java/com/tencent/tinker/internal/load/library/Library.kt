package com.tencent.tinker.internal.load.library

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.load.Loader
import com.tencent.tinker.internal.load.library.test.TestLibrary
import com.tencent.tinker.internal.module.hidden.JavaMutableList
import com.tencent.tinker.internal.module.hidden.lazySetNativeLibraryDirectoriesOld
import com.tencent.tinker.internal.module.hidden.lazySetNativeLibraryDirectoriesV23
import com.tencent.tinker.internal.module.hidden.lazySetNativeLibraryPathElements
import com.tencent.tinker.internal.module.hidden.makeElements
import com.tencent.tinker.internal.module.hidden.makeNativeLibraryElements
import com.tencent.tinker.internal.module.hidden.nativeLibraryDirectoriesOld
import com.tencent.tinker.internal.module.hidden.nativeLibraryDirectoriesV23
import com.tencent.tinker.internal.module.hidden.pathList
import com.tencent.tinker.internal.module.hidden.systemNativeLibraryDirectories
import com.tencent.tinker.internal.util.expected
import java.io.File
import java.io.IOException

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
private fun ClassLoader.createLoadActionsV23(inputs: List<File>): List<Runnable> =
    buildList actions@{
        val dexPathList =
            this@createLoadActionsV23.pathList

        val originalNativeLibraryDirectories =
            dexPathList.nativeLibraryDirectoriesV23

        val updatedNativeLibraryDirectories = if (originalNativeLibraryDirectories != null) {
            val inputPathsSet =
                inputs.map { it.absolutePath }.toSet()
            val result =
                inputs + originalNativeLibraryDirectories.filter { it.absolutePath !in inputPathsSet }
            this@actions.add(ListUpdater(originalNativeLibraryDirectories, result))
            result
        } else {
            this@actions.add(dexPathList.lazySetNativeLibraryDirectoriesV23(ArrayList(inputs)))
            inputs
        }

        val makeElementsInput = buildList {
            addAll(updatedNativeLibraryDirectories)
            addAll(dexPathList.systemNativeLibraryDirectories)
        }

        val elements =
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    || (Build.VERSION.SDK_INT == Build.VERSION_CODES.N_MR1 && Build.VERSION.PREVIEW_SDK_INT > 0)
                ) {
                    dexPathList.makeNativeLibraryElements(makeElementsInput)
                } else {
                    null
                }
            } catch (_: Throwable) {
                null
            } ?: run {
                val suppressedExceptions = ArrayList<IOException>()
                dexPathList.makeElements(
                    files = ArrayList(makeElementsInput),
                    optimizedDirectory = null,
                    suppressedExceptions = suppressedExceptions
                )
            }

        elements.let(dexPathList::lazySetNativeLibraryPathElements)
            .let(this@actions::add)
    }

private fun ClassLoader.createLoadActionsOld(inputs: List<File>): List<Runnable> =
    buildList actions@{
        val dexPathList =
            this@createLoadActionsOld.pathList
        val originalNativeLibraryDirectories =
            dexPathList.nativeLibraryDirectoriesOld
        val updatedNativeLibraryDirectories =
            inputs.toTypedArray() + originalNativeLibraryDirectories
        dexPathList.lazySetNativeLibraryDirectoriesOld(updatedNativeLibraryDirectories)
            .let(this@actions::add)
    }

private fun ClassLoader.createLoadActions(inputs: List<File>): List<Runnable> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        return createLoadActionsV23(inputs)
    }
    return createLoadActionsOld(inputs)
}

internal class LibraryLoader(
    private val actions: Iterable<Runnable>,
) : Loader() {

    companion object {
        @VisibleForTesting
        fun errorTypeOfForTesting(type: String): TinkerError.Type =
            ErrorType.valueOf(type)
    }

    private enum class ErrorType : TinkerError.Type {
        UNEXPECTED,
        INVALID_LIBRARY_DIRECTORY,
        NO_VALID_INPUTS,
        VERIFY_FAILED;

        override val group: TinkerError.TypeGroup
            get() = TinkerError.TypeGroup.LOAD_LIBRARY

        override val typeCode: Int
            get() = ordinal
    }

    override fun load() {
        libraryLoad()
        verify()
    }

    private fun libraryLoad() {
        actions.forEach { it.run() }
    }

    @VisibleForTesting
    fun libraryLoadForTesting() {
        libraryLoad()
    }

    private fun verify() {
        val fromJni = TestLibrary.fromJni()
        if (fromJni != "<_P_>") {
            throw TinkerError(
                ErrorType.VERIFY_FAILED,
                "Cannot load patched test JNI library.",
            )
        }
        val fromDependency = TestLibrary.fromDependency()
        if (fromDependency != "<_P_>") {
            throw TinkerError(
                ErrorType.VERIFY_FAILED,
                "Cannot load patched test dependency library.",
            )
        }
    }

    class Factory(
        private val applicationClassLoader: ClassLoader,
        private val abiList: Array<String> = Build.SUPPORTED_ABIS,
    ) : Loader.Factory() {

        constructor(context: Context) : this(context.applicationContext.classLoader)

        private fun buildInputs(patch: Patch): List<File> =
            abiList
                .map { abi ->
                    patch.libraryDirectory.resolve(abi)
                        .also {
                            if (!it.isDirectory) {
                                throw TinkerError(
                                    ErrorType.INVALID_LIBRARY_DIRECTORY,
                                    "Path \"${it.absolutePath}\" is not an existing directory."
                                )
                            }
                        }
                }
                .takeIf { it.isNotEmpty() }
                ?: throw TinkerError(
                    ErrorType.NO_VALID_INPUTS,
                    "Missing valid input library directory in \"${patch.libraryDirectory.absolutePath}\".",
                )

        override fun createLoaderIfNeeded(patch: Patch): LibraryLoader {
            expected<ErrorType>("create library loader") {
                return buildInputs(patch)
                    .let(applicationClassLoader::createLoadActions)
                    .let(::LibraryLoader)
            }
        }
    }
}