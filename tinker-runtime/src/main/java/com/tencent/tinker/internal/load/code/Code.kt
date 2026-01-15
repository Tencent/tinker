package com.tencent.tinker.internal.load.code

import androidx.annotation.VisibleForTesting
import com.tencent.tinker.Tinker
import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.load.Loader
import com.tencent.tinker.internal.util.expected
import com.tencent.tinker.internal.util.searchAndSortDexFiles
import java.io.File

internal abstract class CodeLoader : Loader() {

    companion object {

        /**
         * Never get class name by referencing `TestClass`, as this will cause `TestClass` to be
         * loaded.
         */
        private const val TEST_ADDED_CLASS_NAME =
            "com.tencent.tinker.internal.load.code.test.TestAddedClass"

        /**
         * Never get class name by referencing `TestClass`, as this will cause `TestClass` to be
         * loaded.
         */
        private const val TEST_MODIFIED_CLASS_NAME =
            "com.tencent.tinker.internal.load.code.test.TestModifiedClass"

        /**
         * Never get class name by referencing `TestLibrary`, as this will cause `TestLibrary` to be
         * loaded.
         */
        private const val TEST_LIBRARY_NAME =
            "com.tencent.tinker.internal.load.code.test.TestLibrary"
    }

    override fun load() {
        doLoad().also(::verify)
    }

    /**
     * Loads patch dex files, and returns a class loader that can load classes from patch dex files.
     */
    protected abstract fun doLoad(): ClassLoader

    @VisibleForTesting
    fun doLoadForTesting() {
        doLoad()
    }

    /**
     * Whether to verify dependencies library loading.
     *
     * Some load strategies may unable to hijack dependency libraries loading, the verification
     * should be disabled.
     *
     * FIXME: Let all load strategies to support dependency library loading verification.
     */
    open val verifyDependencyLibraryLoading: Boolean = true

    private fun verify(classLoader: ClassLoader) {
        verifyDex(classLoader)
        verifyLibrary(classLoader)
    }

    private fun verifyDex(classLoader: ClassLoader) {
        // FIXME:
        //   The best way to verify if patch test dex is loaded is using application class loader,
        //     not class loader returned from implementation. We never know the class loader we get
        //     is the same class loader the application will use.
        //   Getting application class loader may cause code is too complex to test. Try to find a
        //     better solution.
        val addedClassPatched = try {
            Class.forName(
                TEST_ADDED_CLASS_NAME,
                true,
                classLoader
            ).getDeclaredField("isPatched").getBoolean(null)
        } catch (throwable: Throwable) {
            throw Tinker.Error(
                Tinker.Error.Load.Code.TEST_RESOURCE_BROKEN,
                "Cannot find \"${TEST_ADDED_CLASS_NAME}\" from provided class loader.",
                throwable,
            )
        }
        if (!addedClassPatched) {
            throw Tinker.Error(
                Tinker.Error.Load.Code.VERIFY_FAILED,
                "Cannot load patch-added test class by provided class loader.",
            )
        }
        val modifiedClassPatched = try {
            Class.forName(
                TEST_MODIFIED_CLASS_NAME,
                true,
                classLoader
            ).getDeclaredField("isPatched").getBoolean(null)
        } catch (throwable: Throwable) {
            throw Tinker.Error(
                Tinker.Error.Load.Code.TEST_RESOURCE_BROKEN,
                "Cannot find \"${TEST_MODIFIED_CLASS_NAME}\" from provided class loader.",
                throwable,
            )
        }
        if (!modifiedClassPatched) {
            throw Tinker.Error(
                Tinker.Error.Load.Code.VERIFY_FAILED,
                "Cannot load patch-modified test class by provided class loader.",
            )
        }
    }

    private fun verifyLibrary(classLoader: ClassLoader) {
        val clazz = try {
            Class.forName(
                TEST_LIBRARY_NAME,
                true,
                classLoader,
            )
        } catch (throwable: Throwable) {
            throw Tinker.Error(
                Tinker.Error.Load.Code.TEST_RESOURCE_BROKEN,
                "Cannot find \"${TEST_LIBRARY_NAME}\" from provided class loader.",
                throwable,
            )
        }
        val fromJni = try {
            clazz.getDeclaredMethod("fromJni").invoke(null) as String
        } catch (throwable: Throwable) {
            throw Tinker.Error(
                Tinker.Error.Load.Code.TEST_RESOURCE_BROKEN,
                "Cannot find \"fromJni\" method from \"${TEST_LIBRARY_NAME}\".",
                throwable,
            )
        }
        if (fromJni != "<_P_>") {
            throw Tinker.Error(
                Tinker.Error.Load.Code.VERIFY_FAILED,
                "Cannot load patched test JNI library.",
            )
        }
        if (verifyDependencyLibraryLoading) {
            val fromDependency = try {
                clazz.getDeclaredMethod("fromDependency").invoke(null) as String
            } catch (throwable: Throwable) {
                throw Tinker.Error(
                    Tinker.Error.Load.Code.TEST_RESOURCE_BROKEN,
                    "Cannot find \"fromDependency\" method from \"${TEST_LIBRARY_NAME}\".",
                    throwable,
                )
            }
            if (fromDependency != "<_P_>") {
                throw Tinker.Error(
                    Tinker.Error.Load.Code.VERIFY_FAILED,
                    "Cannot load patched test dependency library.",
                )
            }
        }
    }

    abstract class Factory(
        private val abiList: Array<String>,
    ) : Loader.Factory() {

        private fun searchJvmCodeFiles(patch: Patch): List<File> {
            patch.dexApkFile
                .takeIf { it.isFile }
                ?.let { return listOf(it) }
            patch.dexDirectory
                .searchAndSortDexFiles()
                ?.let { return it }
            throw Tinker.Error(
                Tinker.Error.Load.Code.NO_VALID_INPUTS,
                "Missing valid input dex files in \"${patch.dexDirectory.absolutePath}\".",
            )
        }

        private fun searchLibraryDirectory(patch: Patch): List<File> =
            abiList
                .map { abi ->
                    patch.libraryDirectory.resolve(abi)
                        .also {
                            if (!it.isDirectory) {
                                throw Tinker.Error(
                                    Tinker.Error.Load.Code.INVALID_LIBRARY_DIRECTORY,
                                    "Path \"${it.absolutePath}\" is not an existing directory."
                                )
                            }
                        }
                }
                .takeIf { it.isNotEmpty() }
                ?: throw Tinker.Error(
                    Tinker.Error.Load.Code.NO_VALID_INPUTS,
                    "Missing valid input library directory in \"${patch.libraryDirectory.absolutePath}\".",
                )


        override fun createLoaderIfNeeded(patch: Patch): CodeLoader {
            expected<Tinker.Error.Load.Code>("create code loader") {
                return createLoader(
                    jvmCodeFiles = searchJvmCodeFiles(patch),
                    libraryDirectories = searchLibraryDirectory(patch),
                )
            }
        }

        protected abstract fun createLoader(
            jvmCodeFiles: List<File>,
            libraryDirectories: List<File>,
        ): CodeLoader
    }
}